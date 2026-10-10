package cn.iocoder.yudao.module.pay.api.fund.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 分别展示退款和归还状态。 */
@Data
public class PayRefundFundStatusRespDTO {
    private Long refundId;
    private List<Detail> details;
    @Data
    public static class Detail {
        private Long id;
        private Long fundDetailId;
        private Long afterSaleId;
        private Long merchantId;
        private Integer amount;
        private Integer refundStatus;
        private Integer divideBackStatus;
        private String lastError;
        private LocalDateTime nextRetryTime;
    }
}
