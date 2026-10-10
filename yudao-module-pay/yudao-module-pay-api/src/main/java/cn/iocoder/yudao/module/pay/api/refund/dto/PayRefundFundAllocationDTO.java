package cn.iocoder.yudao.module.pay.api.refund.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 退款单的资金分配明细。
 *
 * <p>每一项对应支付时保存的一条分账明细，金额使用分作为单位；
 * divideDetailNo 用于易宝资金归还时定位原分账明细。</p>
 */
@Data
public class PayRefundFundAllocationDTO implements Serializable {

    /**
     * 售后单编号
     */
    private Long afterSaleId;

    /**
     * 支付资金明细编号
     */
    private Long fundDetailId;

    /** 交易订单项编号；支付侧在没有显式资金明细编号时据此解析快照。 */
    private Long tradeOrderItemId;

    /** 商家履约子订单编号；资金明细按子订单聚合。 */
    private Long tradeOrderSubId;

    /**
     * 平台商家编号
     */
    private Long merchantId;

    /**
     * 易宝退款资金归集商编快照。由支付资金模块根据原分账明细回填，调用方不能自行决定。
     */
    private String ledgerNo;

    /**
     * 本次退款金额，单位：分
     */
    @NotNull(message = "退款分配金额不能为空")
    @Min(value = 1, message = "退款分配金额必须大于零")
    private Integer amount;

    /**
     * 原分账明细号
     */
    private String divideDetailNo;

    /**
     * 延迟结算订单从待分账账户扣款时使用的扣账账户类型。
     *
     * <p>易宝官方退款协议当前支持 {@code DIVIDE_ACCOUNT}。使用该字段时，
     * {@link #divideDetailNo} 和 {@link #amount} 不作为资金归集明细发送，
     * 退款金额由 trade/refund 的 refundAmount 决定。</p>
     */
    private String divBackAccountType;

    /**
     * 已先通过 /divide/back 完成资金归还。此时 /trade/refund 不得再次携带归还明细。
     */
    private Boolean divideBackCompleted;

}
