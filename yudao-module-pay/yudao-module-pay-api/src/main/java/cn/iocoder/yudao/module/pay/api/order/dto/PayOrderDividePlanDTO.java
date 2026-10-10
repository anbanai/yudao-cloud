package cn.iocoder.yudao.module.pay.api.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 支付单分账计划明细。
 *
 * <p>金额使用分作为单位。商编和金额在创建支付单时作为不可变快照传入，
 * 便于支付侧在支付成功后异步执行分账。</p>
 */
@Data
public class PayOrderDividePlanDTO implements Serializable {

    /**
     * 交易订单编号
     */
    @NotNull(message = "交易订单编号不能为空")
    private Long tradeOrderId;

    /**
     * 交易子订单编号
     */
    @NotNull(message = "交易子订单编号不能为空")
    private Long tradeOrderSubId;

    /** 交易订单项编号，用于将售后单精确映射回分账明细。 */
    private Long tradeOrderItemId;

    /**
     * 平台商家编号
     */
    @NotNull(message = "商家编号不能为空")
    private Long merchantId;

    /**
     * 易宝商编快照
     */
    private String merchantNo;

    /**
     * 分账金额，单位：分
     */
    @NotNull(message = "分账金额不能为空")
    @Min(value = 1, message = "分账金额必须大于零")
    private Integer amount;

}
