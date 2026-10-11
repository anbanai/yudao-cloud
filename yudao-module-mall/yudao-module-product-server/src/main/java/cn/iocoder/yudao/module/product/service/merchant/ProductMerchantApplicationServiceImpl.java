package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDetailRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDraftReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationQualificationReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationQualificationRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationSubmitReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantReviewRecordRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationQualificationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationRequirementDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantReviewRecordDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantBusinessTypeMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationRequirementMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantReviewRecordMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantApplicationStatusEnum;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantQualificationStatusEnum;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantApplicationProperties;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductMerchantApplicationServiceImpl implements ProductMerchantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProductMerchantApplicationServiceImpl.class);

    private static final String TYPE_INITIAL = "INITIAL";
    private static final String TYPE_NEW = "NEW";
    private static final String TYPE_CHANGE = "CHANGE";

    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantApplicationMapper applicationMapper;
    @Resource
    private ProductMerchantApplicationQualificationMapper applicationQualificationMapper;
    @Resource
    private ProductMerchantBusinessTypeMapper businessTypeMapper;
    @Resource
    private ProductMerchantQualificationRequirementMapper requirementMapper;
    @Resource
    private ProductMerchantReviewRecordMapper reviewRecordMapper;
    @Resource
    private ProductMerchantQualificationMapper qualificationMapper;
    @Resource
    private ProductMerchantService merchantService;
    @Resource
    private ProductMerchantApplicationProperties applicationProperties;
    @Autowired(required = false)
    private ObjectProvider<SystemMerchantUserApi> merchantUserApiProvider;
    @Autowired(required = false)
    private ObjectProvider<FileApi> fileApiProvider;
    @Autowired
    private ObjectProvider<ProductMerchantPayOnboardingService> payOnboardingServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindLegacyAccount(Long merchantId, Long accountId, Long operatorId) {
        ProductMerchantDO merchant = merchantMapper.selectByIdForUpdate(merchantId);
        ProductMerchantApplicationDO latest = applicationMapper.selectLatestByMerchantIdForUpdate(merchantId);
        if (merchant == null || latest == null || !"LEGACY".equals(latest.getType())
                || merchant.getOwnerAccountId() != null && !accountId.equals(merchant.getOwnerAccountId())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        ProductMerchantDO existing = merchantMapper.selectByOwnerAccountId(accountId);
        SystemMerchantUserApi api = merchantUserApiProvider.getIfAvailable();
        var canBind = api == null ? null : api.canBindMerchant(accountId, merchantId);
        if (existing != null && !merchantId.equals(existing.getId())
                || canBind == null || !canBind.isSuccess() || !Boolean.TRUE.equals(canBind.getData())) {
            throw exception(MERCHANT_IDENTITY_BIND_FAILED);
        }
        if (merchant.getOwnerAccountId() == null) {
            Integer statusTo = ProductMerchantStatusEnum.PENDING.getStatus().equals(merchant.getStatus())
                    ? ProductMerchantStatusEnum.REJECTED.getStatus() : merchant.getStatus();
            merchantMapper.updateById(new ProductMerchantDO().setId(merchantId).setOwnerAccountId(accountId)
                    .setStatus(statusTo));
            reviewRecordMapper.insert(createReviewRecord(latest, operatorId, UserTypeEnum.ADMIN.getValue(),
                    merchant.getStatus(), statusTo, operatorId, "BIND_LEGACY", "关联历史主体申请账号，补正后重新提交", LocalDateTime.now()));
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bindMerchantAccount(accountId, merchantId);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(Long ownerAccountId, ProductMerchantApplicationDraftReqVO reqVO) {
        // Serialize draft saves for one account. This also protects the application version
        // calculation, which is unique per merchant.
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountIdForUpdate(ownerAccountId);
        boolean newMerchant = merchant == null;
        if (newMerchant) {
            merchant = new ProductMerchantDO().setOwnerAccountId(ownerAccountId)
                    .setName("申请中账号-" + ownerAccountId)
                    .setStatus(ProductMerchantStatusEnum.DRAFT.getStatus());
            try {
                merchantMapper.insert(merchant);
            } catch (DuplicateKeyException ex) {
                // A concurrent first save may win the owner-account unique key. Re-read its
                // locked row and continue with the same transaction semantics as an existing
                // merchant instead of leaking a duplicate-key failure to the caller.
                merchant = merchantMapper.selectByOwnerAccountIdForUpdate(ownerAccountId);
                if (merchant == null) {
                    throw ex;
                }
                newMerchant = false;
            }
        }

        ProductMerchantApplicationDO latest = applicationMapper.selectLatestByMerchantIdForUpdate(merchant.getId());
        ProductMerchantApplicationDO application;
        if (latest != null && !"LEGACY".equals(latest.getType())
                && ProductMerchantApplicationStatusEnum.DRAFT.getStatus().equals(latest.getStatus())) {
            application = latest;
        } else {
            String type = determineNextType(merchant, latest, newMerchant);
            application = new ProductMerchantApplicationDO().setMerchantId(merchant.getId())
                    .setOwnerAccountId(ownerAccountId).setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1)
                    .setType(type).setStatus(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
            if (latest != null) {
                copyApplicationFields(latest, application);
            }
            applicationMapper.insert(application);
            if (latest != null) {
                copyDraftQualifications(latest.getId(), application.getId());
            }
        }
        applyDraftFields(application, reqVO);
        applicationMapper.updateById(application);
        replaceDraftQualifications(application, reqVO.getQualifications());
        // Only bind a committed merchant. If the RPC fails, the next draft save retries the
        // same owner-account unique row; submit and operating APIs require a valid binding.
        Long merchantId = merchant.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bindMerchantAccount(ownerAccountId, merchantId);
            }
        });
        return application.getId();
    }

    @Override
    public ProductMerchantApplicationDetailRespVO getMine(Long ownerAccountId) {
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(ownerAccountId);
        if (merchant == null) {
            return null;
        }
        ProductMerchantApplicationDO application = applicationMapper.selectLatestByMerchantId(merchant.getId());
        return application == null ? null : toDetail(application, true);
    }

    @Override
    public List<ProductMerchantApplicationDetailRespVO> getHistory(Long ownerAccountId) {
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(ownerAccountId);
        if (merchant == null) {
            return List.of();
        }
        return applicationMapper.selectListByMerchantId(merchant.getId()).stream()
                .map(application -> toDetail(application, true)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long ownerAccountId, ProductMerchantApplicationSubmitReqVO reqVO, String consentIp) {
        if (reqVO == null || !Boolean.TRUE.equals(reqVO.getAccepted())
                || !applicationProperties.matches(reqVO.getAgreementVersion(), reqVO.getPrivacyPolicyVersion())) {
            throw exception(MERCHANT_APPLICATION_TERMS_INVALID);
        }
        // Lock in merchant -> application order. Review operations use the same order to prevent
        // a concurrent submit from overwriting an already reviewed state (or deadlocking).
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountIdForUpdate(ownerAccountId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        requireMerchantAccountBinding(ownerAccountId, merchant.getId());
        ProductMerchantApplicationDO application = applicationMapper.selectLatestByMerchantIdForUpdate(merchant.getId());
        if (application == null || !ProductMerchantApplicationStatusEnum.DRAFT.getStatus().equals(application.getStatus())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        List<String> businessTypes = resolveBusinessTypes(application);
        if (businessTypes.isEmpty() || businessTypes.stream().map(businessTypeMapper::selectByCode)
                .anyMatch(businessType -> businessType == null || !Boolean.TRUE.equals(businessType.getEnabled()))) {
            throw exception(MERCHANT_BUSINESS_TYPE_NOT_EXISTS);
        }
        validateApplicationInfo(application);
        validateMerchantNameUnique(merchant.getId(), application.getMerchantName());
        snapshotQualifications(application, businessTypes);

        if (TYPE_CHANGE.equals(application.getType())) {
            if (!ProductMerchantStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())
                    && !ProductMerchantStatusEnum.CHANGE_REJECTED.getStatus().equals(merchant.getStatus())) {
                throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
            }
            int updateCount = merchantMapper.update(new ProductMerchantDO()
                            .setStatus(ProductMerchantStatusEnum.CHANGE_PENDING.getStatus()).setRejectReason(null),
                    new LambdaUpdateWrapper<ProductMerchantDO>()
                            .eq(ProductMerchantDO::getId, merchant.getId())
                            .eq(ProductMerchantDO::getStatus, merchant.getStatus()));
            if (updateCount != 1) {
                throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
            }
        } else {
            if (!ProductMerchantStatusEnum.DRAFT.getStatus().equals(merchant.getStatus())
                    && !ProductMerchantStatusEnum.REJECTED.getStatus().equals(merchant.getStatus())) {
                throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
            }
            int updateCount = merchantMapper.update(new ProductMerchantDO()
                            .setStatus(ProductMerchantStatusEnum.PENDING.getStatus()).setRejectReason(null),
                    new LambdaUpdateWrapper<ProductMerchantDO>()
                            .eq(ProductMerchantDO::getId, merchant.getId())
                            .eq(ProductMerchantDO::getStatus, merchant.getStatus()));
            if (updateCount != 1) {
                throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Integer statusFrom = merchant.getStatus();
        Integer statusTo = TYPE_CHANGE.equals(application.getType())
                ? ProductMerchantStatusEnum.CHANGE_PENDING.getStatus() : ProductMerchantStatusEnum.PENDING.getStatus();
        applicationMapper.updateById(new ProductMerchantApplicationDO().setId(application.getId())
                .setStatus(ProductMerchantApplicationStatusEnum.PENDING.getStatus()).setSubmittedTime(now)
                .setAgreementVersion(reqVO.getAgreementVersion()).setPrivacyPolicyVersion(reqVO.getPrivacyPolicyVersion())
                .setConsentAccepted(true).setConsentTime(now).setConsentIp(consentIp));
        reviewRecordMapper.insert(createReviewRecord(application, ownerAccountId, UserTypeEnum.MERCHANT.getValue(),
                statusFrom, statusTo, null, "SUBMIT", null, now));
    }

    @Override
    public PageResult<ProductMerchantApplicationDetailRespVO> getPage(ProductMerchantApplicationPageReqVO reqVO) {
        PageResult<ProductMerchantApplicationDO> page = applicationMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toPageSummary).toList(),
                page.getTotal());
    }

    @Override
    public ProductMerchantApplicationDetailRespVO get(Long applicationId) {
        ProductMerchantApplicationDO application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw exception(MERCHANT_APPLICATION_NOT_EXISTS);
        }
        return toDetail(application, true);
    }

    @Override
    public List<ProductMerchantApplicationDetailRespVO> getMerchantHistory(Long merchantId) {
        return applicationMapper.selectListByMerchantId(merchantId).stream().map(this::toPageSummary).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applicationId, Long reviewerId) {
        ProductMerchantApplicationDO application = requireApplication(applicationId);
        ProductMerchantDO merchant = merchantMapper.selectByIdForUpdate(application.getMerchantId());
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        application = requirePendingApplicationForLockedMerchant(applicationId, merchant.getId());
        if ("LEGACY".equals(application.getType())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        Integer statusFrom = merchant.getStatus();
        Integer expectedStatus = expectedReviewStatus(application);
        if (!Objects.equals(expectedStatus, statusFrom)) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        validateMerchantNameUnique(merchant.getId(), application.getMerchantName());

        // Review the immutable submitted snapshot, including file existence/ownership and
        // validity at approval time. Configuration edits must not rewrite this revision.
        for (ProductMerchantApplicationQualificationDO qualification : applicationQualificationMapper
                .selectListByApplicationId(applicationId)) {
            validateFileOwner(application.getOwnerAccountId(), qualification.getFileId());
            validateQualificationDates(qualification);
        }

        merchantMapper.updateById(copyApplicationToMerchant(application)
                .setId(merchant.getId()).setApprovedRevision(application.getVersionNo())
                .setRejectReason(null));
        replaceCurrentQualifications(merchant.getId(), application.getId());
        merchantService.approveByApplication(merchant.getId(), reviewerId, expectedStatus);
        LocalDateTime now = LocalDateTime.now();
        applicationMapper.updateById(new ProductMerchantApplicationDO().setId(application.getId())
                .setStatus(ProductMerchantApplicationStatusEnum.APPROVED.getStatus())
                .setReviewerId(reviewerId).setReviewTime(now).setRejectReason(null));
        reviewRecordMapper.insert(createReviewRecord(application, reviewerId, UserTypeEnum.ADMIN.getValue(),
                statusFrom, ProductMerchantStatusEnum.APPROVED.getStatus(), reviewerId, "APPROVE", null, now));
        ProductMerchantPayOnboardingService payOnboardingService = payOnboardingServiceProvider.getIfAvailable();
        if (payOnboardingService != null) {
            // 审批和待补料任务同事务提交，支付服务故障不会丢失入网触发。
            payOnboardingService.enqueueApprovedMerchant(merchant.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applicationId, String reason, Long reviewerId) {
        if (!StringUtils.hasText(reason)) {
            throw exception(MERCHANT_APPLICATION_REJECT_REASON_REQUIRED);
        }
        ProductMerchantApplicationDO application = requireApplication(applicationId);
        ProductMerchantDO merchant = merchantMapper.selectByIdForUpdate(application.getMerchantId());
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        application = requirePendingApplicationForLockedMerchant(applicationId, merchant.getId());
        if ("LEGACY".equals(application.getType())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        Integer statusFrom = merchant.getStatus();
        Integer expectedStatus = expectedReviewStatus(application);
        if (!Objects.equals(expectedStatus, statusFrom)) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        int applicationUpdateCount = applicationMapper.update(new ProductMerchantApplicationDO()
                        .setStatus(ProductMerchantApplicationStatusEnum.REJECTED.getStatus())
                        .setReviewerId(reviewerId).setReviewTime(now).setRejectReason(reason),
                new LambdaUpdateWrapper<ProductMerchantApplicationDO>()
                        .eq(ProductMerchantApplicationDO::getId, applicationId)
                        .eq(ProductMerchantApplicationDO::getStatus, ProductMerchantApplicationStatusEnum.PENDING.getStatus()));
        if (applicationUpdateCount != 1) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        Integer merchantStatus = TYPE_CHANGE.equals(application.getType())
                ? ProductMerchantStatusEnum.CHANGE_REJECTED.getStatus() : ProductMerchantStatusEnum.REJECTED.getStatus();
        Integer statusTo = TYPE_CHANGE.equals(application.getType())
                ? ProductMerchantStatusEnum.CHANGE_REJECTED.getStatus() : ProductMerchantStatusEnum.REJECTED.getStatus();
        int merchantUpdateCount = merchantMapper.update(new ProductMerchantDO()
                        .setStatus(merchantStatus).setReviewerId(reviewerId).setReviewTime(now).setRejectReason(reason),
                new LambdaUpdateWrapper<ProductMerchantDO>()
                        .eq(ProductMerchantDO::getId, application.getMerchantId())
                        .eq(ProductMerchantDO::getStatus, expectedStatus));
        if (merchantUpdateCount != 1) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        reviewRecordMapper.insert(createReviewRecord(application, reviewerId, UserTypeEnum.ADMIN.getValue(),
                statusFrom, statusTo, reviewerId, "REJECT", reason, now));
    }

    @Override
    public List<ProductMerchantReviewRecordRespVO> getReviewRecords(Long merchantId) {
        return reviewRecordMapper.selectListByMerchantId(merchantId).stream().map(record -> {
            ProductMerchantReviewRecordRespVO resp = new ProductMerchantReviewRecordRespVO();
            resp.setId(record.getId());
            resp.setMerchantId(record.getMerchantId());
            resp.setApplicationId(record.getApplicationId());
            resp.setVersionNo(record.getVersionNo());
            resp.setAction(record.getAction());
            resp.setReason(record.getReason());
            resp.setStatusFrom(record.getStatusFrom());
            resp.setStatusTo(record.getStatusTo());
            resp.setOperatorId(record.getOperatorId());
            resp.setOperatorType(record.getOperatorType());
            resp.setReviewerId(record.getReviewerId());
            resp.setReviewTime(record.getReviewTime());
            return resp;
        }).toList();
    }

    private String determineNextType(ProductMerchantDO merchant, ProductMerchantApplicationDO latest, boolean newMerchant) {
        if (newMerchant || latest == null || "LEGACY".equals(latest.getType())
                && ProductMerchantStatusEnum.DRAFT.getStatus().equals(merchant.getStatus())) {
            return TYPE_INITIAL;
        }
        if (ProductMerchantStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())
                || ProductMerchantStatusEnum.CHANGE_REJECTED.getStatus().equals(merchant.getStatus())) {
            return TYPE_CHANGE;
        }
        if (ProductMerchantStatusEnum.REJECTED.getStatus().equals(merchant.getStatus())) {
            return TYPE_NEW;
        }
        throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
    }

    private void applyDraftFields(ProductMerchantApplicationDO application, ProductMerchantApplicationDraftReqVO reqVO) {
        if (reqVO.getBusinessTypes() != null) {
            List<String> businessTypes = reqVO.getBusinessTypes().stream().filter(StringUtils::hasText).distinct().toList();
            application.setBusinessTypes(businessTypes)
                    .setBusinessType(businessTypes.isEmpty() ? null : businessTypes.get(0));
        }
        if (reqVO.getName() != null) application.setMerchantName(reqVO.getName());
        if (reqVO.getStoreName() != null) application.setStoreName(reqVO.getStoreName());
        if (reqVO.getLogoUrl() != null) application.setLogoUrl(reqVO.getLogoUrl());
        if (reqVO.getCompanyName() != null) application.setCompanyName(reqVO.getCompanyName());
        if (reqVO.getCreditCode() != null) application.setCreditCode(reqVO.getCreditCode());
        if (reqVO.getLegalPerson() != null) application.setLegalPerson(reqVO.getLegalPerson());
        if (reqVO.getContactName() != null) application.setContactName(reqVO.getContactName());
        if (reqVO.getContactMobile() != null) application.setContactMobile(reqVO.getContactMobile());
    }

    private void replaceDraftQualifications(ProductMerchantApplicationDO application,
                                            List<ProductMerchantApplicationQualificationReqVO> reqQualifications) {
        if (reqQualifications == null) {
            return;
        }
        Map<String, ProductMerchantQualificationRequirementDO> requirements =
                loadEnabledRequirements(resolveBusinessTypes(application));
        applicationQualificationMapper.deleteByApplicationId(application.getId());
        Set<String> codes = new HashSet<>();
        for (ProductMerchantApplicationQualificationReqVO qualification : reqQualifications) {
            if (!codes.add(qualification.getCode())) {
                throw exception(MERCHANT_APPLICATION_FILE_INVALID);
            }
            if (!requirements.containsKey(qualification.getCode())) {
                throw exception(MERCHANT_APPLICATION_FILE_INVALID);
            }
            validateFileOwner(application.getOwnerAccountId(), qualification.getFileId());
            ProductMerchantQualificationRequirementDO requirement = requirements.get(qualification.getCode());
            applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                    .setApplicationId(application.getId()).setCode(qualification.getCode())
                    .setName(requirement == null ? qualification.getCode() : requirement.getName())
                    .setRequired(requirement == null ? false : requirement.getRequired())
                    .setCertificateNoRequired(requirement != null && Boolean.TRUE.equals(requirement.getCertificateNoRequired()))
                    .setCertificateNoPattern(requirement == null ? null : requirement.getCertificateNoPattern())
                    .setValidityRequired(requirement != null && Boolean.TRUE.equals(requirement.getValidityRequired()))
                    .setCertificateNo(qualification.getCertificateNo()).setFileId(qualification.getFileId())
                    .setValidStart(qualification.getValidStart()).setValidEnd(qualification.getValidEnd()));
        }
    }

    private void snapshotQualifications(ProductMerchantApplicationDO application, List<String> businessTypes) {
        List<ProductMerchantQualificationRequirementDO> requirements =
                new ArrayList<>(loadEnabledRequirements(businessTypes).values());
        Map<String, ProductMerchantApplicationQualificationDO> submitted = new HashMap<>();
        for (ProductMerchantApplicationQualificationDO qualification : applicationQualificationMapper
                .selectListByApplicationId(application.getId())) {
            submitted.put(qualification.getCode(), qualification);
        }
        Set<String> activeCodes = new HashSet<>();
        List<ProductMerchantApplicationQualificationDO> snapshots = new ArrayList<>();
        for (ProductMerchantQualificationRequirementDO requirement : requirements) {
            activeCodes.add(requirement.getCode());
            ProductMerchantApplicationQualificationDO source = submitted.get(requirement.getCode());
            if (Boolean.TRUE.equals(requirement.getRequired()) && (source == null || source.getFileId() == null)) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            boolean provided = source != null && (source.getFileId() != null
                    || StringUtils.hasText(source.getCertificateNo())
                    || source.getValidStart() != null || source.getValidEnd() != null);
            if (!Boolean.TRUE.equals(requirement.getRequired()) && !provided) {
                continue; // Optional qualifications may be omitted entirely.
            }
            if (source == null || source.getFileId() == null) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            if (Boolean.TRUE.equals(requirement.getCertificateNoRequired())
                    && (source == null || !StringUtils.hasText(source.getCertificateNo()))) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            if (source != null && StringUtils.hasText(source.getCertificateNo())
                    && StringUtils.hasText(requirement.getCertificateNoPattern())
                    && !Pattern.matches(requirement.getCertificateNoPattern(), source.getCertificateNo())) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            if (source != null) {
                validateFileOwner(application.getOwnerAccountId(), source.getFileId());
                validateQualificationDates(source);
            }
            if (Boolean.TRUE.equals(requirement.getValidityRequired())
                    && (source == null || source.getValidStart() == null || source.getValidEnd() == null
                    || source.getValidEnd().isBefore(source.getValidStart()))) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            if (source != null && source.getValidEnd() != null && source.getValidEnd().isBefore(LocalDate.now())) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            if (source != null && source.getValidStart() != null && source.getValidStart().isAfter(LocalDate.now())) {
                throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
            }
            snapshots.add(new ProductMerchantApplicationQualificationDO().setApplicationId(application.getId())
                    .setCode(requirement.getCode()).setName(requirement.getName()).setRequired(requirement.getRequired())
                    .setCertificateNoRequired(requirement.getCertificateNoRequired())
                    .setCertificateNoPattern(requirement.getCertificateNoPattern())
                    .setValidityRequired(requirement.getValidityRequired())
                    .setCertificateNo(source == null ? null : source.getCertificateNo())
                    .setFileId(source == null ? null : source.getFileId())
                    .setValidStart(source == null ? null : source.getValidStart())
                    .setValidEnd(source == null ? null : source.getValidEnd()));
        }
        if (submitted.keySet().stream().anyMatch(code -> !activeCodes.contains(code))) {
            throw exception(MERCHANT_APPLICATION_FILE_INVALID);
        }
        applicationQualificationMapper.deleteByApplicationId(application.getId());
        snapshots.forEach(applicationQualificationMapper::insert);
    }

    private Map<String, ProductMerchantQualificationRequirementDO> loadEnabledRequirements(List<String> businessTypes) {
        Map<String, ProductMerchantQualificationRequirementDO> requirements = new HashMap<>();
        for (String businessType : businessTypes) {
            for (ProductMerchantQualificationRequirementDO requirement :
                    requirementMapper.selectEnabledListByBusinessType(businessType)) {
                ProductMerchantQualificationRequirementDO existing = requirements.get(requirement.getCode());
                if (existing == null) {
                    requirements.put(requirement.getCode(), requirement);
                } else {
                    existing.setRequired(Boolean.TRUE.equals(existing.getRequired())
                            || Boolean.TRUE.equals(requirement.getRequired()));
                    existing.setCertificateNoRequired(Boolean.TRUE.equals(existing.getCertificateNoRequired())
                            || Boolean.TRUE.equals(requirement.getCertificateNoRequired()));
                    existing.setValidityRequired(Boolean.TRUE.equals(existing.getValidityRequired())
                            || Boolean.TRUE.equals(requirement.getValidityRequired()));
                    existing.setCertificateNoPattern(mergeCertificateNoPatterns(
                            existing.getCertificateNoPattern(), requirement.getCertificateNoPattern()));
                }
            }
        }
        return requirements;
    }

    private String mergeCertificateNoPatterns(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second) || first.equals(second)) {
            return first;
        }
        // Each selected business type must match the complete number, not a substring.
        return "(?=(?:" + first + ")\\z)(?:" + second + ")";
    }

    private void validateQualificationDates(ProductMerchantApplicationQualificationDO qualification) {
        if (qualification.getValidStart() != null && qualification.getValidEnd() != null
                && qualification.getValidEnd().isBefore(qualification.getValidStart())
                || qualification.getValidEnd() != null && qualification.getValidEnd().isBefore(LocalDate.now())
                || qualification.getValidStart() != null && qualification.getValidStart().isAfter(LocalDate.now())) {
            throw exception(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED);
        }
    }

    private List<String> resolveBusinessTypes(ProductMerchantApplicationDO application) {
        if (application.getBusinessTypes() != null && !application.getBusinessTypes().isEmpty()) {
            return application.getBusinessTypes().stream().filter(StringUtils::hasText).distinct().toList();
        }
        return StringUtils.hasText(application.getBusinessType()) ? List.of(application.getBusinessType()) : List.of();
    }

    private void validateFileOwner(Long ownerAccountId, Long fileId) {
        if (fileId == null) {
            return;
        }
        FileApi fileApi = fileApiProvider == null ? null : fileApiProvider.getIfAvailable();
        var result = fileApi == null ? null : fileApi.isMerchantQualificationFileOwnedBy(fileId, ownerAccountId);
        if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
            throw exception(MERCHANT_APPLICATION_FILE_INVALID);
        }
    }

    private void copyApplicationFields(ProductMerchantApplicationDO source, ProductMerchantApplicationDO target) {
        List<String> businessTypes = resolveBusinessTypes(source);
        target.setBusinessTypes(businessTypes).setBusinessType(businessTypes.isEmpty() ? null : businessTypes.get(0))
                .setMerchantName(source.getMerchantName())
                .setStoreName(source.getStoreName()).setLogoUrl(source.getLogoUrl())
                .setCompanyName(source.getCompanyName()).setCreditCode(source.getCreditCode())
                .setLegalPerson(source.getLegalPerson()).setContactName(source.getContactName())
                .setContactMobile(source.getContactMobile());
    }

    private void copyDraftQualifications(Long sourceApplicationId, Long targetApplicationId) {
        for (ProductMerchantApplicationQualificationDO source : applicationQualificationMapper
                .selectListByApplicationId(sourceApplicationId)) {
            applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                    .setApplicationId(targetApplicationId).setCode(source.getCode()).setName(source.getName())
                    .setRequired(source.getRequired()).setCertificateNo(source.getCertificateNo())
                    .setCertificateNoRequired(source.getCertificateNoRequired())
                    .setCertificateNoPattern(source.getCertificateNoPattern())
                    .setValidityRequired(source.getValidityRequired())
                    .setFileId(source.getFileId()).setValidStart(source.getValidStart())
                    .setValidEnd(source.getValidEnd()));
        }
    }

    private void validateApplicationInfo(ProductMerchantApplicationDO application) {
        if (!StringUtils.hasText(application.getMerchantName()) || !StringUtils.hasText(application.getStoreName())
                || !StringUtils.hasText(application.getCompanyName()) || !StringUtils.hasText(application.getCreditCode())
                || !StringUtils.hasText(application.getLegalPerson()) || !StringUtils.hasText(application.getContactName())
                || !StringUtils.hasText(application.getContactMobile())) {
            throw exception(MERCHANT_APPLICATION_INFO_INCOMPLETE);
        }
    }

    private void validateMerchantNameUnique(Long merchantId, String name) {
        ProductMerchantDO duplicate = merchantMapper.selectByName(name);
        if (duplicate != null && !duplicate.getId().equals(merchantId)) {
            throw exception(MERCHANT_NAME_EXISTS);
        }
    }

    private void bindMerchantAccount(Long ownerAccountId, Long merchantId) {
        SystemMerchantUserApi api = merchantUserApiProvider == null
                ? null : merchantUserApiProvider.getIfAvailable();
        if (api == null) {
            throw exception(MERCHANT_IDENTITY_BIND_FAILED);
        }
        try {
            var result = api.bindMerchantId(ownerAccountId, merchantId);
            if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                throw exception(MERCHANT_IDENTITY_BIND_FAILED);
            }
        } catch (Exception ex) {
            log.warn("[bindMerchantAccount][merchantId({}) ownerAccountId({})] binding failed",
                    merchantId, ownerAccountId, ex);
            throw exception(MERCHANT_IDENTITY_BIND_FAILED);
        }
    }

    private void requireMerchantAccountBinding(Long ownerAccountId, Long merchantId) {
        SystemMerchantUserApi api = merchantUserApiProvider == null
                ? null : merchantUserApiProvider.getIfAvailable();
        if (api == null) {
            throw exception(MERCHANT_IDENTITY_BIND_FAILED);
        }
        try {
            var result = api.validateMerchantBinding(ownerAccountId, merchantId);
            if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                throw exception(MERCHANT_IDENTITY_BIND_FAILED);
            }
        } catch (Exception ex) {
            log.warn("[requireMerchantAccountBinding][merchantId({}) ownerAccountId({})] binding validation failed",
                    merchantId, ownerAccountId, ex);
            throw exception(MERCHANT_IDENTITY_BIND_FAILED);
        }
    }

    private ProductMerchantApplicationDetailRespVO toDetail(ProductMerchantApplicationDO application, boolean withFiles) {
        ProductMerchantApplicationDetailRespVO resp = new ProductMerchantApplicationDetailRespVO();
        resp.setId(application.getId());
        resp.setMerchantId(application.getMerchantId());
        resp.setOwnerAccountId(application.getOwnerAccountId());
        resp.setVersionNo(application.getVersionNo());
        resp.setType(application.getType());
        resp.setStatus(application.getStatus());
        ProductMerchantDO merchant = merchantMapper.selectById(application.getMerchantId());
        resp.setMerchantStatus(merchant == null ? null : merchant.getStatus());
        resp.setCurrentOwnerAccountId(merchant == null ? null : merchant.getOwnerAccountId());
        List<String> businessTypes = resolveBusinessTypes(application);
        resp.setBusinessTypes(businessTypes);
        resp.setBusinessType(businessTypes.isEmpty() ? null : businessTypes.get(0));
        resp.setName(application.getMerchantName());
        resp.setStoreName(application.getStoreName());
        resp.setLogoUrl(application.getLogoUrl());
        resp.setCompanyName(application.getCompanyName());
        resp.setCreditCode(application.getCreditCode());
        resp.setLegalPerson(application.getLegalPerson());
        resp.setContactName(application.getContactName());
        resp.setContactMobile(application.getContactMobile());
        resp.setSubmittedTime(application.getSubmittedTime());
        resp.setAgreementVersion(application.getAgreementVersion());
        resp.setPrivacyPolicyVersion(application.getPrivacyPolicyVersion());
        resp.setConsentAccepted(application.getConsentAccepted());
        resp.setConsentTime(application.getConsentTime());
        resp.setConsentIp(application.getConsentIp());
        resp.setReviewerId(application.getReviewerId());
        resp.setReviewTime(application.getReviewTime());
        resp.setRejectReason(application.getRejectReason());
        List<ProductMerchantApplicationQualificationRespVO> qualifications = applicationQualificationMapper
                .selectListByApplicationId(application.getId()).stream().map(qualification -> {
                    ProductMerchantApplicationQualificationRespVO item = new ProductMerchantApplicationQualificationRespVO();
                    item.setCode(qualification.getCode());
                    item.setName(qualification.getName());
                    item.setRequired(qualification.getRequired());
                    item.setCertificateNoRequired(qualification.getCertificateNoRequired());
                    item.setCertificateNoPattern(qualification.getCertificateNoPattern());
                    item.setValidityRequired(qualification.getValidityRequired());
                    item.setCertificateNo(qualification.getCertificateNo());
                    item.setFileId(qualification.getFileId());
                    item.setValidStart(qualification.getValidStart());
                    item.setValidEnd(qualification.getValidEnd());
                    item.setPreviewUnavailable(qualification.getFileId() == null
                            && StringUtils.hasText(qualification.getLegacyFileUrl()));
                    if (withFiles && qualification.getFileId() != null && fileApiProvider != null) {
                        FileApi fileApi = fileApiProvider.getIfAvailable();
                        if (fileApi != null) {
                            try {
                                var result = fileApi.presignGetUrl(qualification.getFileId(), 900);
                                if (result == null || !result.isSuccess() || !StringUtils.hasText(result.getData())) {
                                    item.setPreviewUnavailable(true);
                                } else {
                                    item.setPreviewUrl(result.getData());
                                }
                            } catch (RuntimeException ex) {
                                // 文件服务不可用不能阻断审核人员查看资料及退回补正。
                                // 不记录异常内容，避免存储地址或签名泄露到日志。
                                item.setPreviewUnavailable(true);
                            }
                        } else {
                            item.setPreviewUnavailable(true);
                        }
                    }
                    return item;
                }).toList();
        resp.setQualifications(qualifications);
        return resp;
    }

    /** 列表只返回审核筛选和状态展示所需字段，主体及证件信息仅允许在详情接口读取。 */
    private ProductMerchantApplicationDetailRespVO toPageSummary(ProductMerchantApplicationDO application) {
        ProductMerchantApplicationDetailRespVO resp = new ProductMerchantApplicationDetailRespVO();
        resp.setId(application.getId());
        resp.setMerchantId(application.getMerchantId());
        resp.setVersionNo(application.getVersionNo());
        resp.setType(application.getType());
        resp.setStatus(application.getStatus());
        ProductMerchantDO merchant = merchantMapper.selectById(application.getMerchantId());
        resp.setMerchantStatus(merchant == null ? null : merchant.getStatus());
        resp.setName(application.getMerchantName());
        resp.setStoreName(application.getStoreName());
        resp.setBusinessTypes(resolveBusinessTypes(application));
        resp.setBusinessType(StringUtils.hasText(application.getBusinessType()) ? application.getBusinessType()
                : (resolveBusinessTypes(application).isEmpty() ? null : resolveBusinessTypes(application).get(0)));
        resp.setSubmittedTime(application.getSubmittedTime());
        resp.setReviewerId(application.getReviewerId());
        resp.setReviewTime(application.getReviewTime());
        resp.setRejectReason(application.getRejectReason());
        return resp;
    }

    private ProductMerchantDO requireOwnedMerchant(Long ownerAccountId) {
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(ownerAccountId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    private ProductMerchantDO requireMerchant(Long merchantId) {
        ProductMerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    private ProductMerchantApplicationDO requireApplication(Long applicationId) {
        ProductMerchantApplicationDO application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw exception(MERCHANT_APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private ProductMerchantApplicationDO requirePendingApplicationForLockedMerchant(Long applicationId,
                                                                                       Long merchantId) {
        ProductMerchantApplicationDO application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null) {
            throw exception(MERCHANT_APPLICATION_NOT_EXISTS);
        }
        if (!Objects.equals(merchantId, application.getMerchantId())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        if (!ProductMerchantApplicationStatusEnum.PENDING.getStatus().equals(application.getStatus())) {
            throw exception(MERCHANT_APPLICATION_STATUS_ERROR);
        }
        return application;
    }

    private Integer expectedReviewStatus(ProductMerchantApplicationDO application) {
        return TYPE_CHANGE.equals(application.getType())
                ? ProductMerchantStatusEnum.CHANGE_PENDING.getStatus()
                : ProductMerchantStatusEnum.PENDING.getStatus();
    }

    private ProductMerchantDO copyApplicationToMerchant(ProductMerchantApplicationDO application) {
        List<String> businessTypes = resolveBusinessTypes(application);
        return new ProductMerchantDO().setBusinessTypes(businessTypes)
                .setBusinessType(businessTypes.isEmpty() ? null : businessTypes.get(0))
                .setName(application.getMerchantName()).setStoreName(application.getStoreName())
                .setLogoUrl(application.getLogoUrl()).setCompanyName(application.getCompanyName())
                .setCreditCode(application.getCreditCode()).setLegalPerson(application.getLegalPerson())
                .setContactName(application.getContactName()).setContactMobile(application.getContactMobile());
    }

    private void replaceCurrentQualifications(Long merchantId, Long applicationId) {
        qualificationMapper.deleteByMerchantId(merchantId);
        for (ProductMerchantApplicationQualificationDO snapshot : applicationQualificationMapper
                .selectListByApplicationId(applicationId)) {
            if (snapshot.getFileId() == null) {
                continue;
            }
            qualificationMapper.insert(new ProductMerchantQualificationDO().setMerchantId(merchantId)
                    .setType(snapshot.getCode()).setCertificateNo(snapshot.getCertificateNo())
                    .setFileId(snapshot.getFileId()).setFileUrl("").setValidStart(snapshot.getValidStart())
                    .setValidEnd(snapshot.getValidEnd())
                    .setStatus(ProductMerchantQualificationStatusEnum.APPROVED.getStatus()));
        }
    }

    private ProductMerchantReviewRecordDO createReviewRecord(ProductMerchantApplicationDO application, Long operatorId,
                                                              Integer operatorType, Integer statusFrom, Integer statusTo,
                                                              Long reviewerId, String action, String reason,
                                                              LocalDateTime now) {
        return new ProductMerchantReviewRecordDO().setMerchantId(application.getMerchantId())
                .setApplicationId(application.getId()).setVersionNo(application.getVersionNo())
                .setAction(action).setReason(reason).setStatusFrom(statusFrom).setStatusTo(statusTo)
                .setOperatorId(operatorId).setOperatorType(operatorType).setReviewerId(reviewerId).setReviewTime(now);
    }
}
