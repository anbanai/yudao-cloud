package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.*;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsTraceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeWechatLogisticsConfigMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeWechatLogisticsTraceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeWechatLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsPrintStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum.UNDELIVERED;

/**
 * 微信物流助手最小业务闭环：创建运单、确认打印后调用现有发货流程。
 */
@Service
@Validated
@Slf4j
public class WechatLogisticsServiceImpl implements WechatLogisticsService {

    @Resource
    private TradeWechatLogisticsConfigMapper configMapper;
    @Resource
    private TradeWechatLogisticsWaybillMapper waybillMapper;
    @Resource
    private TradeWechatLogisticsTraceMapper traceMapper;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private SocialClientApi socialClientApi;
    @Resource
    private PayOrderApi payOrderApi;
    @Resource
    private ProductSkuApi productSkuApi;
    @Resource
    private DeliveryExpressService deliveryExpressService;
    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;

    private final WechatLogisticsOrderAssembler orderAssembler = new WechatLogisticsOrderAssembler();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(WechatLogisticsConfigSaveReqVO reqVO) {
        TradeWechatLogisticsConfigDO config = configMapper.selectByUserType(reqVO.getUserType());
        TradeWechatLogisticsConfigDO update = new TradeWechatLogisticsConfigDO()
                .setUserType(reqVO.getUserType()).setDeliveryId(reqVO.getDeliveryId()).setBizId(reqVO.getBizId())
                .setServiceType(reqVO.getServiceType()).setServiceName(reqVO.getServiceName()).setEnabled(reqVO.getEnabled())
                .setSenderName(reqVO.getSenderName()).setSenderTel(reqVO.getSenderTel()).setSenderMobile(reqVO.getSenderMobile())
                .setSenderCompany(reqVO.getSenderCompany()).setSenderPostCode(reqVO.getSenderPostCode())
                .setSenderCountry(reqVO.getSenderCountry()).setSenderProvince(reqVO.getSenderProvince())
                .setSenderCity(reqVO.getSenderCity()).setSenderArea(reqVO.getSenderArea()).setSenderAddress(reqVO.getSenderAddress())
                .setDefaultWeight(reqVO.getDefaultWeight()).setDefaultSpaceLength(reqVO.getDefaultSpaceLength())
                .setDefaultSpaceWidth(reqVO.getDefaultSpaceWidth()).setDefaultSpaceHeight(reqVO.getDefaultSpaceHeight());
        if (config == null) {
            configMapper.insert(update);
        } else {
            update.setId(config.getId());
            configMapper.updateById(update);
        }
    }

    @Override
    public TradeWechatLogisticsConfigDO getConfig() {
        return configMapper.selectByUserType(UserTypeEnum.MEMBER.getValue());
    }

    @Override
    public WechatLogisticsAccountStatusRespVO getAccountStatus() {
        List<SocialWxaExpressAccountRespDTO> accounts = socialClientApi
                .getWxaExpressAccountList(UserTypeEnum.MEMBER.getValue()).getCheckedData();
        List<SocialWxaExpressDeliveryRespDTO> deliveries = socialClientApi
                .getWxaExpressDeliveryList(UserTypeEnum.MEMBER.getValue()).getCheckedData();
        SocialWxaExpressAccountRespDTO sf = accounts == null ? null : accounts.stream()
                .filter(item -> "SF".equals(item.getDeliveryId()) && Objects.equals(item.getStatusCode(), 0)
                        && ObjectUtil.isNotEmpty(item.getServiceTypes()))
                .findFirst().orElse(null);
        return new WechatLogisticsAccountStatusRespVO().setAvailable(sf != null)
                .setMessage(sf == null ? "尚未绑定可用的顺丰微信物流账号，或账号尚未返回服务类型" : "顺丰微信物流账号可用")
                .setAccounts(accounts).setDeliveries(deliveries);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatLogisticsWaybillRespVO createWaybill(Long orderId) {
        TradeWechatLogisticsWaybillDO existing = waybillMapper.selectByOrderId(orderId);
        if (existing != null) {
            if (WechatLogisticsWaybillStatusEnum.UNKNOWN.name().equals(existing.getStatus())) {
                throw exception(WECHAT_LOGISTICS_WAYBILL_UNKNOWN);
            }
            return convertWaybill(existing);
        }
        TradeOrderDO order = validateOrder(orderId);
        TradeWechatLogisticsConfigDO config = validateConfig();
        PayOrderRespDTO payOrder = validateWxLiteOrder(order);
        String openid = payOrder.getChannelUserId();
        String wechatOrderId = tenantId() + "-" + order.getNo();
        SocialWxaExpressOrderQueryReqDTO query = new SocialWxaExpressOrderQueryReqDTO()
                .setOrderId(wechatOrderId).setOpenid(openid).setDeliveryId(config.getDeliveryId());
        SocialWxaExpressOrderRespDTO remote = null;
        try {
            remote = socialClientApi.getWxaExpressOrder(UserTypeEnum.MEMBER.getValue(), query).getCheckedData();
        } catch (Exception ignored) {
            log.debug("[createWaybill][微信订单尚未存在，继续调用 addOrder：orderId({})]", orderId);
        }
        if (remote != null && ObjectUtil.isNotEmpty(remote.getWaybillId())) {
            return saveCreatedWaybill(order, config, openid, wechatOrderId, remote);
        }
        List<TradeOrderItemDO> items = tradeOrderItemMapper.selectListByOrderId(orderId);
        Map<Long, ProductSkuRespDTO> skuMap = items.isEmpty() ? Collections.emptyMap()
                : productSkuApi.getSkuMap(items.stream().map(TradeOrderItemDO::getSkuId).toList());
        SocialWxaExpressAddOrderReqDTO request = orderAssembler.build(tenantId(), order, items, skuMap, config, openid);
        TradeWechatLogisticsWaybillDO creating = new TradeWechatLogisticsWaybillDO().setOrderId(orderId)
                .setOrderNo(order.getNo()).setWechatOrderId(wechatOrderId).setOpenid(openid)
                .setDeliveryId(config.getDeliveryId()).setBizId(config.getBizId())
                .setStatus(WechatLogisticsWaybillStatusEnum.CREATING.name())
                .setPrintStatus(WechatLogisticsPrintStatusEnum.PENDING.name());
        waybillMapper.insert(creating);
        try {
            remote = socialClientApi.addWxaExpressOrder(UserTypeEnum.MEMBER.getValue(), request).getCheckedData();
        } catch (Exception ex) {
            creating.setStatus(WechatLogisticsWaybillStatusEnum.UNKNOWN.name()).setErrorMessage(ex.getMessage());
            waybillMapper.updateById(creating);
            // 保留 UNKNOWN 记录，下一次操作必须先查询相同 wechat_order_id，禁止盲目重试。
            return convertWaybill(creating);
        }
        if (remote == null || ObjectUtil.isEmpty(remote.getWaybillId())) {
            creating.setStatus(WechatLogisticsWaybillStatusEnum.FAILED.name())
                    .setErrorMessage("微信 addOrder 未返回 waybill_id");
            waybillMapper.updateById(creating);
            return convertWaybill(creating);
        }
        return saveCreatedWaybill(order, config, openid, wechatOrderId, remote);
    }

    @Override
    public List<WechatLogisticsWaybillRespVO> batchCreateWaybills(List<Long> orderIds) {
        return orderIds.stream().map(orderId -> {
            try {
                return createWaybill(orderId);
            } catch (Exception ex) {
                TradeWechatLogisticsWaybillDO waybill = waybillMapper.selectByOrderId(orderId);
                if (waybill == null) {
                    log.warn("[batchCreateWaybills][订单({}) 创建微信物流运单失败且没有本地结果]", orderId, ex);
                    return null;
                }
                return convertWaybill(waybill);
            }
        }).filter(Objects::nonNull).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatLogisticsWaybillRespVO confirmPrint(Long waybillId) {
        TradeWechatLogisticsWaybillDO waybill = validateWaybill(waybillId);
        if (!WechatLogisticsPrintStatusEnum.PENDING.name().equals(waybill.getPrintStatus())) {
            if (WechatLogisticsPrintStatusEnum.CONFIRMED.name().equals(waybill.getPrintStatus())) {
                return convertWaybill(waybill);
            }
            throw exception(WECHAT_LOGISTICS_PRINT_NOT_PENDING);
        }
        TradeOrderDO order = validateOrder(waybill.getOrderId());
        if (!UNDELIVERED.getStatus().equals(order.getStatus())) {
            throw exception(WECHAT_LOGISTICS_ORDER_NOT_UNDELIVERED);
        }
        DeliveryExpressDO express = deliveryExpressService.getDeliveryExpressByCode(waybill.getDeliveryId());
        if (express == null) {
            throw exception(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_NOT_EXISTS);
        }
        Long expressId = express.getId();
        tradeOrderUpdateService.deliveryOrder(new TradeOrderDeliveryReqVO().setId(order.getId())
                .setLogisticsId(expressId).setLogisticsNo(waybill.getWaybillId()));
        waybill.setPrintStatus(WechatLogisticsPrintStatusEnum.CONFIRMED.name());
        waybillMapper.updateById(waybill);
        return convertWaybill(waybill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelWaybill(Long waybillId) {
        TradeWechatLogisticsWaybillDO waybill = validateWaybill(waybillId);
        socialClientApi.cancelWxaExpressOrder(UserTypeEnum.MEMBER.getValue(), new SocialWxaExpressOrderQueryReqDTO()
                .setOrderId(waybill.getWechatOrderId()).setOpenid(waybill.getOpenid())
                .setDeliveryId(waybill.getDeliveryId()).setWaybillId(waybill.getWaybillId())).checkError();
        waybill.setStatus(WechatLogisticsWaybillStatusEnum.CANCELLED.name());
        waybillMapper.updateById(waybill);
    }

    @Override
    public WechatLogisticsWaybillRespVO getWaybill(Long waybillId) {
        return convertWaybill(validateWaybill(waybillId));
    }

    @Override
    public List<WechatLogisticsWaybillRespVO> getPendingWaybills() {
        return waybillMapper.selectListByPendingPrint().stream().map(this::convertWaybill).toList();
    }

    @Override
    public List<WechatLogisticsTraceRespVO> getTrace(Long waybillId) {
        validateWaybill(waybillId);
        return traceMapper.selectListByWaybillId(waybillId).stream().map(this::convertTrace).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTrace(Long waybillId) {
        TradeWechatLogisticsWaybillDO waybill = validateWaybill(waybillId);
        SocialWxaExpressPathRespDTO path = socialClientApi.getWxaExpressPath(UserTypeEnum.MEMBER.getValue(),
                new SocialWxaExpressOrderQueryReqDTO().setOrderId(waybill.getWechatOrderId())
                        .setOpenid(waybill.getOpenid()).setDeliveryId(waybill.getDeliveryId())
                        .setWaybillId(waybill.getWaybillId())).getCheckedData();
        traceMapper.delete(TradeWechatLogisticsTraceDO::getWaybillId, waybillId);
        if (path != null && path.getPathItemList() != null) {
            path.getPathItemList().forEach(item -> traceMapper.insert(new TradeWechatLogisticsTraceDO()
                    .setWaybillId(waybillId).setActionTime(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(item.getActionTime()), ZoneId.systemDefault()))
                    .setActionType(item.getActionType()).setActionMsg(item.getActionMsg())));
        }
        waybill.setLastSyncTime(LocalDateTime.now());
        waybillMapper.updateById(waybill);
    }

    @Override
    public WechatLogisticsPrinterRespVO bindPrinter(WechatLogisticsPrinterBindReqVO reqVO) {
        socialClientApi.updateWxaExpressPrinter(UserTypeEnum.MEMBER.getValue(),
                new SocialWxaExpressPrinterUpdateReqDTO().setOpenid(reqVO.getOpenid())
                        .setUpdateType(reqVO.getUpdateType()).setTagidList(reqVO.getTagidList())).checkError();
        return getPrinter();
    }

    @Override
    public WechatLogisticsPrinterRespVO getPrinter() {
        SocialWxaExpressPrinterRespDTO printer = socialClientApi
                .getWxaExpressPrinter(UserTypeEnum.MEMBER.getValue()).getCheckedData();
        return new WechatLogisticsPrinterRespVO().setCount(printer == null ? 0 : printer.getCount())
                .setOpenid(printer == null ? Collections.emptyList() : printer.getOpenid())
                .setTagidList(printer == null ? Collections.emptyList() : printer.getTagidList());
    }

    private TradeOrderDO validateOrder(Long orderId) {
        TradeOrderDO order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw exception(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND);
        }
        if (!TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())) {
            throw exception(WECHAT_LOGISTICS_ORDER_NOT_UNDELIVERED);
        }
        return order;
    }

    private PayOrderRespDTO validateWxLiteOrder(TradeOrderDO order) {
        if (!PayChannelEnum.WX_LITE.getCode().equals(order.getPayChannelCode()) || order.getPayOrderId() == null) {
            throw exception(WECHAT_LOGISTICS_OPENID_NOT_EXISTS);
        }
        PayOrderRespDTO payOrder = payOrderApi.getOrder(order.getPayOrderId()).getCheckedData();
        if (payOrder == null || ObjectUtil.isEmpty(payOrder.getChannelUserId())) {
            throw exception(WECHAT_LOGISTICS_OPENID_NOT_EXISTS);
        }
        return payOrder;
    }

    private TradeWechatLogisticsConfigDO validateConfig() {
        TradeWechatLogisticsConfigDO config = getConfig();
        if (config == null) {
            throw exception(WECHAT_LOGISTICS_CONFIG_NOT_EXISTS);
        }
        if (!Boolean.TRUE.equals(config.getEnabled()) || ObjectUtil.isEmpty(config.getBizId())
                || ObjectUtil.isEmpty(config.getDeliveryId()) || config.getServiceType() == null
                || ObjectUtil.isEmpty(config.getServiceName())) {
            throw exception(WECHAT_LOGISTICS_CONFIG_INVALID);
        }
        return config;
    }

    private TradeWechatLogisticsWaybillDO validateWaybill(Long id) {
        TradeWechatLogisticsWaybillDO waybill = waybillMapper.selectById(id);
        if (waybill == null) {
            throw exception(WECHAT_LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        return waybill;
    }

    private WechatLogisticsWaybillRespVO saveCreatedWaybill(TradeOrderDO order, TradeWechatLogisticsConfigDO config,
                                                             String openid, String wechatOrderId,
                                                             SocialWxaExpressOrderRespDTO remote) {
        TradeWechatLogisticsWaybillDO waybill = waybillMapper.selectByOrderId(order.getId());
        if (waybill == null) {
            waybill = new TradeWechatLogisticsWaybillDO().setOrderId(order.getId()).setOrderNo(order.getNo())
                    .setWechatOrderId(wechatOrderId).setOpenid(openid).setDeliveryId(config.getDeliveryId())
                    .setBizId(config.getBizId());
        }
        waybill.setWaybillId(remote.getWaybillId()).setStatus(WechatLogisticsWaybillStatusEnum.CREATED.name())
                .setPrintStatus(WechatLogisticsPrintStatusEnum.PENDING.name()).setWechatOrderStatus(remote.getOrderStatus())
                .setWaybillData(JsonUtils.toJsonString(remote.getWaybillData())).setErrorCode(null).setErrorMessage(null);
        if (waybill.getId() == null) {
            waybillMapper.insert(waybill);
        } else {
            waybillMapper.updateById(waybill);
        }
        return convertWaybill(waybill);
    }

    private WechatLogisticsWaybillRespVO convertWaybill(TradeWechatLogisticsWaybillDO waybill) {
        return new WechatLogisticsWaybillRespVO().setId(waybill.getId()).setOrderId(waybill.getOrderId())
                .setOrderNo(waybill.getOrderNo()).setWechatOrderId(waybill.getWechatOrderId())
                .setDeliveryId(waybill.getDeliveryId()).setBizId(waybill.getBizId()).setWaybillId(waybill.getWaybillId())
                .setStatus(waybill.getStatus()).setPrintStatus(waybill.getPrintStatus())
                .setWechatOrderStatus(waybill.getWechatOrderStatus()).setErrorCode(waybill.getErrorCode())
                .setErrorMessage(waybill.getErrorMessage()).setLastSyncTime(waybill.getLastSyncTime())
                .setCreateTime(waybill.getCreateTime()).setUpdateTime(waybill.getUpdateTime());
    }

    private WechatLogisticsTraceRespVO convertTrace(TradeWechatLogisticsTraceDO trace) {
        return new WechatLogisticsTraceRespVO().setId(trace.getId()).setWaybillId(trace.getWaybillId())
                .setActionTime(trace.getActionTime()).setActionType(trace.getActionType()).setActionMsg(trace.getActionMsg());
    }

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }
}
