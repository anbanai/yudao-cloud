package cn.iocoder.yudao.module.system.api.social.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信物流助手打印员绑定请求。
 */
@Data
public class SocialWxaExpressPrinterUpdateReqDTO {

    @NotBlank(message = "打印员 openid 不能为空")
    private String openid;
    @NotBlank(message = "打印员更新类型不能为空")
    private String updateType;
    private String tagidList;
}
