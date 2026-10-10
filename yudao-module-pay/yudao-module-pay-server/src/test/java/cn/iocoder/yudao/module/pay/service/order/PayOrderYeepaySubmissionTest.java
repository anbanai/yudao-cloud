package cn.iocoder.yudao.module.pay.service.order;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderDividePlanDTO;
import cn.iocoder.yudao.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.config.PayProperties;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.notify.PayNotifyService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants.PAY_ORDER_DIVIDE_PLAN_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(PayOrderServiceImpl.class)
class PayOrderYeepaySubmissionTest extends BaseDbUnitTest {

    @Resource
    private PayOrderServiceImpl orderService;
    @Resource
    private PayOrderMapper orderMapper;
    @Resource
    private PayOrderExtensionMapper extensionMapper;
    @MockitoBean
    private PayProperties properties;
    @MockitoBean
    private PayNoRedisDAO noRedisDAO;
    @MockitoBean
    private PayAppService appService;
    @MockitoBean
    private PayChannelService channelService;
    @MockitoBean
    private PayNotifyService notifyService;
    @MockitoBean
    private PayFundService fundService;

    @Test
    void repeatedYeepaySubmission_reusesSingleExternalOrder() {
        PayOrderDO order = waitingOrder();
        PayClient<?> client = configureChannel(PayChannelEnum.YEEPAY_WX_LITE);
        PayOrderSubmitReqVO request = submitRequest(order, PayChannelEnum.YEEPAY_WX_LITE);

        orderService.submitOrder(request, "127.0.0.1");
        orderService.submitOrder(request, "127.0.0.1");

        assertThat(extensionMapper.selectListByOrderId(order.getId())).hasSize(1);
        ArgumentCaptor<PayOrderUnifiedReqDTO> requests = ArgumentCaptor.forClass(PayOrderUnifiedReqDTO.class);
        verify(client, times(2)).unifiedOrder(requests.capture());
        assertThat(requests.getAllValues()).extracting(PayOrderUnifiedReqDTO::getOutTradeNo)
                .containsExactly("P-STABLE-1", "P-STABLE-1");
        assertThat(JsonUtils.toJsonString(requests.getValue())).contains("\"parentMerchantNo\":\"ORIGINAL-PARENT\"")
                .contains("\"collectionMerchantNo\":\"ORIGINAL-COLLECTION\"");
    }

    @Test
    void repeatedWxSubmission_preservesSeparatePaymentAttempts() {
        PayOrderDO order = waitingOrder();
        configureChannel(PayChannelEnum.WX_LITE);
        PayOrderSubmitReqVO request = submitRequest(order, PayChannelEnum.WX_LITE);

        orderService.submitOrder(request, "127.0.0.1");
        orderService.submitOrder(request, "127.0.0.1");

        assertThat(extensionMapper.selectListByOrderId(order.getId())).hasSize(2);
    }

    @Test
    void updatePrice_afterYeepayPreparation_rejectsChangingFrozenSnapshot() {
        PayOrderDO order = waitingOrder();
        extensionMapper.insert(randomPojo(PayOrderExtensionDO.class, extension -> extension
                .setOrderId(order.getId()).setNo("P-STABLE-1").setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setStatus(PayOrderStatusEnum.WAITING.getStatus())));

        assertServiceException(() -> orderService.updatePayOrderPrice(order.getId(), 150),
                PAY_ORDER_DIVIDE_PLAN_INVALID);
        assertThat(orderMapper.selectById(order.getId()).getPrice()).isEqualTo(100);
    }

    @Test
    void updatePriceAndPlan_beforePreparation_persistsMatchingAllocation() {
        PayOrderDO order = waitingOrder();
        List<PayOrderDividePlanDTO> original = List.of(plan(100));
        orderMapper.updateById(new PayOrderDO().setId(order.getId()).setDividePlanData(JsonUtils.toJsonString(original)));

        updatePriceWithPlan(order.getId(), 150, List.of(plan(150)));

        PayOrderDO updated = orderMapper.selectById(order.getId());
        assertThat(updated.getPrice()).isEqualTo(150);
        assertThat(JsonUtils.parseArray(updated.getDividePlanData(), PayOrderDividePlanDTO.class))
                .extracting(PayOrderDividePlanDTO::getAmount).containsExactly(150);
    }

    @Test
    void updatePriceWithoutPlan_rejectsStaleMerchantAllocation() {
        PayOrderDO order = waitingOrder();
        orderMapper.updateById(new PayOrderDO().setId(order.getId())
                .setDividePlanData(JsonUtils.toJsonString(List.of(plan(100)))));

        assertServiceException(() -> orderService.updatePayOrderPrice(order.getId(), 150),
                PAY_ORDER_DIVIDE_PLAN_INVALID);
        assertThat(orderMapper.selectById(order.getId()).getPrice()).isEqualTo(100);
    }

    private PayOrderDividePlanDTO plan(int amount) {
        return new PayOrderDividePlanDTO().setTradeOrderId(30L).setTradeOrderSubId(10L)
                .setMerchantId(1L).setAmount(amount);
    }

    private void updatePriceWithPlan(Long id, int amount, List<PayOrderDividePlanDTO> plans) {
        orderService.updatePayOrderPrice(id, amount, plans);
    }

    private PayOrderDO waitingOrder() {
        PayOrderDO order = randomPojo(PayOrderDO.class, value -> value.setAppId(1L).setPrice(100)
                .setDividePlanData(null).setStatus(PayOrderStatusEnum.WAITING.getStatus()).setExpireTime(LocalDateTime.now().plusDays(1)));
        orderMapper.insert(order);
        return order;
    }

    private PayClient<?> configureChannel(PayChannelEnum channelCode) {
        when(properties.getOrderNoPrefix()).thenReturn("P");
        when(properties.getOrderNotifyUrl()).thenReturn("https://platform.test/pay/notify/order");
        when(noRedisDAO.generate("P")).thenReturn("P-STABLE-1", "P-ATTEMPT-2");
        when(appService.validPayApp(1L)).thenReturn(new PayAppDO().setId(1L));
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L).setCode(channelCode.getCode());
        when(channelService.validPayChannel(1L, channelCode.getCode())).thenReturn(channel);
        PayClient<?> client = mock(PayClient.class);
        when(channelService.getPayClient(10L)).thenReturn(client);
        when(fundService.buildPrePayDivideDetail(any())).thenReturn("[]");
        when(fundService.getFundOrderByPayOrderId(anyLong())).thenReturn(new PayFundOrderDO()
                .setParentMerchantNo("ORIGINAL-PARENT").setCollectionMerchantNo("ORIGINAL-COLLECTION"));
        return client;
    }

    private PayOrderSubmitReqVO submitRequest(PayOrderDO order, PayChannelEnum channel) {
        return new PayOrderSubmitReqVO().setId(order.getId()).setChannelCode(channel.getCode());
    }

}
