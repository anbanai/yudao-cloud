package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TradeOrderSubRespVO {
    private Long id;
    private Long orderId;
    private String no;
    private Long merchantId;
    private String merchantName;
    private Integer status;
    private Integer productCount;
    private Integer totalPrice;
    private Integer discountPrice;
    private Integer couponPrice;
    private Integer pointPrice;
    private Integer vipPrice;
    private Integer adjustPrice;
    private Integer deliveryPrice;
    private Integer payPrice;
    private Integer deliveryType;
    private Long logisticsId;
    private String logisticsNo;
    private LocalDateTime deliveryTime;
    private LocalDateTime receiveTime;
    private Integer refundStatus;
    private Integer afterSaleStatus;
}
