package cn.iocoder.yudao.module.pay.api.fund.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 资金状态视图，不返回渠道原报文和安全配置。金额单位分。 */
@Data
public class PayFundStatusRespDTO {
    private Long id;
    private Long payOrderId;
    private Long tradeOrderId;
    private Integer amount;
    private Integer refundPrice;
    private Integer clearingStatus;
    private Integer divideStatus;
    private String lastError;
    private LocalDateTime nextRetryTime;
    private List<Detail> details;
    @Data
    public static class Detail {
        private Long id;
        private Long tradeOrderSubId;
        private Long merchantId;
        private Integer amount;
        private Integer divideAmount;
        private Integer refundedAmount;
        private Integer returnedAmount;
        private Integer status;
        private String channelDetailNo;
    }
}
