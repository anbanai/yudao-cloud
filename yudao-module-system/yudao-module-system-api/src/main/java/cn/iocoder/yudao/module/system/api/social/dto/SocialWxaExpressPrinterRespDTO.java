package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;

/**
 * 微信物流助手打印员信息。
 */
@Data
public class SocialWxaExpressPrinterRespDTO {

    private Integer count;
    private List<String> openid;
    private List<String> tagidList;
}
