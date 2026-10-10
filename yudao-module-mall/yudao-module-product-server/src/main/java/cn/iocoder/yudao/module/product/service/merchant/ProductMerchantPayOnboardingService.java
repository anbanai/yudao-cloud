package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApi;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantAccountRespDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayOnboardingStatusEnum;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayMaterialsReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayOnboardingDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayMaterialRevisionDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayApprovedMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayOnboardingMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayMaterialRevisionMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantPayProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/** 审批只保存任务；支付网络调用由持久化重试流程执行，避免审批事务依赖渠道可用性。 */
@Service
public class ProductMerchantPayOnboardingService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FILE_REFERENCES = "_fileReferences";
    private static final Set<String> MATERIAL_KEYS = Set.of("merchantType", "merchantSubjectInfo",
            "merchantCorporationInfo", "merchantContactInfo", "businessAddressInfo", "functionServiceQualificationInfo",
            "accountInfo", "industryCategoryInfo", "productInfo", "functionService");
    private static final Set<String> FILE_ROOTS = Set.of("merchantSubjectInfo", "merchantCorporationInfo",
            "merchantContactInfo", "businessAddressInfo", "functionServiceQualificationInfo", "accountInfo", "productInfo");
    private static final List<String> REQUIRED_OBJECTS = List.of("merchantSubjectInfo", "merchantCorporationInfo",
            "merchantContactInfo", "businessAddressInfo");
    private static final Set<String> CREDENTIAL_KEYS = Set.of("privatekey", "secretkey", "aeskey", "appsecret",
            "hmackey", "encryptkey", "apikey", "merchantprivatekey", "certificatepassword");

    @Resource
    private ProductMerchantPayApprovedMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantPayOnboardingMapper onboardingMapper;
    @Resource
    private ProductMerchantPayMaterialRevisionMapper materialRevisionMapper;
    @Resource
    private ProductMerchantPayProperties properties;
    @Resource
    private PayFundApi payFundApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Transactional(rollbackFor = Exception.class)
    public Long enqueueApprovedMerchant(Long merchantId) {
        ProductMerchantDO merchant = requireApprovedMerchant(merchantId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime approvalTime = approvalTime(merchant);
        for (ProductMerchantPayOnboardingDO previous : onboardingMapper.selectByMerchantId(merchantId, tenantId)) {
            if (Objects.equals(previous.getMerchantApprovalTime(), approvalTime)) {
                return previous.getId();
            }
            if (!"SUPERSEDED".equals(previous.getState())) {
                onboardingMapper.updateById(previous.setState("SUPERSEDED").setNextRetryTime(null));
            }
        }
        ProductMerchantPayOnboardingDO task = new ProductMerchantPayOnboardingDO().setMerchantId(merchantId)
                .setMerchantApprovalTime(approvalTime).setAppKey(properties.getAppKey())
                .setRequestNo("MER-" + tenantId + "-" + merchantId)
                .setBusinessRole("SHARE_MERCHANT").setState("WAITING_MATERIALS")
                .setMaterialsVersion(0).setAttemptCount(0).setRequestAccepted(false);
        task.setTenantId(tenantId);
        onboardingMapper.insert(task);
        return task.getId();
    }

    /** 后台账号上传到既有私有存储，敏感文件名不会写入渠道资料或返回 URL。 */
    public Long uploadPrivateFile(Long onboardingId, MultipartFile file) {
        ProductMerchantPayOnboardingDO task = requireTask(onboardingId, false);
        ProductMerchantDO merchant = merchantMapper.selectById(task.getMerchantId());
        if (merchant == null || !Objects.equals(merchant.getStatus(), ProductMerchantStatusEnum.APPROVED.getStatus())
                || !Objects.equals(task.getMerchantApprovalTime(), approvalTime(merchant))
                || Set.of("COMPLETED", "SUPERSEDED").contains(task.getState())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        String type = file == null ? null : file.getContentType();
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024
                || !Set.of("image/jpeg", "image/png", "application/pdf").contains(type == null ? "" : type)
                || !Boolean.TRUE.equals(fileApi.isPrivateMasterSupported().getCheckedData())) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
        String extension = "image/jpeg".equals(type) ? ".jpg" : "image/png".equals(type) ? ".png" : ".pdf";
        try {
            return fileApi.createPrivateFile(file.getBytes(), UUID.randomUUID() + extension,
                    "merchant-pay/" + task.getTenantId() + "/" + task.getId(), type).getId();
        } catch (Exception ex) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMaterials(ProductMerchantPayMaterialsReqVO reqVO) {
        ProductMerchantPayOnboardingDO candidate = requireTask(reqVO.getOnboardingId(), false);
        ProductMerchantDO merchant = requireApprovedMerchant(candidate.getMerchantId());
        ProductMerchantPayOnboardingDO task = requireTask(candidate.getId(), true);
        if (!Objects.equals(task.getMerchantApprovalTime(), approvalTime(merchant))) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        if ("COMPLETED".equals(task.getState()) || "SUPERSEDED".equals(task.getState())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        if (!"SETTLED_MERCHANT".equals(reqVO.getBusinessRole()) && !"SHARE_MERCHANT".equals(reqVO.getBusinessRole())) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
        Map<String, Object> materials = copyMaterials(reqVO.getMaterials());
        if (!MATERIAL_KEYS.containsAll(materials.keySet()) || containsCredentials(materials)
                || !missingMaterials(materials).isEmpty()) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
        Map<String, Long> references = reqVO.getFileReferences();
        if (references != null && !references.isEmpty()) {
            for (Map.Entry<String, Long> reference : references.entrySet()) {
                validateFileReferencePath(reference.getKey(), materials);
                if (reference.getValue() == null || reference.getValue() <= 0
                        || !Boolean.TRUE.equals(fileApi.isFileCreatedByCurrentUser(reference.getValue()).getCheckedData())) {
                    throw exception(MERCHANT_PAY_MATERIALS_INVALID);
                }
            }
            materials.put(FILE_REFERENCES, references);
        }
        String json = serialize(materials);
        if (json.length() > 65536) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
        int version = task.getMaterialsVersion() + 1;
        ProductMerchantPayMaterialRevisionDO revision = new ProductMerchantPayMaterialRevisionDO()
                .setOnboardingId(task.getId()).setMerchantId(task.getMerchantId()).setMaterialsVersion(version)
                .setBusinessRole(reqVO.getBusinessRole()).setOperatorId(SecurityFrameworkUtils.getLoginUserId())
                .setMaterialsJson(json);
        revision.setTenantId(TenantContextHolder.getRequiredTenantId());
        materialRevisionMapper.insert(revision);
        onboardingMapper.updateById(task.setMaterialsJson(json).setMaterialsVersion(version)
                .setBusinessRole(reqVO.getBusinessRole()).setState("READY").setRequestAccepted(false)
                .setLastError(null).setSignUrl(null).setAuthUrl(null).setNextRetryTime(LocalDateTime.now()));
    }

    public ProductMerchantPayOnboardingRespVO get(Long onboardingId) {
        ProductMerchantPayOnboardingDO task = requireTask(onboardingId, false);
        Map<String, Object> materials = task.getMaterialsJson() == null ? Map.of() : parse(task.getMaterialsJson());
        return convert(task).setMissingMaterials(missingMaterials(materials))
                .setSignUrl(task.getSignUrl()).setAuthUrl(task.getAuthUrl());
    }

    public PageResult<ProductMerchantPayOnboardingRespVO> getPage(ProductMerchantPayOnboardingPageReqVO reqVO) {
        PageResult<ProductMerchantPayOnboardingDO> page = onboardingMapper.selectPage(reqVO,
                TenantContextHolder.getRequiredTenantId());
        return new PageResult<>(page.getList().stream().map(this::convert).toList(), page.getTotal());
    }

    private ProductMerchantPayOnboardingRespVO convert(ProductMerchantPayOnboardingDO task) {
        ProductMerchantDO merchant = merchantMapper.selectById(task.getMerchantId());
        return new ProductMerchantPayOnboardingRespVO().setOnboardingId(task.getId())
                .setMerchantName(merchant == null ? null : merchant.getName())
                .setMerchantApprovalTime(task.getMerchantApprovalTime()).setBusinessRole(task.getBusinessRole())
                .setMerchantId(task.getMerchantId()).setState(task.getState()).setMaterialsVersion(task.getMaterialsVersion())
                .setOnboardingStatus(task.getOnboardingStatus()).setAttemptCount(task.getAttemptCount())
                .setNextRetryTime(task.getNextRetryTime()).setLastError(task.getLastError());
    }

    /** 当前租户的到期任务；每行使用独立事务，多个实例通过商家/任务行锁串行处理。 */
    public int processPending() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int processed = 0;
        for (ProductMerchantPayOnboardingDO candidate : onboardingMapper.selectDue(tenantId)) {
            if (Boolean.TRUE.equals(transaction.execute(status -> processOne(candidate.getId())))) {
                processed++;
            }
        }
        return processed;
    }

    private boolean processOne(Long onboardingId) {
        ProductMerchantPayOnboardingDO candidate = requireTask(onboardingId, false);
        ProductMerchantDO merchant = merchantMapper.selectByIdForUpdate(candidate.getMerchantId());
        ProductMerchantPayOnboardingDO task = requireTask(onboardingId, true);
        if (task.getNextRetryTime() == null || task.getNextRetryTime().isAfter(LocalDateTime.now())
                || Set.of("COMPLETED", "SUPERSEDED", "WAITING_MATERIALS").contains(task.getState())) {
            return false;
        }
        if (merchant == null || !Objects.equals(task.getMerchantApprovalTime(), approvalTime(merchant))) {
            onboardingMapper.updateById(task.setState("SUPERSEDED").setNextRetryTime(null));
            return true;
        }
        if (!Objects.equals(merchant.getStatus(), ProductMerchantStatusEnum.APPROVED.getStatus())) {
            // 商家被冻结时保留任务，恢复批准状态后继续处理。
            onboardingMapper.updateById(task.setNextRetryTime(LocalDateTime.now().plusSeconds(properties.getPollSeconds())));
            return false;
        }
        task.setAttemptCount(task.getAttemptCount() + 1);
        boolean poll = Boolean.TRUE.equals(task.getRequestAccepted());
        try {
            PayFundMerchantAccountRespDTO response;
            if (poll) {
                response = payFundApi.getMerchant(task.getAppKey(), task.getMerchantId()).getCheckedData();
            } else {
                Map<String, Object> materials = parse(task.getMaterialsJson());
                if (!missingMaterials(materials).isEmpty()) {
                    onboardingMapper.updateById(task.setState("WAITING_MATERIALS").setNextRetryTime(null));
                    return true;
                }
                resolveFileReferences(materials);
                materials.put("materialsRef", "product-merchant-pay-onboarding:" + task.getId()
                        + ":m" + task.getMaterialsVersion());
                materials.put("materialsVersion", task.getMaterialsVersion());
                response = payFundApi.registerMerchant(new PayFundMerchantRegisterReqDTO().setAppKey(task.getAppKey())
                        .setMerchantId(task.getMerchantId()).setMerchantName(merchant.getName())
                        .setRequestNo(task.getRequestNo()).setBusinessRole(task.getBusinessRole())
                        .setMaterials(materials)).getCheckedData();
            }
            applyResponse(task, response);
        } catch (RuntimeException ex) {
            // RPC/SDK 异常可能携带资料，持久化固定错误码，不打印原始异常或请求。
            task.setState("RETRY").setLastError(poll ? "PAY_QUERY_FAILED" : "PAY_REQUEST_FAILED")
                    .setNextRetryTime(LocalDateTime.now().plusSeconds(properties.getRetrySeconds()));
        }
        onboardingMapper.updateById(task);
        return true;
    }

    private void applyResponse(ProductMerchantPayOnboardingDO task, PayFundMerchantAccountRespDTO response) {
        if (response == null) {
            throw new IllegalStateException("Missing payment onboarding response");
        }
        Integer status = response.getOnboardingStatus();
        task.setOnboardingStatus(status);
        if (PayYeepayOnboardingStatusEnum.isCompleted(status)) {
            task.setState("COMPLETED").setRequestAccepted(true).setLastError(null).setNextRetryTime(null)
                    .setSignUrl(null).setAuthUrl(null);
            return;
        }
        if (Objects.equals(status, PayYeepayOnboardingStatusEnum.REVIEW_BACK.getStatus())
                || Objects.equals(status, PayYeepayOnboardingStatusEnum.FAILED.getStatus())) {
            task.setState("ACTION_REQUIRED").setRequestAccepted(true).setLastError("PAY_MATERIALS_REJECTED")
                    .setNextRetryTime(null).setSignUrl(null).setAuthUrl(null);
            return;
        }
        if (StringUtils.hasText(response.getLastError())) {
            throw new IllegalStateException("Payment onboarding request failed");
        }
        task.setRequestAccepted(true).setLastError(null)
                .setNextRetryTime(LocalDateTime.now().plusSeconds(properties.getPollSeconds()));
        if (Objects.equals(status, PayYeepayOnboardingStatusEnum.AGREEMENT_SIGNING.getStatus())
                || Objects.equals(status, PayYeepayOnboardingStatusEnum.AUTHENTICITY_VERIFYING.getStatus())) {
            task.setState("ACTION_REQUIRED");
        } else {
            task.setState("SUBMITTED");
        }
        if (safeUrl(response.getSignUrl()) != null) {
            task.setSignUrl(response.getSignUrl());
        }
        if (safeUrl(response.getAuthUrl()) != null) {
            task.setAuthUrl(response.getAuthUrl());
        }
    }

    private ProductMerchantDO requireApprovedMerchant(Long merchantId) {
        ProductMerchantDO merchant = merchantMapper.selectByIdForUpdate(merchantId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        if (!Objects.equals(merchant.getStatus(), ProductMerchantStatusEnum.APPROVED.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        return merchant;
    }

    /** 审核时间优先；旧部署缺少审核时间时使用现有更新时间，不引入申请领域。 */
    private LocalDateTime approvalTime(ProductMerchantDO merchant) {
        LocalDateTime value = merchant.getReviewTime() == null ? merchant.getUpdateTime() : merchant.getReviewTime();
        if (value == null) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        return value;
    }

    private ProductMerchantPayOnboardingDO requireTask(Long onboardingId, boolean lock) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProductMerchantPayOnboardingDO task = onboardingMapper.selectByIdAndTenant(onboardingId, tenantId, lock);
        if (task == null) {
            throw exception(MERCHANT_PAY_ONBOARDING_NOT_EXISTS);
        }
        return task;
    }

    private List<String> missingMaterials(Map<String, Object> materials) {
        List<String> missing = new ArrayList<>();
        Object merchantType = materials.get("merchantType");
        if (merchantType == null && materials.get("merchantSubjectInfo") instanceof Map<?, ?> subject) {
            merchantType = subject.get("merchantType");
        }
        boolean micro = merchantType instanceof String type && Set.of("MICRO", "MICRO_MERCHANT",
                "SMALL_MICRO", "SMALL_MICRO_MERCHANT").contains(type.trim().toUpperCase(Locale.ROOT));
        for (String key : REQUIRED_OBJECTS) {
            if (micro && "merchantContactInfo".equals(key)) {
                continue;
            }
            if (!(materials.get(key) instanceof Map<?, ?> object) || object.isEmpty()) {
                missing.add(key);
            }
        }
        if (!(materials.get("functionServiceQualificationInfo") instanceof Map<?, ?> qualification)
                || !(qualification.get("shareScene") instanceof String scene) || !StringUtils.hasText(scene)) {
            missing.add("functionServiceQualificationInfo.shareScene");
        }
        return missing;
    }

    private boolean containsCredentials(Object value) {
        if (value instanceof Map<?, ?> object) {
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (CREDENTIAL_KEYS.contains(String.valueOf(entry.getKey()).replace("_", "").toLowerCase(Locale.ROOT))
                        || containsCredentials(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Iterable<?> collection) {
            for (Object item : collection) {
                if (containsCredentials(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resolveFileReferences(Map<String, Object> materials) {
        Object value = materials.remove(FILE_REFERENCES);
        if (!(value instanceof Map<?, ?> references)) {
            return;
        }
        for (Map.Entry<?, ?> reference : references.entrySet()) {
            String path = String.valueOf(reference.getKey());
            validateFileReferencePath(path, materials);
            Long fileId = Long.valueOf(String.valueOf(reference.getValue()));
            String url = fileApi.presignGetUrl(fileId, 900).getCheckedData();
            if (safeUrl(url) == null) {
                throw exception(MERCHANT_PAY_MATERIALS_INVALID);
            }
            String[] parts = path.split("\\.");
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) materials.get(parts[0]);
            object.put(parts[1], url);
        }
    }

    private void validateFileReferencePath(String path, Map<String, Object> materials) {
        if (path == null || !path.matches("[A-Za-z][A-Za-z0-9]*\\.[A-Za-z][A-Za-z0-9]*")) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
        String root = path.substring(0, path.indexOf('.'));
        String field = path.substring(path.indexOf('.') + 1);
        if (!FILE_ROOTS.contains(root) || !(materials.get(root) instanceof Map<?, ?>)
                || CREDENTIAL_KEYS.contains(field.toLowerCase(Locale.ROOT))) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    private Map<String, Object> copyMaterials(Map<String, Object> materials) {
        return materials == null ? new LinkedHashMap<>() : parse(serialize(materials));
    }

    private String serialize(Map<String, ?> materials) {
        try {
            return JSON.writeValueAsString(materials);
        } catch (Exception ex) {
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    private Map<String, Object> parse(String value) {
        try {
            return JSON.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ex) {
            // 不能使用会打印原文的 JsonUtils.parseObject。
            throw exception(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    private String safeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
