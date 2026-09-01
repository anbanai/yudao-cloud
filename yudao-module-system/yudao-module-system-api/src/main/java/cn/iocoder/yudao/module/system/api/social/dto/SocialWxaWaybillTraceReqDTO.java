package cn.iocoder.yudao.module.system.api.social.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 微信小程序物流查询组件运单追踪请求。
 */
@Data
public class SocialWxaWaybillTraceReqDTO {

    @NotEmpty(message = "支付者 openid 不能为空")
    private String openid;

    @NotEmpty(message = "收件人手机号不能为空")
    private String receiverPhone;

    @NotEmpty(message = "运单号不能为空")
    private String waybillId;

    @NotEmpty(message = "微信支付交易号不能为空")
    private String transactionId;

    @NotEmpty(message = "快递公司编码不能为空")
    private String deliveryId;

    @NotEmpty(message = "订单详情路径不能为空")
    private String orderDetailPath;

    @Valid
    @NotEmpty(message = "商品信息不能为空")
    private List<GoodsItem> goods;

    @Data
    public static class GoodsItem {

        @NotEmpty(message = "商品名称不能为空")
        private String name;

        @NotEmpty(message = "商品图片不能为空")
        private String imageUrl;

    }

}
