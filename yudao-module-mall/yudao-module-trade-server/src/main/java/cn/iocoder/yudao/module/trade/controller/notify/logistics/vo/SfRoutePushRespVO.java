package cn.iocoder.yudao.module.trade.controller.notify.logistics.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SfRoutePushRespVO {

    @JsonProperty("return_code")
    private String returnCode;
    @JsonProperty("return_msg")
    private String returnMsg;

    public static SfRoutePushRespVO success() {
        return new SfRoutePushRespVO("0000", "成功");
    }

    public static SfRoutePushRespVO failure() {
        return new SfRoutePushRespVO("1000", "处理失败");
    }
}
