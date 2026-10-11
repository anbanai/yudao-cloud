package cn.iocoder.yudao.module.system.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商家账号 Response VO")
@Data
public class MerchantUserRespVO {

    @Schema(description = "账号编号", example = "1001")
    private Long id;

    @Schema(description = "商家编号", example = "2001")
    private Long merchantId;

    @Schema(description = "账号", example = "seller")
    private String username;

    @Schema(description = "昵称", example = "Tea Seller")
    private String nickname;

    @Schema(description = "邮箱", example = "seller@example.com")
    private String email;

    @Schema(description = "手机号", example = "15601691300")
    private String mobile;

    @Schema(description = "性别")
    private Integer sex;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "账号状态")
    private Integer status;

    @Schema(description = "最后登录 IP")
    private String loginIp;

    @Schema(description = "最后登录时间")
    private LocalDateTime loginDate;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
