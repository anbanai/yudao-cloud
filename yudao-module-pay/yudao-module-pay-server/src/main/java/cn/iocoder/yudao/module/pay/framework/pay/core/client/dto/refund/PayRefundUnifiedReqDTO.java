package cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 统一 退款 Request DTO
 *
 * @author jason
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PayRefundUnifiedReqDTO {

    /**
     * 外部订单号
     *
     * 对应 PayOrderExtensionDO 的 no 字段
     */
    @NotEmpty(message = "外部订单编号不能为空")
    private String outTradeNo;

    /**
     * 易宝渠道订单号，对应官方接口的 uniqueOrderNo。
     * 易宝退款请求在该字段存在时只发送 uniqueOrderNo，不与 orderId 同时发送。
     */
    private String uniqueOrderNo;

    /** 易宝原支付资金快照的商编；已有订单不能随渠道配置切换收款主体。 */
    private String parentMerchantNo;
    private String collectionMerchantNo;

    /**
     * 外部退款号
     *
     * 对应 PayRefundDO 的 no 字段
     */
    @NotEmpty(message = "退款请求单号不能为空")
    private String outRefundNo;

    /**
     * 退款原因
     */
    @NotEmpty(message = "退款原因不能为空")
    private String reason;

    /**
     * 支付金额，单位：分
     *
     * 目前微信支付在退款的时候，必须传递该字段
     */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    private Integer payPrice;
    /**
     * 退款金额，单位：分
     */
    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    private Integer refundPrice;

    /**
     * 退款结果的 notify 回调地址
     */
    @NotEmpty(message = "支付结果的回调地址不能为空")
    @URL(message = "支付结果的 notify 回调地址必须是 URL 格式")
    private String notifyUrl;

    /**
     * 易宝退款资金归集明细。普通渠道忽略该字段，易宝客户端将其转换为 divideBackDetail。
     */
    private List<PayRefundFundAllocationDTO> fundAllocation;

}
