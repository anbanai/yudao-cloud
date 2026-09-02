package cn.iocoder.yudao.module.trade.controller.notify.logistics.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfRoutePushReqVO {

    @JsonProperty("Body")
    private Body body;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("WaybillRoute")
        private List<Route> waybillRoute;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route {
        private String mailno;
        private String acceptAddress;
        private String reasonName;
        private String orderid;
        private String acceptTime;
        private String remark;
        private String opCode;
        private String id;
        private String reasonCode;
        private String firstStatusCode;
        private String firstStatusName;
        private String secondaryStatusCode;
        private String secondaryStatusName;
    }
}
