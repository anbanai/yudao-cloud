package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppReturnShipmentCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnTraceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleReturnShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleReturnTraceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleWayEnum;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentFeePayerEnum;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.framework.logistics.returnshipment.ReturnShipmentProvider;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class AfterSaleReturnShipmentServiceImpl implements AfterSaleReturnShipmentService {

    @Value("${yudao.trade.logistics.sf.return-shipment-estimated-fee-per-kg:0}")
    private Integer estimatedFeePerKg;

    @Resource private AfterSaleMapper afterSaleMapper;
    @Resource private AfterSaleReturnShipmentMapper shipmentMapper;
    @Resource private AfterSaleReturnTraceMapper traceMapper;
    @Resource private TradeLogisticsAccountMapper accountMapper;
    @Resource private DeliveryExpressService deliveryExpressService;
    @Resource private MemberAddressApi memberAddressApi;
    @Resource private TradeOrderQueryService tradeOrderQueryService;
    @Resource private ReturnShipmentProvider returnShipmentProvider;
    @Resource private ReturnShipmentFeePolicy returnShipmentFeePolicy;
    @Resource private PlatformTransactionManager transactionManager;

    @Override
    public AfterSaleReturnShipmentDO preview(Long userId, Long afterSaleId) {
        AfterSaleDO afterSale = requireEligibleAfterSale(userId, afterSaleId);
        AfterSaleReturnShipmentDO existing = shipmentMapper.selectByAfterSaleId(afterSaleId);
        if (existing != null) return existing;
        TradeLogisticsAccountDO account = accountMapper.selectDefaultEnabled();
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        MemberAddressRespDTO address = memberAddressApi.getDefaultAddress(userId).getCheckedData();
        String pickupName = address == null ? null : address.getName();
        String pickupMobile = address == null ? null : address.getMobile();
        Integer pickupAreaId = address == null ? null : address.getAreaId();
        String pickupAddress = address == null ? null : address.getDetailAddress();
        TradeOrderDO order = tradeOrderQueryService.getOrder(afterSale.getOrderId());
        // Only expose the order snapshot when it matches the selected address-book entry;
        // otherwise preview and create would refer to different pickup addresses.
        if (order != null && (address == null || (StrUtil.equals(address.getName(), order.getReceiverName())
                && StrUtil.equals(address.getMobile(), order.getReceiverMobile())
                && ObjectUtil.equal(address.getAreaId(), order.getReceiverAreaId())
                && StrUtil.equals(address.getDetailAddress(), order.getReceiverDetailAddress())))) {
            pickupName = order.getReceiverName();
            pickupMobile = order.getReceiverMobile();
            pickupAreaId = order.getReceiverAreaId();
            pickupAddress = order.getReceiverDetailAddress();
        }
        ReceiverArea pickupArea = resolveArea(pickupAreaId);
        return new AfterSaleReturnShipmentDO().setAfterSaleId(afterSaleId).setProvider("SF")
                .setStatus(ReturnShipmentStatusEnum.CREATING.name())
                .setAddressId(address == null ? null : address.getId())
                .setPickupName(pickupName)
                .setPickupMobile(pickupMobile)
                .setPickupAreaId(pickupAreaId)
                .setPickupAddress(pickupAddress)
                .setPickupProvince(pickupArea.province()).setPickupCity(pickupArea.city())
                .setPickupDistrict(pickupArea.district())
                .setPackageWeight(account.getDefaultWeightKg()).setEstimatedFee(estimateFee(account.getDefaultWeightKg()))
                .setFeePayer(defaultFeePayer(afterSale).name())
                .setWarehouseName(account.getSenderName()).setWarehousePhone(account.getSenderPhone())
                .setWarehouseProvince(account.getSenderProvince()).setWarehouseCity(account.getSenderCity())
                .setWarehouseDistrict(account.getSenderDistrict()).setWarehouseAddress(account.getSenderAddress());
    }

    @Override
    public AfterSaleReturnShipmentDO create(Long userId, Long afterSaleId, AppReturnShipmentCreateReqVO request) {
        AfterSaleDO afterSale = requireEligibleAfterSale(userId, afterSaleId);
        String idempotencyKey = request.effectiveIdempotencyKey();
        if (StrUtil.isBlank(idempotencyKey)) throw exception(RETURN_SHIPMENT_APPOINTMENT_INVALID);
        if (!returnShipmentProvider.isAvailable()) {
            throw exception(RETURN_SHIPMENT_PROVIDER_UNAVAILABLE);
        }
        AfterSaleReturnShipmentDO existing = shipmentMapper.selectByAfterSaleId(afterSaleId);
        if (existing != null) {
            if (StrUtil.equals(existing.getIdempotencyKey(), idempotencyKey)) return existing;
            if (!ReturnShipmentStateMachine.canUseManualFallback(existing.statusEnum())) {
                throw exception(RETURN_SHIPMENT_ALREADY_EXISTS);
            }
        }
        MemberAddressRespDTO address = memberAddressApi.getAddress(request.getAddressId(), userId).getCheckedData();
        if (address == null || !ObjectUtil.equal(address.getUserId(), userId)) {
            throw exception(RETURN_SHIPMENT_ADDRESS_INVALID);
        }
        String pickupMobile = StrUtil.blankToDefault(request.getContactMobile(), address.getMobile());
        String pickupName = StrUtil.blankToDefault(request.getContactName(), address.getName());
        if (StrUtil.isBlank(pickupName) || StrUtil.isBlank(address.getDetailAddress())
                || StrUtil.isBlank(pickupMobile) || !pickupMobile.matches("^1\\d{10}$")) {
            throw exception(RETURN_SHIPMENT_ADDRESS_INVALID);
        }
        TradeLogisticsAccountDO account = accountMapper.selectDefaultEnabled();
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        if (request.getPackageWeight().compareTo(new java.math.BigDecimal("50")) > 0) {
            throw exception(RETURN_SHIPMENT_WEIGHT_INVALID);
        }
        if (request.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw exception(RETURN_SHIPMENT_APPOINTMENT_INVALID);
        }
        ReceiverArea pickupArea = resolveArea(address.getAreaId());
        String providerOrderNo = "YD-RET-" + TenantContextHolder.getTenantId() + "-" + afterSaleId
                + "-" + IdUtil.fastSimpleUUID().substring(0, 12);
        AfterSaleReturnShipmentDO shipment = new AfterSaleReturnShipmentDO().setAfterSaleId(afterSaleId)
                .setAccountId(account.getId()).setProvider("SF").setProviderOrderNo(providerOrderNo)
                .setStatus(ReturnShipmentStatusEnum.CREATING.name()).setIdempotencyKey(idempotencyKey)
                .setAddressId(address.getId()).setPickupName(pickupName)
                .setPickupMobile(pickupMobile)
                .setPickupAreaId(address.getAreaId()).setPickupProvince(pickupArea.province())
                .setPickupCity(pickupArea.city()).setPickupDistrict(pickupArea.district())
                .setPickupAddress(address.getDetailAddress())
                .setWarehouseName(account.getSenderName()).setWarehousePhone(account.getSenderPhone())
                .setWarehouseProvince(account.getSenderProvince()).setWarehouseCity(account.getSenderCity())
                .setWarehouseDistrict(account.getSenderDistrict()).setWarehouseAddress(account.getSenderAddress())
                .setAppointmentTime(request.getAppointmentTime()).setPackageWeight(request.getPackageWeight())
                .setFeePayer(defaultFeePayer(afterSale).name());
        if (existing != null) {
            final AfterSaleReturnShipmentDO requestedShipment = shipment;
            AfterSaleReturnShipmentDO reserved = new TransactionTemplate(transactionManager).execute(status -> {
                AfterSaleReturnShipmentDO current = shipmentMapper.selectByIdForUpdate(existing.getId());
                if (current == null || !ReturnShipmentStateMachine.canUseManualFallback(current.statusEnum())) {
                    throw exception(RETURN_SHIPMENT_ALREADY_EXISTS);
                }
                shipmentMapper.update(null, new LambdaUpdateWrapper<AfterSaleReturnShipmentDO>()
                        .eq(AfterSaleReturnShipmentDO::getId, current.getId())
                        .set(AfterSaleReturnShipmentDO::getAccountId, requestedShipment.getAccountId())
                        .set(AfterSaleReturnShipmentDO::getProvider, requestedShipment.getProvider())
                        .set(AfterSaleReturnShipmentDO::getProviderOrderNo, requestedShipment.getProviderOrderNo())
                        .set(AfterSaleReturnShipmentDO::getWaybillNo, null)
                        .set(AfterSaleReturnShipmentDO::getStatus, requestedShipment.getStatus())
                        .set(AfterSaleReturnShipmentDO::getIdempotencyKey, requestedShipment.getIdempotencyKey())
                        .set(AfterSaleReturnShipmentDO::getAddressId, requestedShipment.getAddressId())
                        .set(AfterSaleReturnShipmentDO::getPickupName, requestedShipment.getPickupName())
                        .set(AfterSaleReturnShipmentDO::getPickupMobile, requestedShipment.getPickupMobile())
                        .set(AfterSaleReturnShipmentDO::getPickupAreaId, requestedShipment.getPickupAreaId())
                        .set(AfterSaleReturnShipmentDO::getPickupProvince, requestedShipment.getPickupProvince())
                        .set(AfterSaleReturnShipmentDO::getPickupCity, requestedShipment.getPickupCity())
                        .set(AfterSaleReturnShipmentDO::getPickupDistrict, requestedShipment.getPickupDistrict())
                        .set(AfterSaleReturnShipmentDO::getPickupAddress, requestedShipment.getPickupAddress())
                        .set(AfterSaleReturnShipmentDO::getWarehouseName, requestedShipment.getWarehouseName())
                        .set(AfterSaleReturnShipmentDO::getWarehousePhone, requestedShipment.getWarehousePhone())
                        .set(AfterSaleReturnShipmentDO::getWarehouseProvince, requestedShipment.getWarehouseProvince())
                        .set(AfterSaleReturnShipmentDO::getWarehouseCity, requestedShipment.getWarehouseCity())
                        .set(AfterSaleReturnShipmentDO::getWarehouseDistrict, requestedShipment.getWarehouseDistrict())
                        .set(AfterSaleReturnShipmentDO::getWarehouseAddress, requestedShipment.getWarehouseAddress())
                        .set(AfterSaleReturnShipmentDO::getAppointmentTime, requestedShipment.getAppointmentTime())
                        .set(AfterSaleReturnShipmentDO::getPackageWeight, requestedShipment.getPackageWeight())
                        .set(AfterSaleReturnShipmentDO::getFeePayer, requestedShipment.getFeePayer())
                        .set(AfterSaleReturnShipmentDO::getEstimatedFee, requestedShipment.getEstimatedFee())
                        .set(AfterSaleReturnShipmentDO::getActualFee, null)
                        .set(AfterSaleReturnShipmentDO::getRefundDeductedFee, null)
                        .set(AfterSaleReturnShipmentDO::getErrorCode, null)
                        .set(AfterSaleReturnShipmentDO::getErrorMessage, null)
                        .set(AfterSaleReturnShipmentDO::getProviderResponse, null)
                        .set(AfterSaleReturnShipmentDO::getLastSyncTime, null)
                        .set(AfterSaleReturnShipmentDO::getCancelledTime, null)
                        .set(AfterSaleReturnShipmentDO::getDeliveredTime, null));
                requestedShipment.setId(current.getId());
                return requestedShipment;
            });
            shipment = reserved;
        } else {
            try {
                shipmentMapper.insert(shipment);
            } catch (DuplicateKeyException exception) {
                AfterSaleReturnShipmentDO latest = shipmentMapper.selectByAfterSaleId(afterSaleId);
                if (latest != null && StrUtil.equals(latest.getIdempotencyKey(), idempotencyKey)) return latest;
                throw exception(RETURN_SHIPMENT_ALREADY_EXISTS);
            }
        }
        try {
            ReturnShipmentProvider.Result result = returnShipmentProvider.create(shipment);
            shipment.setProviderOrderNo(result.providerOrderNo()).setWaybillNo(result.waybillNo())
                    .setStatus(result.status()).setProviderResponse(StrUtil.maxLength(result.response(), 20_000))
                    .setEstimatedFee(result.estimatedFee() == null ? shipment.getEstimatedFee() : result.estimatedFee())
                    .setActualFee(result.actualFee()).setLastSyncTime(LocalDateTime.now());
        } catch (SfApiException exception) {
            shipment.setStatus(exception.isUnknownResult() ? ReturnShipmentStatusEnum.UNKNOWN.name()
                    : ReturnShipmentStatusEnum.CREATE_FAILED.name())
                    .setErrorCode(exception.getCode()).setErrorMessage(StrUtil.maxLength(exception.getMessage(), 500));
        } catch (RuntimeException exception) {
            shipment.setStatus(ReturnShipmentStatusEnum.UNKNOWN.name()).setErrorCode("PROVIDER_UNKNOWN")
                    .setErrorMessage("服务商响应未知，请等待系统查询或使用人工寄回");
        }
        shipmentMapper.updateById(shipment);
        return shipment;
    }

    @Override
    public AfterSaleReturnShipmentDO get(Long userId, Long afterSaleId) {
        requireAfterSaleOwner(userId, afterSaleId);
        return shipmentMapper.selectByAfterSaleId(afterSaleId);
    }

    @Override
    public AfterSaleReturnShipmentDO getAdminShipment(Long afterSaleId) {
        return shipmentMapper.selectByAfterSaleId(afterSaleId);
    }

    @Override
    public List<AfterSaleReturnTraceDO> getTraces(Long userId, Long afterSaleId) {
        requireAfterSaleOwner(userId, afterSaleId);
        syncProviderTraces(afterSaleId);
        return getAdminTraces(afterSaleId);
    }

    @Override
    public List<AfterSaleReturnTraceDO> getAdminTraces(Long afterSaleId) {
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByAfterSaleId(afterSaleId);
        return shipment == null ? List.of() : traceMapper.selectListByShipmentId(shipment.getId());
    }

    @Override
    public void cancel(Long userId, Long afterSaleId) {
        requireAfterSaleOwner(userId, afterSaleId);
        if (!returnShipmentProvider.isAvailable()) {
            throw exception(RETURN_SHIPMENT_PROVIDER_UNAVAILABLE);
        }
        AfterSaleReturnShipmentDO shipment = markCancelRequested(afterSaleId);
        try {
            returnShipmentProvider.cancel(shipment);
            shipment.setStatus(ReturnShipmentStatusEnum.CANCELLED.name()).setCancelledTime(LocalDateTime.now());
        } catch (RuntimeException exception) {
            shipment.setStatus(ReturnShipmentStatusEnum.UNKNOWN.name()).setErrorCode("CANCEL_UNKNOWN")
                    .setErrorMessage(StrUtil.maxLength(exception.getMessage(), 500));
        }
        persistAfterProviderCall(shipment);
    }

    @Override
    public void cancelAdmin(Long afterSaleId) {
        if (!returnShipmentProvider.isAvailable()) {
            throw exception(RETURN_SHIPMENT_PROVIDER_UNAVAILABLE);
        }
        AfterSaleReturnShipmentDO shipment = markCancelRequested(afterSaleId);
        try {
            returnShipmentProvider.cancel(shipment);
            shipment.setStatus(ReturnShipmentStatusEnum.CANCELLED.name()).setCancelledTime(LocalDateTime.now());
        } catch (RuntimeException ex) {
            shipment.setStatus(ReturnShipmentStatusEnum.UNKNOWN.name()).setErrorCode("CANCEL_UNKNOWN")
                    .setErrorMessage(StrUtil.maxLength(ex.getMessage(), 500));
        }
        persistAfterProviderCall(shipment);
    }

    @Override
    public AfterSaleReturnShipmentDO retry(Long afterSaleId) {
        if (!returnShipmentProvider.isAvailable()) {
            throw exception(RETURN_SHIPMENT_PROVIDER_UNAVAILABLE);
        }
        AfterSaleDO afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null || !ObjectUtil.equal(afterSale.getStatus(), AfterSaleStatusEnum.SELLER_AGREE.getStatus())
                || !ObjectUtil.equal(afterSale.getWay(), AfterSaleWayEnum.RETURN_AND_REFUND.getWay())) {
            throw exception(AFTER_SALE_DELIVERY_FAIL_STATUS_NOT_SELLER_AGREE);
        }
        AfterSaleReturnShipmentDO shipment = markRetryCreating(afterSaleId);
        try {
            ReturnShipmentProvider.Result result = returnShipmentProvider.create(shipment);
            shipment.setProviderOrderNo(result.providerOrderNo()).setWaybillNo(result.waybillNo())
                    .setStatus(result.status()).setProviderResponse(StrUtil.maxLength(result.response(), 20_000))
                    .setEstimatedFee(result.estimatedFee() == null ? shipment.getEstimatedFee() : result.estimatedFee())
                    .setActualFee(result.actualFee()).setLastSyncTime(LocalDateTime.now());
        } catch (SfApiException ex) {
            shipment.setStatus(ex.isUnknownResult() ? ReturnShipmentStatusEnum.UNKNOWN.name()
                    : ReturnShipmentStatusEnum.CREATE_FAILED.name()).setErrorCode(ex.getCode())
                    .setErrorMessage(StrUtil.maxLength(ex.getMessage(), 500));
        } catch (RuntimeException ex) {
            shipment.setStatus(ReturnShipmentStatusEnum.UNKNOWN.name()).setErrorCode("PROVIDER_UNKNOWN")
                    .setErrorMessage("服务商响应未知，请等待系统查询或使用人工寄回");
        }
        persistAfterProviderCall(shipment);
        return shipment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverUnknown(Long returnShipmentId) {
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByIdForUpdate(returnShipmentId);
        if (shipment == null || !ReturnShipmentStatusEnum.UNKNOWN.name().equals(shipment.getStatus())
                || !returnShipmentProvider.isAvailable()) {
            return;
        }
        try {
            ReturnShipmentProvider.Result result = returnShipmentProvider.query(shipment);
            if (result == null || StrUtil.isBlank(result.status())) {
                return;
            }
            shipment.setProviderOrderNo(StrUtil.blankToDefault(result.providerOrderNo(), shipment.getProviderOrderNo()))
                    .setWaybillNo(StrUtil.blankToDefault(result.waybillNo(), shipment.getWaybillNo()))
                    .setStatus(ReturnShipmentStatusEnum.UNKNOWN.name())
                    .setProviderResponse(StrUtil.maxLength(result.response(), 20_000))
                    .setEstimatedFee(result.estimatedFee() == null ? shipment.getEstimatedFee() : result.estimatedFee())
                    .setActualFee(result.actualFee())
                    .setLastSyncTime(LocalDateTime.now()).setErrorCode(null).setErrorMessage(null);
            shipmentMapper.updateById(shipment);
            syncStatus(shipment.getId(), result.status(),
                    "recovery-" + shipment.getId() + "-" + System.currentTimeMillis(),
                    "服务商状态恢复", null);
        } catch (SfApiException exception) {
            log.warn("[recoverUnknown][query return shipment {} failed: {}]", returnShipmentId,
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("[recoverUnknown][query return shipment {} failed]", returnShipmentId, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualDelivery(Long afterSaleId, Long logisticsId, String logisticsNo) {
        AfterSaleDO afterSale = afterSaleMapper.selectByIdForUpdate(afterSaleId);
        if (afterSale == null) throw exception(AFTER_SALE_NOT_FOUND);
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.SELLER_AGREE.getStatus())) {
            throw exception(AFTER_SALE_DELIVERY_FAIL_STATUS_NOT_SELLER_AGREE);
        }
        if (!ObjectUtil.equal(afterSale.getWay(), AfterSaleWayEnum.RETURN_AND_REFUND.getWay())) {
            throw exception(AFTER_SALE_DELIVERY_FAIL_STATUS_NOT_SELLER_AGREE);
        }
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByAfterSaleId(afterSaleId);
        if (shipment != null) shipment = shipmentMapper.selectByIdForUpdate(shipment.getId());
        if (!ReturnShipmentStateMachine.canUseManualFallback(shipment == null ? null : shipment.statusEnum())) {
            throw exception(RETURN_SHIPMENT_ALREADY_EXISTS);
        }
        deliveryExpressService.validateDeliveryExpress(logisticsId);
        afterSaleMapper.updateByIdAndStatus(afterSaleId, AfterSaleStatusEnum.SELLER_AGREE.getStatus(),
                new AfterSaleDO().setStatus(AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())
                        .setLogisticsId(logisticsId).setLogisticsNo(logisticsNo).setDeliveryTime(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefundDeduction(AfterSaleDO afterSale) {
        AfterSaleReturnShipmentDO current = shipmentMapper.selectByAfterSaleId(afterSale.getId());
        if (current == null || !ReturnShipmentFeePayerEnum.BUYER_DEDUCT_REFUND.name().equals(current.getFeePayer())) {
            return;
        }
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByIdForUpdate(current.getId());
        if (shipment == null || shipment.getRefundDeductedFee() != null) {
            return;
        }
        if (shipment.getActualFee() == null) {
            if (!returnShipmentProvider.isAvailable()) {
                throw exception(RETURN_SHIPMENT_REFUND_FEE_NOT_READY);
            }
            try {
                ReturnShipmentProvider.Result result = returnShipmentProvider.query(shipment);
                if (result != null) {
                    if (result.actualFee() != null) shipment.setActualFee(result.actualFee());
                    if (result.estimatedFee() != null) shipment.setEstimatedFee(result.estimatedFee());
                    if (StrUtil.isNotBlank(result.response())) {
                        shipment.setProviderResponse(StrUtil.maxLength(result.response(), 20_000));
                    }
                    shipment.setLastSyncTime(LocalDateTime.now());
                    shipmentMapper.updateById(shipment);
                }
            } catch (RuntimeException exception) {
                log.warn("[applyRefundDeduction][query actual fee for return shipment {} failed]",
                        shipment.getId(), exception);
            }
            if (shipment.getActualFee() == null) {
                throw exception(RETURN_SHIPMENT_REFUND_FEE_NOT_READY);
            }
        }
        int refundPrice = afterSale.getRefundPrice() == null ? 0 : afterSale.getRefundPrice();
        if (shipment.getActualFee() > refundPrice) {
            throw exception(RETURN_SHIPMENT_REFUND_FEE_EXCEEDS_REFUND);
        }
        shipment.setRefundDeductedFee(shipment.getActualFee());
        shipmentMapper.updateById(shipment);
        afterSale.setRefundPrice(refundPrice - shipment.getActualFee());
        afterSaleMapper.updateById(new AfterSaleDO().setId(afterSale.getId())
                .setRefundPrice(afterSale.getRefundPrice()));
    }

    @Override
    public void syncStatus(Long returnShipmentId, String status, String eventId, String description,
                           String location) {
        syncStatus(returnShipmentId, status, eventId, description, location, null);
    }

    @Override
    public void syncStatus(Long returnShipmentId, String status, String eventId, String description,
                           String location, LocalDateTime occurredTime) {
        if (StrUtil.isBlank(eventId) || StrUtil.isBlank(status)) {
            log.warn("[syncStatus][missing event id or status for shipment {}]", returnShipmentId);
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByIdForUpdate(returnShipmentId);
            if (shipment == null || traceMapper.existsByEventId(returnShipmentId, eventId)) return;
            ReturnShipmentStatusEnum next;
            try {
                next = ReturnShipmentStatusEnum.valueOf(status);
            } catch (IllegalArgumentException | NullPointerException ex) {
                log.warn("[syncStatus][unknown status {} for shipment {}]", status, returnShipmentId);
                return;
            }
            ReturnShipmentStatusEnum current = shipment.statusEnum();
            if (next == null || !ReturnShipmentStateMachine.canTransition(current, next)) return;
            AfterSaleReturnTraceDO trace = new AfterSaleReturnTraceDO().setReturnShipmentId(returnShipmentId)
                    .setProvider(shipment.getProvider()).setStatus(status).setEventId(eventId)
                    .setDescription(description).setLocation(location)
                    .setOccurredTime(occurredTime == null ? LocalDateTime.now() : occurredTime);
            traceMapper.insert(trace);
            shipment.setStatus(status).setLastSyncTime(LocalDateTime.now());
            if (next == ReturnShipmentStatusEnum.DELIVERED) shipment.setDeliveredTime(LocalDateTime.now());
            shipmentMapper.updateById(shipment);
            if (next == ReturnShipmentStatusEnum.PICKED_UP || next == ReturnShipmentStatusEnum.IN_TRANSIT
                    || next == ReturnShipmentStatusEnum.DELIVERED) {
                AfterSaleDO afterSale = afterSaleMapper.selectById(shipment.getAfterSaleId());
                if (afterSale != null && ObjectUtil.equal(afterSale.getStatus(), AfterSaleStatusEnum.SELLER_AGREE.getStatus())) {
                    afterSaleMapper.updateByIdAndStatus(afterSale.getId(), AfterSaleStatusEnum.SELLER_AGREE.getStatus(),
                            new AfterSaleDO().setStatus(AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())
                                    .setDeliveryTime(LocalDateTime.now()));
                }
            }
        });
    }

    @Override
    public void syncStatusByProviderRefs(String providerOrderNo, String waybillNo, String status, String eventId,
                                         String description, String location, Long tenantId) {
        String normalizedStatus = normalizeProviderStatus(status);
        if (StrUtil.isBlank(normalizedStatus)) return;
        // Resolve the tenant from the persisted shipment first. The callback body is
        // supplied by an external provider and must not be allowed to select an
        // arbitrary tenant before the shipment has been identified.
        Long resolvedTenantId = tenantId != null ? tenantId : parseTenantId(providerOrderNo);
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByProviderRefs(providerOrderNo, waybillNo,
                resolvedTenantId);
        if (shipment == null) return;
        if (tenantId != null && ObjectUtil.notEqual(shipment.getTenantId(), tenantId)) {
            log.warn("[syncStatusByProviderRefs][tenant mismatch for shipment {}, callbackTenant={}]",
                    shipment.getId(), tenantId);
            return;
        }
        TenantUtils.execute(shipment.getTenantId(),
                () -> syncStatus(shipment.getId(), normalizedStatus, eventId, description, location));
    }

    /** Maps common SF route opCodes to the internal forward-only state machine. */
    private static String normalizeProviderStatus(String status) {
        if (StrUtil.isBlank(status)) return null;
        String value = status.trim().toUpperCase();
        try {
            return ReturnShipmentStatusEnum.valueOf(value).name();
        } catch (IllegalArgumentException ignored) {
            // Continue with SF's route code/name aliases.
        }
        return switch (value) {
            case "10", "50", "已收件", "收件", "揽收" -> ReturnShipmentStatusEnum.PICKED_UP.name();
            case "2", "3", "在途中", "运输中", "派送中" -> ReturnShipmentStatusEnum.IN_TRANSIT.name();
            case "4", "已签收", "签收" -> ReturnShipmentStatusEnum.DELIVERED.name();
            case "5", "问题件", "异常" -> ReturnShipmentStatusEnum.EXCEPTION.name();
            default -> null;
        };
    }

    /**
     * Our provider order id embeds the tenant as a routing hint for callbacks
     * that do not echo tenant metadata. It is only used to narrow the lookup;
     * the returned row is still checked by the normal tenant-aware mapper.
     */
    private static Long parseTenantId(String providerOrderNo) {
        if (StrUtil.isBlank(providerOrderNo)) return null;
        String[] parts = providerOrderNo.split("-");
        if (parts.length < 4 || !"YD".equals(parts[0]) || !"RET".equals(parts[1])) return null;
        try {
            long tenantId = Long.parseLong(parts[2]);
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private AfterSaleDO requireEligibleAfterSale(Long userId, Long afterSaleId) {
        AfterSaleDO afterSale = requireAfterSaleOwner(userId, afterSaleId);
        if (!ObjectUtil.equal(afterSale.getStatus(), AfterSaleStatusEnum.SELLER_AGREE.getStatus())
                || !ObjectUtil.equal(afterSale.getWay(), AfterSaleWayEnum.RETURN_AND_REFUND.getWay())) {
            throw exception(AFTER_SALE_DELIVERY_FAIL_STATUS_NOT_SELLER_AGREE);
        }
        return afterSale;
    }

    private AfterSaleDO requireAfterSaleOwner(Long userId, Long afterSaleId) {
        AfterSaleDO afterSale = afterSaleMapper.selectByIdAndUserId(afterSaleId, userId);
        if (afterSale == null) throw exception(AFTER_SALE_NOT_FOUND);
        return afterSale;
    }

    private ReturnShipmentFeePayerEnum defaultFeePayer(AfterSaleDO afterSale) {
        return returnShipmentFeePolicy.resolve(afterSale);
    }

    private Integer estimateFee(java.math.BigDecimal weight) {
        if (weight == null || estimatedFeePerKg == null || estimatedFeePerKg <= 0) return null;
        return weight.multiply(java.math.BigDecimal.valueOf(estimatedFeePerKg)).setScale(0, java.math.RoundingMode.UP).intValue();
    }

    private void syncProviderTraces(Long afterSaleId) {
        AfterSaleReturnShipmentDO shipment = shipmentMapper.selectByAfterSaleId(afterSaleId);
        if (shipment == null || StrUtil.isBlank(shipment.getWaybillNo()) || !returnShipmentProvider.isAvailable()) return;
        List<AfterSaleReturnTraceDO> current = traceMapper.selectListByShipmentId(shipment.getId());
        if (!current.isEmpty() && shipment.getLastSyncTime() != null
                && shipment.getLastSyncTime().isAfter(LocalDateTime.now().minusMinutes(5))) return;
        try {
            for (ReturnShipmentProvider.Trace trace : returnShipmentProvider.queryTraces(shipment)) {
                syncStatus(shipment.getId(), trace.status(), trace.eventId(), trace.description(), trace.location(),
                        trace.occurredTime());
            }
        } catch (RuntimeException ex) {
            log.warn("[syncProviderTraces][return shipment {} query failed]", shipment.getId(), ex);
        }
    }

    private AfterSaleReturnShipmentDO markCancelRequested(Long afterSaleId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            AfterSaleReturnShipmentDO current = shipmentMapper.selectByAfterSaleId(afterSaleId);
            AfterSaleReturnShipmentDO shipment = current == null ? null : shipmentMapper.selectByIdForUpdate(current.getId());
            if (shipment == null || !ReturnShipmentStateMachine.canCancel(shipment.statusEnum())) {
                throw exception(RETURN_SHIPMENT_CANCEL_NOT_ALLOWED);
            }
            shipment.setStatus(ReturnShipmentStatusEnum.CANCEL_REQUESTED.name());
            shipmentMapper.updateById(shipment);
            return shipment;
        });
    }

    private AfterSaleReturnShipmentDO markRetryCreating(Long afterSaleId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            AfterSaleReturnShipmentDO current = shipmentMapper.selectByAfterSaleId(afterSaleId);
            AfterSaleReturnShipmentDO shipment = current == null ? null : shipmentMapper.selectByIdForUpdate(current.getId());
            if (shipment == null || !ReturnShipmentStatusEnum.CREATE_FAILED.name().equals(shipment.getStatus())) {
                throw exception(RETURN_SHIPMENT_ALREADY_EXISTS);
            }
            // A retry must use a fresh provider order number. Reusing the failed
            // provider reference can be interpreted as a duplicate create request
            // by the carrier and makes the result impossible to reconcile safely.
            String providerOrderNo = "YD-RET-" + TenantContextHolder.getTenantId() + "-" + afterSaleId
                    + "-" + IdUtil.fastSimpleUUID().substring(0, 12);
            shipment.setProviderOrderNo(providerOrderNo).setWaybillNo(null)
                    .setStatus(ReturnShipmentStatusEnum.CREATING.name()).setErrorCode(null).setErrorMessage(null)
                    .setProviderResponse(null).setActualFee(null).setRefundDeductedFee(null)
                    .setLastSyncTime(null).setCancelledTime(null).setDeliveredTime(null);
            shipmentMapper.updateById(shipment);
            return shipment;
        });
    }

    private void persistAfterProviderCall(AfterSaleReturnShipmentDO shipment) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> shipmentMapper.updateById(shipment));
    }

    private static ReceiverArea resolveArea(Integer areaId) {
        Area area = areaId == null ? null : AreaUtils.getArea(areaId);
        String province = null;
        String city = null;
        String district = null;
        while (area != null) {
            if (AreaTypeEnum.PROVINCE.getType().equals(area.getType())) province = area.getName();
            else if (AreaTypeEnum.CITY.getType().equals(area.getType())) city = area.getName();
            else if (AreaTypeEnum.DISTRICT.getType().equals(area.getType())) district = area.getName();
            area = area.getParent();
        }
        return new ReceiverArea(province, city, district);
    }

    private record ReceiverArea(String province, String city, String district) {
    }
}
