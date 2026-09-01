package cn.iocoder.yudao.module.trade.controller.internal.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Data
@Accessors(chain = true)
public class PrintBridgeStatusReqVO {

    @NotBlank
    private String event;
    @NotBlank
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("job_id")
    private String jobId;
    @NotBlank
    private String status;
    private String message;
    @JsonProperty("occurred_at")
    private OffsetDateTime occurredAt;
}
