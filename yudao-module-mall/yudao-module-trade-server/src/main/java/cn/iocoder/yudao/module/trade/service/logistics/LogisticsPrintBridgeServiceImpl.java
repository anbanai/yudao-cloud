package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeStatusReqVO;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeTaskRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
public class LogisticsPrintBridgeServiceImpl implements LogisticsPrintBridgeService {

    private static final int LABEL_URL_EXPIRATION_SECONDS = 15 * 60;

    @Resource
    private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Resource
    private TradeLogisticsPrintTaskMapper taskMapper;
    @Resource
    private TradeLogisticsPrintEventMapper eventMapper;
    @Resource
    private TradeLogisticsWaybillMapper waybillMapper;
    @Resource
    private TradeOrderUpdateService orderUpdateService;
    @Resource
    private FileApi fileApi;
    @Resource
    private TradeOrderMapper orderMapper;

    @Override
    public TradeLogisticsPrintDeviceDO authenticate(String token, String deviceCode) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(deviceCode)) {
            throw exception(LOGISTICS_DEVICE_AUTH_FAILED);
        }
        TradeLogisticsPrintDeviceDO device = deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash(token));
        if (device == null || device.getStatus() == null || device.getStatus() != 0
                || !StrUtil.equals(device.getDeviceCode(), deviceCode)) {
            throw exception(LOGISTICS_DEVICE_AUTH_FAILED);
        }
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrintBridgeTaskRespVO pull(TradeLogisticsPrintDeviceDO device, String deviceName) {
        return TenantUtils.execute(device.getTenantId(), () -> pullInTenant(device, deviceName));
    }

    private PrintBridgeTaskRespVO pullInTenant(TradeLogisticsPrintDeviceDO device, String deviceName) {
        LocalDateTime now = LocalDateTime.now();
        deviceMapper.updateById(new TradeLogisticsPrintDeviceDO().setId(device.getId())
                .setDeviceName(StrUtil.sub(deviceName, 0, 128)).setLastPollTime(now));
        TradeLogisticsPrintTaskDO task = taskMapper.selectClaimable(device.getId(), now);
        if (task == null) {
            return null;
        }
        LogisticsPrintTaskStatusEnum current = LogisticsPrintTaskStatusEnum.valueOf(task.getStatus());
        if (current == LogisticsPrintTaskStatusEnum.PENDING
                || (current == LogisticsPrintTaskStatusEnum.DISPATCHED
                && task.getLeaseExpireTime() != null && task.getLeaseExpireTime().isBefore(now))) {
            task.setStatus(LogisticsPrintTaskStatusEnum.DISPATCHED.name())
                    .setDispatchedTime(ObjUtil.defaultIfNull(task.getDispatchedTime(), now))
                    .setLeaseExpireTime(now.plusSeconds(60));
            taskMapper.updateById(task);
        }
        String fileUrl = fileApi.presignGetUrl(task.getLabelUrl(), LABEL_URL_EXPIRATION_SECONDS).getCheckedData();
        return new PrintBridgeTaskRespVO().setType("print").setRequestId(task.getRequestId())
                .setJobId(task.getJobId()).setFormat(task.getFormat()).setFileUrl(fileUrl)
                .setCopies(task.getCopies()).setPaper(new PrintBridgeTaskRespVO.Paper()
                        .setWidthMm(task.getPaperWidthMm()).setHeightMm(task.getPaperHeightMm()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void report(TradeLogisticsPrintDeviceDO device, PrintBridgeStatusReqVO request) {
        TenantUtils.execute(device.getTenantId(), () -> reportInTenant(device, request));
    }

    private void reportInTenant(TradeLogisticsPrintDeviceDO device, PrintBridgeStatusReqVO request) {
        if (StrUtil.isBlank(request.getJobId())) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        if (eventMapper.selectByEventId(request.getEventId()) != null) {
            return;
        }
        TradeLogisticsPrintTaskDO task = taskMapper.selectByJobIdForUpdate(request.getJobId());
        if (task == null || !device.getId().equals(task.getDeviceId())) {
            throw exception(LOGISTICS_PRINT_TASK_NOT_EXISTS);
        }
        LogisticsPrintTaskStatusEnum next = parseRemoteStatus(request.getStatus());
        LogisticsPrintTaskStatusEnum current = LogisticsPrintTaskStatusEnum.valueOf(task.getStatus());
        if (!LogisticsPrintTaskStateMachine.canTransition(current, next)) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        TradeLogisticsPrintEventDO event = new TradeLogisticsPrintEventDO().setEventId(request.getEventId())
                .setTaskId(task.getId()).setDeviceId(device.getId()).setJobId(task.getJobId())
                .setEventType(request.getEvent()).setStatus(request.getStatus()).setMessage(request.getMessage())
                .setEventTime(request.getOccurredAt() == null ? LocalDateTime.now()
                        : request.getOccurredAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
                .setRawPayload(JsonUtils.toJsonString(request));
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(next.name()).setLastError(next == LogisticsPrintTaskStatusEnum.FAILED ? request.getMessage() : null);
        if (next == LogisticsPrintTaskStatusEnum.ACCEPTED) {
            task.setAcceptedTime(now).setLeaseExpireTime(null);
        } else if (next == LogisticsPrintTaskStatusEnum.SUCCESS || next == LogisticsPrintTaskStatusEnum.FAILED) {
            task.setCompletedTime(now).setLeaseExpireTime(null);
        }
        taskMapper.updateById(task);
        if (next == LogisticsPrintTaskStatusEnum.SUCCESS && !Boolean.TRUE.equals(task.getTestFlag())) {
            deliver(task);
        }
    }

    private void deliver(TradeLogisticsPrintTaskDO task) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(task.getWaybillId());
        if (waybill == null) {
            throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        if (StrUtil.equals(waybill.getDeliveryStatus(), "DELIVERED")) {
            return;
        }
        orderUpdateService.deliveryOrder(new TradeOrderDeliveryReqVO().setId(waybill.getOrderId())
                .setLogisticsId(waybill.getLogisticsId()).setLogisticsNo(waybill.getWaybillNo()));
        waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(waybill.getId())
                .setDeliveryStatus("DELIVERED").setDeliveredTime(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int compensateDeliveredOrders() {
        int count = 0;
        for (TradeLogisticsPrintTaskDO task : taskMapper.selectListByStatus(LogisticsPrintTaskStatusEnum.SUCCESS.name())) {
            TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(task.getWaybillId());
            if (waybill == null || "DELIVERED".equals(waybill.getDeliveryStatus()) || Boolean.TRUE.equals(task.getTestFlag())) {
                continue;
            }
            TradeOrderDO order = orderMapper.selectByIdForUpdate(waybill.getOrderId());
            if (order != null && TradeOrderStatusEnum.isDelivered(order.getStatus())) {
                if (waybill.getWaybillNo().equals(order.getLogisticsNo())) {
                    waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(waybill.getId())
                            .setDeliveryStatus("DELIVERED").setDeliveredTime(order.getDeliveryTime()));
                    count++;
                } else {
                    waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(waybill.getId())
                            .setDeliveryStatus("CONFLICT").setErrorCode("DELIVERY_CONFLICT")
                            .setErrorMessage("订单已使用其他运单号发货，需人工处理"));
                }
                continue;
            }
            deliver(task);
            count++;
        }
        return count;
    }

    private static LogisticsPrintTaskStatusEnum parseRemoteStatus(String status) {
        if (StrUtil.isBlank(status)) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "accepted", "queued", "submitted" -> LogisticsPrintTaskStatusEnum.ACCEPTED;
            case "success" -> LogisticsPrintTaskStatusEnum.SUCCESS;
            case "failed" -> LogisticsPrintTaskStatusEnum.FAILED;
            case "unknown" -> LogisticsPrintTaskStatusEnum.UNKNOWN;
            default -> throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        };
    }
}
