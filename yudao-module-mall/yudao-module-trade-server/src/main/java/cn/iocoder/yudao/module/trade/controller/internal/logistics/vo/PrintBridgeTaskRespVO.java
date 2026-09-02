package cn.iocoder.yudao.module.trade.controller.internal.logistics.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PrintBridgeTaskRespVO {

    private String type;
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("job_id")
    private String jobId;
    private String format;
    @JsonProperty("printer_name")
    private String printerName;
    @JsonProperty("file_url")
    private String fileUrl;
    private Integer copies;
    private Paper paper;

    @Data
    @Accessors(chain = true)
    public static class Paper {
        @JsonProperty("width_mm")
        private Integer widthMm;
        @JsonProperty("height_mm")
        private Integer heightMm;
    }
}
