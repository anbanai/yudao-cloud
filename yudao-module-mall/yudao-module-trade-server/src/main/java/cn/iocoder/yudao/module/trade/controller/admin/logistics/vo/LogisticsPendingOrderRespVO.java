package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LogisticsPendingOrderRespVO {
    private Long id;
    private String no;
    private String receiverName;
    private String receiverMobile;
    private Integer productCount;
    private Integer payPrice;
    private LocalDateTime createTime;
}
