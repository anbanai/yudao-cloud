package cn.iocoder.yudao.module.trade.framework.logistics.returnshipment;

import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfOpenApiClient;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfLogisticsClient;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class SfReturnShipmentProvider implements ReturnShipmentProvider {

    @Value("${yudao.trade.logistics.sf.return-shipment-enabled:false}")
    private boolean enabled;

    @Value("${yudao.trade.logistics.sf.return-shipment-api-service-code:EXP_RECE_CREATE_ORDER}")
    private String apiServiceCode;

    @Value("${yudao.trade.logistics.sf.return-shipment-express-type-id:}")
    private String returnShipmentExpressTypeId;

    @Value("${yudao.trade.logistics.sf.return-shipment-fee-unit:fen}")
    private String feeUnit;

    @Resource
    private SfOpenApiClient openApiClient;
    @Resource
    private TradeLogisticsAccountMapper accountMapper;

    @Override
    public String getCode() {
        return "SF";
    }

    @Override
    public boolean isAvailable() {
        return enabled && StrUtil.isNotBlank(apiServiceCode);
    }

    @Override
    public Result create(AfterSaleReturnShipmentDO shipment) {
        TradeLogisticsAccountDO account = requireAccount(shipment);
        ObjectNode request = buildRequest(account, shipment);
        JsonNode response = openApiClient.invoke(account, apiServiceCode, request);
        String waybill = text(response, "waybillNo", "mailNo", "waybill");
        if (waybill == null) {
            JsonNode list = response.path("waybillNoInfoList");
            if (list.isArray() && !list.isEmpty()) waybill = text(list.get(0), "waybillNo", "mailNo");
        }
        if (waybill == null || waybill.isBlank()) {
            throw new SfApiException("WAYBILL_NOT_FOUND", "顺丰退货接口未返回运单号", true, null);
        }
        Integer estimatedFee = fee(response, "estimatedFee", "estimateFee", "fee");
        return new Result(shipment.getProviderOrderNo(), waybill, "PICKUP_PENDING",
                response.toString(), estimatedFee, null);
    }

    @Override
    public Result query(AfterSaleReturnShipmentDO shipment) {
        TradeLogisticsAccountDO account = requireAccount(shipment);
        ObjectNode request = JsonNodeFactory.instance.objectNode().put("orderId", shipment.getProviderOrderNo())
                .put("searchType", 1).put("language", "zh-CN");
        JsonNode response = openApiClient.invoke(account, SfLogisticsClient.SEARCH_ORDER, request);
        String waybill = text(response, "waybillNo", "mailNo");
        Integer estimatedFee = fee(response, "estimatedFee", "estimateFee", "fee");
        Integer actualFee = fee(response, "actualFee", "realFee", "fee");
        return new Result(shipment.getProviderOrderNo(), waybill, status(response), response.toString(),
                estimatedFee, actualFee);
    }

    @Override
    public void cancel(AfterSaleReturnShipmentDO shipment) {
        TradeLogisticsAccountDO account = requireAccount(shipment);
        ObjectNode request = JsonNodeFactory.instance.objectNode().put("orderId", shipment.getProviderOrderNo())
                .put("dealType", 2).put("language", "zh-CN");
        openApiClient.invoke(account, SfLogisticsClient.UPDATE_ORDER, request);
    }

    @Override
    public List<Trace> queryTraces(AfterSaleReturnShipmentDO shipment) {
        if (shipment.getWaybillNo() == null || shipment.getWaybillNo().isBlank()) return List.of();
        TradeLogisticsAccountDO account = requireAccount(shipment);
        ObjectNode request = JsonNodeFactory.instance.objectNode().put("trackingType", 1)
                .put("methodType", 1).put("language", "zh-CN");
        request.putArray("trackingNumber").add(shipment.getWaybillNo());
        JsonNode response = openApiClient.invoke(account, SfLogisticsClient.SEARCH_ROUTES, request);
        List<Trace> result = new ArrayList<>();
        collectRoutes(response, result);
        return result;
    }

    private TradeLogisticsAccountDO requireAccount(AfterSaleReturnShipmentDO shipment) {
        TradeLogisticsAccountDO account = shipment.getAccountId() == null ? null : accountMapper.selectById(shipment.getAccountId());
        if (account == null || account.getStatus() == null || account.getStatus() != 0) {
            throw new SfApiException("SF_ACCOUNT_NOT_CONFIGURED", "顺丰物流账号未配置或已停用");
        }
        return account;
    }

    private ObjectNode buildRequest(TradeLogisticsAccountDO account, AfterSaleReturnShipmentDO shipment) {
        ObjectNode request = JsonNodeFactory.instance.objectNode().put("language", "zh-CN")
                .put("orderId", shipment.getProviderOrderNo()).put("monthlyCard", account.getMonthlyCard())
                .put("expressTypeId", resolveExpressTypeId(account)).put("payMethod", 1)
                .put("parcelQty", 1).put("totalWeight", shipment.getPackageWeight().doubleValue());
        ArrayNode contacts = request.putArray("contactInfoList");
        contacts.add(contact(1, shipment.getPickupName(), shipment.getPickupMobile(), shipment.getPickupProvince(),
                shipment.getPickupCity(), shipment.getPickupDistrict(), shipment.getPickupAddress()));
        contacts.add(contact(2, shipment.getWarehouseName(), shipment.getWarehousePhone(), shipment.getWarehouseProvince(),
                shipment.getWarehouseCity(), shipment.getWarehouseDistrict(), shipment.getWarehouseAddress()));
        if (shipment.getAppointmentTime() != null) {
            request.put("appointmentTime", shipment.getAppointmentTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        request.putArray("cargoDetails").addObject().put("name", "退货商品").put("count", 1).put("unit", "件");
        return request;
    }

    private ObjectNode contact(int type, String name, String phone, String province, String city,
                               String district, String address) {
        return JsonNodeFactory.instance.objectNode().put("contactType", type).put("contact", name)
                .put("tel", phone).put("province", province).put("city", city)
                .put("county", district).put("address", address);
    }

    private int resolveExpressTypeId(TradeLogisticsAccountDO account) {
        String value = StrUtil.blankToDefault(returnShipmentExpressTypeId, account.getServiceCode());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new SfApiException("RETURN_EXPRESS_TYPE_INVALID", "顺丰退货产品类型未正确配置", false, exception);
        }
    }

    private String status(JsonNode response) {
        String value = text(response, "status", "orderStatus", "opCode");
        return value == null ? "PICKUP_PENDING" : switch (value.toUpperCase()) {
            case "1", "10", "50", "CREATED", "已收件", "揽收" -> "PICKED_UP";
            case "2", "3", "在途中", "运输中", "派送中" -> "IN_TRANSIT";
            case "4", "已签收", "签收" -> "DELIVERED";
            case "5", "问题件", "异常" -> "EXCEPTION";
            default -> "PICKUP_PENDING";
        };
    }

    private void collectRoutes(JsonNode node, List<Trace> result) {
        if (node == null || node.isMissingNode()) return;
        if (node.isArray()) { node.forEach(item -> collectRoutes(item, result)); return; }
        if (node.has("routeResps")) { collectRoutes(node.get("routeResps"), result); return; }
        if (node.has("routes")) { collectRoutes(node.get("routes"), result); return; }
        String eventId = text(node, "id", "eventId", "opCode");
        String description = text(node, "remark", "reasonName", "description");
        if (eventId == null || description == null) return;
        String routeStatus = status(node);
        LocalDateTime time = parseTime(text(node, "acceptTime", "operateTime", "time"));
        result.add(new Trace(eventId, routeStatus, description, text(node, "acceptAddress", "location"), time, node.toString()));
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value.replace(' ', 'T')); }
        catch (RuntimeException ignored) { return null; }
    }

    private String text(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) if (node.hasNonNull(name) && !node.get(name).asText().isBlank()) return node.get(name).asText();
        return null;
    }

    private Integer fee(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null) return null;
        try {
            double amount = Double.parseDouble(value);
            return "yuan".equalsIgnoreCase(feeUnit) ? (int) Math.round(amount * 100) : (int) Math.round(amount);
        }
        catch (NumberFormatException ignored) { return null; }
    }
}
