package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "商家认证 - 个人资料 Response VO")
@Data
public class MerchantUserProfileRespVO {

    private Long id;
    private Long merchantId;

    @Schema(description = "首次入驻可选平台；已选定平台或关闭多租户时为空值")
    private List<Platform> onboardingPlatforms;

    public record Platform(Long id, String name, boolean recommended) {
    }

    private String username;
    private String nickname;
    private String email;
    private String mobile;
    private String avatar;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime createTime;

}
