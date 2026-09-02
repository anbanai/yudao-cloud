package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SfLogisticsClientTest {

    private SfOpenApiClient openApiClient;
    private SfLogisticsClient client;

    @BeforeEach
    void setUp() {
        openApiClient = mock(SfOpenApiClient.class);
        client = new SfLogisticsClient();
        ReflectionTestUtils.setField(client, "openApiClient", openApiClient);
    }

    @Test
    void createWaybillUsesSummedSkuWeight() {
        TradeOrderItemDO first = new TradeOrderItemDO().setSkuId(1L).setSpuName("茶叶 A").setCount(2);
        TradeOrderItemDO second = new TradeOrderItemDO().setSkuId(2L).setSpuName("茶叶 B").setCount(1);
        Map<Long, ProductSkuRespDTO> skus = Map.of(
                1L, new ProductSkuRespDTO().setId(1L).setWeight(0.25),
                2L, new ProductSkuRespDTO().setId(2L).setWeight(0.70));
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        when(openApiClient.invoke(any(), eq(SfLogisticsClient.CREATE_ORDER), payload.capture()))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("waybillNo", "SF001"));

        client.createWaybill(account(), "ORDER-1", order(), List.of(first, second), skus);

        assertThat(payload.getValue().path("totalWeight").decimalValue())
                .isEqualByComparingTo(new BigDecimal("1.20"));
    }

    @Test
    void createWaybillUsesDefaultWeightWhenAnySkuWeightIsMissing() {
        TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(1L).setSpuName("茶叶").setCount(2);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        when(openApiClient.invoke(any(), eq(SfLogisticsClient.CREATE_ORDER), payload.capture()))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("waybillNo", "SF001"));

        client.createWaybill(account(), "ORDER-1", order(), List.of(item), Map.of());

        assertThat(payload.getValue().path("totalWeight").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void createWaybillMissingWaybillNumberIsUnknownResult() {
        when(openApiClient.invoke(any(), eq(SfLogisticsClient.CREATE_ORDER), any()))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        assertThatThrownBy(() -> client.createWaybill(account(), "ORDER-1", order(), List.of(), Map.of()))
                .isInstanceOfSatisfying(SfApiException.class,
                        exception -> assertThat(exception.isUnknownResult()).isTrue());
    }

    @Test
    void getLabelRequestsSynchronousPdfAndUsesDownloadToken() throws Exception {
        byte[] expected = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        SfLogisticsClient testClient = new SfLogisticsClient() {
            @Override
            byte[] downloadLabel(String url, String token) {
                assertThat(url).isEqualTo("https://download.sf-express.com/label.pdf");
                assertThat(token).isEqualTo("download-token");
                return expected;
            }
        };
        ReflectionTestUtils.setField(testClient, "openApiClient", openApiClient);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        when(openApiClient.invoke(any(), eq(SfLogisticsClient.CLOUD_PRINT), payload.capture()))
                .thenReturn(JsonNodeFactory.instance.objectNode()
                        .put("url", "https://download.sf-express.com/label.pdf")
                        .put("token", "download-token"));

        byte[] actual = testClient.getLabel(account().setTemplateCode("fm_verified"), "SF001");

        assertThat(actual).isEqualTo(expected);
        assertThat(payload.getValue().path("sync").asBoolean()).isTrue();
        assertThat(payload.getValue().path("templateCode").asText()).isEqualTo("fm_verified");
    }

    @Test
    void validateLabelDownloadUrlRejectsInsecureAndPrivateDestinations() {
        assertThatThrownBy(() -> SfLogisticsClient.validateLabelDownloadUrl(
                "http://download.sf-express.com/a.pdf", List.of("sf-express.com")))
                .isInstanceOf(SfApiException.class);
        assertThatThrownBy(() -> SfLogisticsClient.validateLabelDownloadUrl(
                "https://127.0.0.1/a.pdf", List.of("sf-express.com")))
                .isInstanceOf(SfApiException.class);
        assertThatThrownBy(() -> SfLogisticsClient.validateLabelDownloadUrl(
                "https://localhost/a.pdf", List.of("sf-express.com")))
                .isInstanceOf(SfApiException.class);
    }

    @Test
    void validateLabelDownloadUrlRejectsHostOutsideConfiguredSuffixes() {
        assertThatThrownBy(() -> SfLogisticsClient.validateLabelDownloadUrl(
                "https://files.example.test/label.pdf", List.of("sf-express.com", "sf-express.cn")))
                .isInstanceOfSatisfying(SfApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LABEL_URL_REJECTED"));
    }

    @Test
    void decodeInlineLabelRejectsNonPdfContent() {
        String content = java.util.Base64.getEncoder().encodeToString("not-a-pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> SfLogisticsClient.decodeInlineLabel(content))
                .isInstanceOfSatisfying(SfApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LABEL_INVALID_PDF"));
    }

    @Test
    void decodeInlineLabelRejectsOversizedEncodedContentBeforeDecode() {
        String content = "A".repeat(14_000_000);

        assertThatThrownBy(() -> SfLogisticsClient.decodeInlineLabel(content))
                .isInstanceOfSatisfying(SfApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LABEL_TOO_LARGE"));
    }

    private static TradeLogisticsAccountDO account() {
        return new TradeLogisticsAccountDO().setMonthlyCard("monthly").setServiceCode("1")
                .setDefaultWeightKg(new BigDecimal("0.80")).setSenderName("仓库")
                .setSenderPhone("13800138000").setSenderProvince("四川省").setSenderCity("成都市")
                .setSenderDistrict("高新区").setSenderAddress("天府大道 1 号");
    }

    private static TradeOrderDO order() {
        return new TradeOrderDO().setReceiverName("收件人").setReceiverMobile("13900139000")
                .setReceiverDetailAddress("世纪城 1 号");
    }
}
