package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintTaskRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsTraceRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.*;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfLogisticsClient;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
public class LogisticsWaybillServiceImpl implements LogisticsWaybillService {

    @Resource private TradeOrderMapper orderMapper;
    @Resource private TradeOrderItemMapper orderItemMapper;
    @Resource private TradeOrderSubMapper orderSubMapper;
    @Resource private TradeLogisticsAccountMapper accountMapper;
    @Resource private TradeMerchantLogisticsConfigService merchantLogisticsConfigService;
    @Resource private TradeLogisticsWaybillMapper waybillMapper;
    @Resource private TradeWechatLogisticsWaybillMapper wechatWaybillMapper;
    @Resource private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Resource private TradeLogisticsPrintTaskMapper taskMapper;
    @Resource private TradeLogisticsTraceMapper traceMapper;
    @Resource private SfLogisticsClient sfClient;
    @Resource private SfLabelTemplateResolver templateResolver;
    @Resource private LogisticsLabelNormalizer labelNormalizer;
    @Resource private FileApi fileApi;
    @Resource private ProductSkuApi productSkuApi;
    @Resource private TradeOrderUpdateService orderUpdateService;
    @Resource private DeliveryExpressService deliveryExpressService;
    @Resource private PlatformTransactionManager transactionManager;

    @Override
    public LogisticsWaybillRespVO createWaybill(LogisticsWaybillCreateReqVO request) {
        CreationPreparation preparation = inTransaction(() -> prepareCreation(request));
        TradeLogisticsWaybillDO waybill = preparation.waybill();
        if (preparation.task() != null) {
            return toResp(waybill, preparation.task()).setReused(preparation.taskReused());
        }
        TradeLogisticsAccountDO account = preparation.account();
        if (preparation.newRecord() || LogisticsWaybillStatusEnum.FAILED.name().equals(waybill.getStatus())) {
            createRemoteWaybill(waybill, account);
        } else if (List.of(LogisticsWaybillStatusEnum.CREATING.name(),
                LogisticsWaybillStatusEnum.UNKNOWN.name()).contains(waybill.getStatus())) {
            queryRemoteWaybill(waybill, account);
        }
        if (!LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            return toResp(waybill, null);
        }
        return createLabelAndTask(waybill, account, preparation.device());
    }

    private CreationPreparation prepareCreation(LogisticsWaybillCreateReqVO request) {
        TradeOrderDO order = orderMapper.selectByIdForUpdate(request.getOrderId());
        validateOrder(order);
        TradeOrderSubDO subOrder = validateSubOrder(order, request.getSubOrderId());
        Long merchantId = subOrder.getMerchantId();
        TradeWechatLogisticsWaybillDO wechatWaybill = wechatWaybillMapper.selectByOrderIdForUpdate(order.getId());
        if (wechatWaybill != null && List.of("CREATING", "CREATED", "UNKNOWN")
                .contains(wechatWaybill.getStatus())) {
            throw exception(WECHAT_LOGISTICS_WAYBILL_ALREADY_EXISTS);
        }
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByOrderIdForUpdate(order.getId(), subOrder.getId());
        boolean newRecord = waybill == null || LogisticsWaybillStatusEnum.CANCELLED.name().equals(waybill.getStatus());
        if (newRecord) {
            TradeLogisticsAccountDO account = getAccount(request.getAccountId());
            TradeMerchantLogisticsConfigDO config = merchantLogisticsConfigService.getRequiredShippingConfig(merchantId);
            TradeLogisticsPrintDeviceDO device = getDevice(merchantId, request.getDeviceId());
            String providerOrderNo = "YD-" + TenantContextHolder.getTenantId() + "-" + order.getId() + "-"
                    + (waybill == null ? subOrder.getId().toString() : IdUtil.fastSimpleUUID());
            waybill = new TradeLogisticsWaybillDO().setOrderId(order.getId()).setSubOrderId(subOrder.getId())
                    .setMerchantId(merchantId).setOrderNo(order.getNo()).setAccountId(account.getId())
                    .setLogisticsId(account.getLogisticsId()).setRequestedDeviceId(device.getId())
                    .setProviderOrderNo(providerOrderNo).setStatus(LogisticsWaybillStatusEnum.CREATING.name())
                    .setDeliveryStatus("PENDING").setPaperWidthMm(config.getPaperWidthMm())
                    .setPaperHeightMm(config.getPaperHeightMm()).setDpi(config.getDpi())
                    .setTemplateCode(templateResolver.requireTemplate(account, config.getPaperWidthMm(),
                            config.getPaperHeightMm(), config.getDpi()))
                    .setCreateRequestSnapshot(buildCreateSnapshot(config, providerOrderNo, order, subOrder.getId()));
            waybillMapper.insert(waybill);
            return new CreationPreparation(account, device, waybill, null, true, false);
        }

        TradeLogisticsPrintTaskDO task = taskMapper.selectLatestByWaybillIdForUpdate(waybill.getId());
        if (task != null && !LogisticsPrintTaskStatusEnum.FAILED.name().equals(task.getStatus())) {
            return new CreationPreparation(null, null, waybill, task, false, true);
        }
        boolean rebuildLegacyRequest = waybill.getCreateRequestSnapshot() == null
                && LogisticsWaybillStatusEnum.FAILED.name().equals(waybill.getStatus()) && task == null;
        TradeLogisticsPrintDeviceDO device = rebuildLegacyRequest ? getDevice(merchantId, request.getDeviceId())
                : getRetryDevice(waybill, merchantId, request.getDeviceId());
        if (task != null) {
            TradeLogisticsPrintTaskDO retryTask = newTask(waybill, device);
            taskMapper.insert(retryTask);
            return new CreationPreparation(null, device, waybill, retryTask, false, false);
        }
        TradeLogisticsAccountDO account = getAccount(waybill.getAccountId());
        // 旧版本在面单生成成功后才保存纸张。只为已存在的运单补齐其原接入账号上的规格。
        if (waybill.getPaperWidthMm() == null) {
            waybill.setPaperWidthMm(account.getPaperWidthMm()).setPaperHeightMm(account.getPaperHeightMm())
                    .setTemplateCode(account.getTemplateCode()).setDpi(account.getDpi());
            waybillMapper.updateById(waybill);
        }
        if (rebuildLegacyRequest) {
            // 已明确失败的旧运单没有请求快照。使用当前商家确认的完整设置重新准备请求。
            TradeMerchantLogisticsConfigDO config = merchantLogisticsConfigService.getRequiredShippingConfig(merchantId);
            waybill.setRequestedDeviceId(device.getId())
                    .setCreateRequestSnapshot(buildCreateSnapshot(config, waybill.getProviderOrderNo(), order, subOrder.getId()))
                    .setPaperWidthMm(config.getPaperWidthMm()).setPaperHeightMm(config.getPaperHeightMm())
                    .setDpi(config.getDpi()).setTemplateCode(templateResolver.requireTemplate(account,
                            config.getPaperWidthMm(), config.getPaperHeightMm(), config.getDpi()));
            waybillMapper.updateById(waybill);
        }
        return new CreationPreparation(account, device, waybill, null, false, false);
    }

    private String buildCreateSnapshot(TradeMerchantLogisticsConfigDO config, String providerOrderNo,
                                       TradeOrderDO order, Long subOrderId) {
        List<TradeOrderItemDO> items = orderItemMapper.selectListBySubOrderId(subOrderId);
        Map<Long, ProductSkuRespDTO> skus = items.isEmpty() ? Map.of()
                : productSkuApi.getSkuMap(items.stream().map(TradeOrderItemDO::getSkuId).collect(Collectors.toSet()));
        return SfLogisticsClient.buildCreateRequest(config, providerOrderNo, order, items, skus).toString();
    }

    private void createRemoteWaybill(TradeLogisticsWaybillDO waybill, TradeLogisticsAccountDO account) {
        try {
            markCreated(waybill, sfClient.createWaybill(account, JsonUtils.parseTree(waybill.getCreateRequestSnapshot())));
        } catch (SfApiException exception) {
            updateFailure(waybill, exception);
        }
    }

    private void queryRemoteWaybill(TradeLogisticsWaybillDO waybill, TradeLogisticsAccountDO account) {
        try {
            markCreated(waybill, sfClient.queryByProviderOrderNo(account, waybill.getProviderOrderNo()));
        } catch (SfApiException exception) {
            updateFailure(waybill, exception);
        }
    }

    private LogisticsWaybillRespVO createLabelAndTask(TradeLogisticsWaybillDO waybill,
                                                        TradeLogisticsAccountDO account,
                                                        TradeLogisticsPrintDeviceDO device) {
        try {
            var privateStorage = fileApi.isPrivateMasterSupported();
            if (privateStorage == null || !Boolean.TRUE.equals(privateStorage.getData())) {
                throw exception(LOGISTICS_PRIVATE_STORAGE_REQUIRED);
            }
            byte[] pdf = sfClient.getLabel(account, waybill.getWaybillNo(), waybill.getTemplateCode());
            byte[] png = labelNormalizer.normalizePdf(pdf, waybill.getPaperWidthMm(), waybill.getPaperHeightMm(),
                    waybill.getDpi());
            String checksum = DigestUtil.sha256Hex(png);
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            FileCreateRespDTO labelFile = fileApi.createPrivateFile(png, waybill.getWaybillNo() + ".png",
                    "trade/logistics/" + tenantId + "/labels", "image/png");
            return inTransaction(() -> persistLabelAndTask(waybill, device, labelFile,
                    checksum, png.length));
        } catch (RuntimeException exception) {
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
                if (current == null) current = waybill;
                current.setErrorCode("LABEL_FAILED").setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
                waybill.setErrorCode(current.getErrorCode()).setErrorMessage(current.getErrorMessage());
                return null;
            });
            return toResp(waybill, null);
        }
    }

    private LogisticsWaybillRespVO persistLabelAndTask(TradeLogisticsWaybillDO waybill,
                                                       TradeLogisticsPrintDeviceDO device,
                                                        FileCreateRespDTO labelFile, String checksum, int labelSize) {
        TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
        if (current == null) current = waybill;
        TradeLogisticsPrintTaskDO existingTask = taskMapper.selectLatestByWaybillId(waybill.getId());
        if (existingTask != null) {
            return toResp(current, existingTask).setReused(true);
        }
        current.setLabelFileId(labelFile.getId()).setLabelUrl(labelFile.getUrl())
                .setLabelContentType("image/png").setLabelChecksum(checksum)
                .setLabelSize((long) labelSize).setErrorCode(null).setErrorMessage(null);
        waybillMapper.updateById(current);
        TradeLogisticsPrintTaskDO task = newTask(current, device);
        taskMapper.insert(task);
        return toResp(current, task);
    }

    private void markCreated(TradeLogisticsWaybillDO waybill, SfLogisticsClient.WaybillResult result) {
        inTransaction(() -> {
            TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
            if (current == null) current = waybill;
            current.setWaybillNo(result.waybillNo()).setStatus(LogisticsWaybillStatusEnum.CREATED.name())
                    .setProviderResponse(StrUtil.maxLength(JsonUtils.toJsonString(result.rawResponse()), 10_000))
                    .setLastSyncTime(LocalDateTime.now()).setErrorCode(null).setErrorMessage(null);
            waybillMapper.updateById(current);
            copyRemoteState(current, waybill);
            return null;
        });
    }

    private void updateFailure(TradeLogisticsWaybillDO waybill, SfApiException exception) {
        inTransaction(() -> {
            TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
            if (current == null) current = waybill;
            if (!LogisticsWaybillStatusEnum.CREATED.name().equals(current.getStatus())) {
                current.setStatus(exception.isUnknownResult() ? LogisticsWaybillStatusEnum.UNKNOWN.name()
                                : LogisticsWaybillStatusEnum.FAILED.name())
                        .setErrorCode(exception.getCode())
                        .setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
            }
            copyRemoteState(current, waybill);
            return null;
        });
    }

    private TradeLogisticsPrintTaskDO newTask(TradeLogisticsWaybillDO waybill,
                                               TradeLogisticsPrintDeviceDO device) {
        return new TradeLogisticsPrintTaskDO().setRequestId("REQ-" + IdUtil.fastSimpleUUID())
                .setJobId("JOB-" + IdUtil.fastSimpleUUID()).setOrderId(waybill.getOrderId())
                .setWaybillId(waybill.getId()).setMerchantId(device.getMerchantId()).setDeviceId(device.getId())
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name()).setFormat("image")
                .setLabelFileId(waybill.getLabelFileId()).setLabelUrl(waybill.getLabelUrl())
                .setChecksum(waybill.getLabelChecksum())
                .setPaperWidthMm(waybill.getPaperWidthMm()).setPaperHeightMm(waybill.getPaperHeightMm())
                .setDpi(waybill.getDpi()).setCopies(1).setTestFlag(false);
    }

    @Override
    public LogisticsWaybillRespVO getWaybill(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectById(id);
        if (waybill == null) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        return toResp(waybill, taskMapper.selectLatestByWaybillId(id));
    }

    @Override
    public List<LogisticsWaybillRespVO> getWaybills() {
        return waybillMapper.selectListAll().stream()
                .map(waybill -> toResp(waybill, taskMapper.selectLatestByWaybillId(waybill.getId()))).toList();
    }

    @Override
    public List<LogisticsPrintTaskRespVO> getPrintTasks() {
        return taskMapper.selectListAll().stream().map(this::toTaskResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsWaybillRespVO reprint(Long id, Long deviceId) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(id);
        if (waybill == null || !LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        TradeLogisticsPrintTaskDO previous = taskMapper.selectLatestByWaybillId(id);
        if (previous != null && !List.of(LogisticsPrintTaskStatusEnum.FAILED.name(),
                LogisticsPrintTaskStatusEnum.UNKNOWN.name()).contains(previous.getStatus())) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        TradeLogisticsPrintTaskDO task = newTask(waybill, getRetryDevice(waybill, waybill.getMerchantId(), deviceId));
        taskMapper.insert(task);
        return toResp(waybill, task);
    }

    @Override
    public void cancelWaybill(Long id) {
        CancellationPreparation preparation = inTransaction(() -> prepareCancellation(id));
        try {
            sfClient.cancelWaybill(preparation.account(), preparation.waybill().getProviderOrderNo());
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(id);
                current.setStatus(LogisticsWaybillStatusEnum.CANCELLED.name()).setCancelledTime(LocalDateTime.now());
                waybillMapper.updateById(current);
                return null;
            });
        } catch (RuntimeException exception) {
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(id);
                current.setStatus(LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name())
                        .setErrorCode("CANCEL_FAILED").setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
                return null;
            });
            throw exception;
        }
    }

    private CancellationPreparation prepareCancellation(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(id);
        if (waybill == null || !List.of(LogisticsWaybillStatusEnum.CREATED.name(),
                        LogisticsWaybillStatusEnum.CANCELLING.name(),
                        LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name()).contains(waybill.getStatus())
                || !"PENDING".equals(waybill.getDeliveryStatus())) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        TradeLogisticsPrintTaskDO task = taskMapper.selectLatestByWaybillIdForUpdate(id);
        if (task != null && !List.of(LogisticsPrintTaskStatusEnum.PENDING.name(),
                LogisticsPrintTaskStatusEnum.CANCELLED.name()).contains(task.getStatus())) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        if (task != null) taskMapper.updateById(task.setStatus(LogisticsPrintTaskStatusEnum.CANCELLED.name()));
        waybillMapper.updateById(waybill.setStatus(LogisticsWaybillStatusEnum.CANCELLING.name()));
        return new CancellationPreparation(waybill, task, getAccount(waybill.getAccountId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LogisticsTraceRespVO> syncTrace(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectById(id);
        if (waybill == null || !LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        JsonNode routes = sfClient.queryTrace(getAccount(waybill.getAccountId()), waybill.getWaybillNo());
        for (JsonNode route : extractRoutes(routes)) {
            String content = route.path("remark").asText();
            LocalDateTime time = parseTime(route.path("acceptTime").asText());
            if (StrUtil.isBlank(content) || time == null) {
                continue;
            }
            String status = route.path("opCode").asText();
            String location = route.path("acceptAddress").asText();
            String providerEventId = route.path("id").asText(null);
            if (StrUtil.isBlank(providerEventId)) {
                providerEventId = "poll-" + DigestUtil.sha256Hex(String.join("\u0000", waybill.getWaybillNo(),
                        time.toString(), status, content, location));
            }
            if (traceMapper.selectByWaybillIdAndProviderEventId(id, providerEventId) != null) {
                continue;
            }
            try {
                traceMapper.insert(new TradeLogisticsTraceDO().setWaybillId(id)
                        .setProviderEventId(providerEventId).setStatus(status).setContent(content)
                        .setLocation(location).setOperateTime(time).setRawData(JsonUtils.toJsonString(route)));
            } catch (DuplicateKeyException ignored) {
                // 路由回调与主动查询可能并发写入；唯一键负责最终幂等。
            }
        }
        waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(id).setLastSyncTime(LocalDateTime.now()));
        return traceMapper.selectListByWaybillId(id).stream().map(this::toTraceResp).toList();
    }

    @Override
    public List<LogisticsTraceRespVO> getTrace(Long id) {
        if (waybillMapper.selectById(id) == null) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        return traceMapper.selectListByWaybillId(id).stream().map(this::toTraceResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateManualDelivery(Long orderId, Long subOrderId, Long logisticsId, String logisticsNo) {
        orderMapper.selectByIdForUpdate(orderId);
        var express = deliveryExpressService.getDeliveryExpress(logisticsId);
        if (express != null && "SF".equalsIgnoreCase(express.getCode())) {
            throw exception(LOGISTICS_SF_MANUAL_DELIVERY_NOT_ALLOWED);
        }
        TradeLogisticsWaybillDO waybill = subOrderId == null
                ? waybillMapper.selectActiveByOrderId(orderId)
                : waybillMapper.selectActiveByOrderId(orderId, subOrderId);
        if (waybill == null || !List.of(LogisticsWaybillStatusEnum.CREATING.name(),
                LogisticsWaybillStatusEnum.CREATED.name(), LogisticsWaybillStatusEnum.UNKNOWN.name(),
                LogisticsWaybillStatusEnum.CANCELLING.name(), LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name())
                .contains(waybill.getStatus())) {
            return;
        }
        throw exception(LOGISTICS_WAYBILL_ALREADY_EXISTS);
    }

    public void validateManualDelivery(Long orderId, Long logisticsId, String logisticsNo) {
        validateManualDelivery(orderId, null, logisticsId, logisticsNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverManually(TradeOrderDeliveryReqVO request) {
        validateManualDelivery(request.getId(), request.getSubOrderId(), request.getLogisticsId(), request.getLogisticsNo());
        orderUpdateService.deliveryOrder(request);
    }

    private void validateOrder(TradeOrderDO order) {
        if (order == null || !TradeOrderStatusEnum.isUndelivered(order.getStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        }
        if (!DeliveryTypeEnum.EXPRESS.getType().equals(order.getDeliveryType())) {
            throw exception(ORDER_DELIVERY_FAIL_DELIVERY_TYPE_NOT_EXPRESS);
        }
        if (!TradeOrderRefundStatusEnum.NONE.getStatus().equals(order.getRefundStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE);
        }
    }

    private TradeLogisticsAccountDO getAccount(Long id) {
        TradeLogisticsAccountDO account = id == null ? accountMapper.selectDefaultEnabled() : accountMapper.selectById(id);
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        if (account.getStatus() == null || account.getStatus() != 0) throw exception(LOGISTICS_ACCOUNT_DISABLED);
        return account;
    }

    private TradeLogisticsPrintDeviceDO getRetryDevice(TradeLogisticsWaybillDO waybill, Long merchantId, Long deviceId) {
        Long originalId = waybill.getRequestedDeviceId();
        if (originalId != null && (deviceId == null || originalId.equals(deviceId))) {
            TradeLogisticsPrintDeviceDO original = deviceMapper.selectById(originalId);
            if (original != null && (merchantId != null && merchantId.equals(original.getMerchantId())
                    || waybill.getCreateRequestSnapshot() == null && original.getMerchantId() == null)) {
                if (deviceId != null || merchantId == null || isReadyDevice(original)) {
                    return requireReadyDevice(original);
                }
            }
        }
        return getDevice(merchantId, deviceId);
    }

    private TradeLogisticsPrintDeviceDO getDevice(Long merchantId, Long deviceId) {
        if (merchantId == null) throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        TradeLogisticsPrintDeviceDO device = deviceId == null ? deviceMapper.selectDefaultEnabled(merchantId)
                : deviceMapper.selectByIdAndMerchantId(deviceId, merchantId);
        if (deviceId == null && device == null) {
            throw exception(LOGISTICS_CONFIGURATION_INVALID, "请在商家顺丰发货设置中绑定默认打印设备");
        }
        return requireReadyDevice(device);
    }

    private TradeLogisticsPrintDeviceDO requireReadyDevice(TradeLogisticsPrintDeviceDO device) {
        if (device == null || device.getStatus() == null || device.getStatus() != 0) {
            throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        }
        if (!isReadyDevice(device)) {
            throw exception(LOGISTICS_DEVICE_NOT_READY);
        }
        return device;
    }

    private boolean isReadyDevice(TradeLogisticsPrintDeviceDO device) {
        return Integer.valueOf(0).equals(device.getStatus()) && StrUtil.isBlank(device.getEnrollmentKey())
                && !StrUtil.startWith(device.getDeviceCode(), "pending-") && StrUtil.isNotBlank(device.getPrinterName());
    }

    private LogisticsWaybillRespVO toResp(TradeLogisticsWaybillDO waybill, TradeLogisticsPrintTaskDO task) {
        TradeOrderSubDO subOrder = waybill.getSubOrderId() == null ? null : orderSubMapper.selectById(waybill.getSubOrderId());
        String monthlyCardMasked = null;
        if (waybill.getCreateRequestSnapshot() != null) {
            String card = JsonUtils.parseTree(waybill.getCreateRequestSnapshot()).path("monthlyCard").textValue();
            if (StrUtil.isNotBlank(card)) {
                monthlyCardMasked = card.length() > 4 ? "****" + StrUtil.subSuf(card, card.length() - 4) : "****";
            }
        }
        return new LogisticsWaybillRespVO().setId(waybill.getId()).setOrderId(waybill.getOrderId())
                .setSubOrderId(waybill.getSubOrderId()).setMerchantId(waybill.getMerchantId())
                .setMerchantName(subOrder == null ? null : subOrder.getMerchantName())
                .setMerchantMonthlyCardMasked(monthlyCardMasked)
                .setOrderNo(waybill.getOrderNo()).setProviderOrderNo(waybill.getProviderOrderNo())
                .setWaybillNo(waybill.getWaybillNo()).setStatus(waybill.getStatus())
                .setDeliveryStatus(waybill.getDeliveryStatus()).setPrintStatus(task == null ? null : task.getStatus())
                .setJobId(task == null ? null : task.getJobId()).setDeviceId(task == null ? null : task.getDeviceId())
                .setErrorCode(waybill.getErrorCode()).setErrorMessage(waybill.getErrorMessage())
                .setCreateTime(waybill.getCreateTime());
    }

    private TradeOrderSubDO validateSubOrder(TradeOrderDO order, Long subOrderId) {
        if (subOrderId == null) {
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        }
        TradeOrderSubDO subOrder = orderSubMapper.selectById(subOrderId);
        if (subOrder == null || !order.getId().equals(subOrder.getOrderId())
                || !TradeOrderStatusEnum.isUndelivered(subOrder.getStatus())
                || !TradeOrderRefundStatusEnum.NONE.getStatus().equals(subOrder.getRefundStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        }
        if (subOrder.getMerchantId() == null) {
            throw exception(TRADE_MERCHANT_LOGISTICS_CONFIG_NOT_EXISTS, subOrderId);
        }
        return subOrder;
    }

    private LogisticsPrintTaskRespVO toTaskResp(TradeLogisticsPrintTaskDO task) {
        return new LogisticsPrintTaskRespVO().setId(task.getId()).setRequestId(task.getRequestId())
                .setJobId(task.getJobId()).setOrderId(task.getOrderId()).setWaybillId(task.getWaybillId())
                .setDeviceId(task.getDeviceId()).setStatus(task.getStatus()).setFormat(task.getFormat())
                .setPaperWidthMm(task.getPaperWidthMm()).setPaperHeightMm(task.getPaperHeightMm())
                .setDpi(task.getDpi()).setCopies(task.getCopies()).setLeaseExpireTime(task.getLeaseExpireTime())
                .setLastError(task.getLastError()).setCreateTime(task.getCreateTime());
    }

    private LogisticsTraceRespVO toTraceResp(TradeLogisticsTraceDO trace) {
        return new LogisticsTraceRespVO().setId(trace.getId()).setStatus(trace.getStatus())
                .setContent(trace.getContent()).setLocation(trace.getLocation()).setOperateTime(trace.getOperateTime());
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) return null;
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception ignored) { return null; }
    }

    private List<JsonNode> extractRoutes(JsonNode response) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode routeResponses = response.path("routeResps");
        if (routeResponses.isArray()) {
            for (JsonNode routeResponse : routeResponses) {
                JsonNode routes = routeResponse.path("routes");
                if (routes.isArray()) {
                    routes.forEach(result::add);
                }
            }
            return result;
        }
        JsonNode routes = response.path("routes");
        if (routes.isArray()) {
            routes.forEach(result::add);
        }
        return result;
    }

    private static void copyRemoteState(TradeLogisticsWaybillDO source, TradeLogisticsWaybillDO target) {
        target.setWaybillNo(source.getWaybillNo()).setStatus(source.getStatus())
                .setProviderResponse(source.getProviderResponse()).setLastSyncTime(source.getLastSyncTime())
                .setErrorCode(source.getErrorCode()).setErrorMessage(source.getErrorMessage());
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        if (transactionManager == null) { // 仅供无 Spring 容器的单元测试使用
            return supplier.get();
        }
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    private record CreationPreparation(TradeLogisticsAccountDO account,
                                       TradeLogisticsPrintDeviceDO device, TradeLogisticsWaybillDO waybill,
                                       TradeLogisticsPrintTaskDO task, boolean newRecord, boolean taskReused) {
    }

    private record CancellationPreparation(TradeLogisticsWaybillDO waybill, TradeLogisticsPrintTaskDO task,
                                           TradeLogisticsAccountDO account) {
    }
}
