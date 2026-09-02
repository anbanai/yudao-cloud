package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SfLogisticsClient {

    private static final int MAX_LABEL_BYTES = 10 * 1024 * 1024;
    private static final int MAX_LABEL_BASE64_CHARS = ((MAX_LABEL_BYTES + 2) / 3) * 4;

    public static final String CREATE_ORDER = "EXP_RECE_CREATE_ORDER";
    public static final String SEARCH_ORDER = "EXP_RECE_SEARCH_ORDER_RESP";
    public static final String UPDATE_ORDER = "EXP_RECE_UPDATE_ORDER";
    public static final String CLOUD_PRINT = "COM_RECE_CLOUD_PRINT_WAYBILLS";
    public static final String SEARCH_ROUTES = "EXP_RECE_SEARCH_ROUTES";

    @Resource
    private SfOpenApiClient openApiClient;
    @Value("${yudao.trade.logistics.sf.label-host-suffixes:sf-express.com,sf-express.cn}")
    private String labelHostSuffixes;

    public WaybillResult createWaybill(TradeLogisticsAccountDO account, String providerOrderNo,
                                       TradeOrderDO order, List<TradeOrderItemDO> items,
                                       Map<Long, ProductSkuRespDTO> skuMap) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("language", "zh-CN").put("orderId", providerOrderNo)
                .put("monthlyCard", account.getMonthlyCard())
                .put("expressTypeId", Integer.parseInt(account.getServiceCode()))
                .put("payMethod", 1).put("parcelQty", 1).put("isReturnRoutelabel", 1)
                .put("totalWeight", calculateTotalWeight(items, skuMap, account.getDefaultWeightKg()));
        ArrayNode contacts = request.putArray("contactInfoList");
        contacts.add(contact(1, account.getSenderName(), account.getSenderPhone(), account.getSenderProvince(),
                account.getSenderCity(), account.getSenderDistrict(), account.getSenderAddress()));
        ReceiverArea receiverArea = resolveArea(order.getReceiverAreaId());
        contacts.add(contact(2, order.getReceiverName(), order.getReceiverMobile(), receiverArea.province(),
                receiverArea.city(), receiverArea.district(), order.getReceiverDetailAddress()));
        ArrayNode cargos = request.putArray("cargoDetails");
        for (TradeOrderItemDO item : items) {
            cargos.addObject().put("name", StrUtil.maxLength(item.getSpuName(), 100))
                    .put("count", item.getCount()).put("unit", "件");
        }
        JsonNode response;
        try {
            response = openApiClient.invoke(account, CREATE_ORDER, request);
        } catch (SfApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SfApiException("INVALID_RESPONSE", "顺丰创建运单响应无法解析", true, exception);
        }
        return new WaybillResult(providerOrderNo, findWaybillNo(response), response);
    }

    public WaybillResult queryByProviderOrderNo(TradeLogisticsAccountDO account, String providerOrderNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("orderId", providerOrderNo).put("searchType", 1).put("language", "zh-CN");
        JsonNode response = openApiClient.invoke(account, SEARCH_ORDER, request);
        return new WaybillResult(providerOrderNo, findWaybillNo(response), response);
    }

    public byte[] getLabel(TradeLogisticsAccountDO account, String waybillNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("templateCode", account.getTemplateCode()).put("version", "2.0").put("fileType", "pdf")
                .put("sync", true);
        request.putArray("documents").addObject().put("masterWaybillNo", waybillNo);
        JsonNode response = openApiClient.invoke(account, CLOUD_PRINT, request);
        String url = findText(response, "fileUrl", "url", "downloadUrl");
        if (StrUtil.isNotBlank(url)) {
            String token = findText(response, "token", "xAuthToken", "X-Auth-token");
            if (StrUtil.isBlank(token)) {
                throw new SfApiException("LABEL_TOKEN_NOT_FOUND", "顺丰云打印返回中没有 PDF 下载凭证");
            }
            return downloadLabel(url, token);
        }
        String content = findText(response, "fileContent", "base64", "pdfBase64");
        if (StrUtil.isBlank(content)) {
            throw new SfApiException("LABEL_NOT_FOUND", "顺丰云打印返回中没有 PDF 文件");
        }
        return decodeInlineLabel(content);
    }

    byte[] downloadLabel(String url, String token) {
        validateLabelDownloadUrl(url, parseHostSuffixes(labelHostSuffixes));
        try (HttpResponse download = HttpRequest.get(url).header("X-Auth-token", token)
                .setFollowRedirects(false).timeout(20_000).execute()) {
            if (!download.isOk()) {
                throw new SfApiException("LABEL_HTTP_" + download.getStatus(),
                        "顺丰面单下载失败，HTTP 状态 " + download.getStatus());
            }
            if (download.contentLength() > MAX_LABEL_BYTES) {
                throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单文件超过 10 MB 限制");
            }
            byte[] bytes = download.bodyStream().readNBytes(MAX_LABEL_BYTES + 1);
            if (bytes.length > MAX_LABEL_BYTES) {
                throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单文件超过 10 MB 限制");
            }
            return validatePdf(bytes);
        } catch (SfApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new SfApiException("LABEL_DOWNLOAD_FAILED", "顺丰面单下载失败", false, exception);
        }
    }

    static void validateLabelDownloadUrl(String url, List<String> allowedHostSuffixes) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || StrUtil.isBlank(uri.getHost())
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = allowedHostSuffixes.stream().map(String::trim)
                    .map(suffix -> StrUtil.removePrefix(suffix.toLowerCase(Locale.ROOT), "."))
                    .filter(StrUtil::isNotBlank)
                    .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
            if (!allowed) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (IllegalArgumentException | IOException exception) {
            throw new SfApiException("LABEL_URL_REJECTED", "顺丰面单下载地址不安全", false, exception);
        }
    }

    static byte[] decodeInlineLabel(String content) {
        if (content.length() > MAX_LABEL_BASE64_CHARS + 128) {
            throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单文件超过 10 MB 限制");
        }
        int comma = content.indexOf(',');
        if (comma >= 128) {
            throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单 Base64 数据头无效");
        }
        String encoded = (comma >= 0 ? content.substring(comma + 1) : content).replaceAll("\\s", "");
        if (encoded.length() > MAX_LABEL_BASE64_CHARS) {
            throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单文件超过 10 MB 限制");
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
            if (bytes.length > MAX_LABEL_BYTES) {
                throw new SfApiException("LABEL_TOO_LARGE", "顺丰面单文件超过 10 MB 限制");
            }
            return validatePdf(bytes);
        } catch (IllegalArgumentException exception) {
            throw new SfApiException("LABEL_INVALID_PDF", "顺丰面单 Base64 数据无效", false, exception);
        }
    }

    private static byte[] validatePdf(byte[] bytes) {
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new SfApiException("LABEL_INVALID_PDF", "顺丰面单内容不是有效 PDF");
        }
        return bytes;
    }

    private static List<String> parseHostSuffixes(String value) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        return StrUtil.split(value, ',');
    }

    public void cancelWaybill(TradeLogisticsAccountDO account, String providerOrderNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("orderId", providerOrderNo).put("dealType", 2).put("language", "zh-CN");
        openApiClient.invoke(account, UPDATE_ORDER, request);
    }

    public JsonNode queryTrace(TradeLogisticsAccountDO account, String waybillNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("trackingType", 1).put("methodType", 1).put("language", "zh-CN");
        request.putArray("trackingNumber").add(waybillNo);
        return openApiClient.invoke(account, SEARCH_ROUTES, request);
    }

    private ObjectNode contact(int type, String name, String phone, String province, String city,
                               String district, String address) {
        return JsonNodeFactory.instance.objectNode().put("contactType", type).put("contact", name)
                .put("tel", phone).put("province", province).put("city", city).put("county", district)
                .put("address", address);
    }

    private String findWaybillNo(JsonNode response) {
        String value = findText(response, "waybillNo", "waybillNoInfo", "mailNo");
        if (StrUtil.isBlank(value)) {
            JsonNode list = response.path("waybillNoInfoList");
            if (list.isArray() && !list.isEmpty()) {
                value = findText(list.get(0), "waybillNo", "mailNo");
            }
        }
        if (StrUtil.isBlank(value)) {
            throw new SfApiException("WAYBILL_NOT_FOUND", "顺丰返回中没有运单号", true, null);
        }
        return value;
    }

    private BigDecimal calculateTotalWeight(List<TradeOrderItemDO> items,
                                            Map<Long, ProductSkuRespDTO> skuMap,
                                            BigDecimal defaultWeight) {
        if (items == null || items.isEmpty() || skuMap == null) {
            return defaultWeight;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (TradeOrderItemDO item : items) {
            ProductSkuRespDTO sku = skuMap.get(item.getSkuId());
            if (sku == null || sku.getWeight() == null || sku.getWeight() <= 0
                    || item.getCount() == null || item.getCount() <= 0) {
                return defaultWeight;
            }
            total = total.add(BigDecimal.valueOf(sku.getWeight())
                    .multiply(BigDecimal.valueOf(item.getCount())));
        }
        return total.signum() > 0 ? total : defaultWeight;
    }

    private String findText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode() && StrUtil.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = findText(child, names);
                if (StrUtil.isNotBlank(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private ReceiverArea resolveArea(Integer areaId) {
        String province = "";
        String city = "";
        String district = "";
        Area area = areaId == null ? null : AreaUtils.getArea(areaId);
        while (area != null) {
            if (AreaTypeEnum.PROVINCE.getType().equals(area.getType())) {
                province = area.getName();
            } else if (AreaTypeEnum.CITY.getType().equals(area.getType())) {
                city = area.getName();
            } else if (AreaTypeEnum.DISTRICT.getType().equals(area.getType())) {
                district = area.getName();
            }
            area = area.getParent();
        }
        return new ReceiverArea(province, city, district);
    }

    public record WaybillResult(String providerOrderNo, String waybillNo, JsonNode rawResponse) {
    }

    private record ReceiverArea(String province, String city, String district) {
    }
}
