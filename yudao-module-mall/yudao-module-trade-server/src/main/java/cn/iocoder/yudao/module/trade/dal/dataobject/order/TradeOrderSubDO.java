package cn.iocoder.yudao.module.trade.dal.dataobject.order;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** 商家履约订单。一个平台主订单按商家拆分出一条或多条记录。 */
@TableName("trade_order_sub")
@KeySequence("trade_order_sub_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderSubDO extends BaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private String no;
    private Long userId;
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
