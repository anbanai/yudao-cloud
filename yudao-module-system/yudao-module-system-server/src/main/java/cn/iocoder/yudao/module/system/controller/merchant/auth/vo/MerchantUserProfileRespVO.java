package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "商家认证 - 个人资料 Response VO")
@Data
public class MerchantUserProfileRespVO {

    private Long id;
    private Long merchantId;
    private String username;
    private String nickname;
    private String email;
    private String mobile;
    private String avatar;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime createTime;

}
