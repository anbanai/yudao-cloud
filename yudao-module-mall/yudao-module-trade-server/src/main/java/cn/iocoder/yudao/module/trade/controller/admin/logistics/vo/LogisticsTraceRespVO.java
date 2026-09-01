package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LogisticsTraceRespVO {
    private Long id;
    private String status;
    private String content;
    private String location;
    private LocalDateTime operateTime;
}
