package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Deliberately no Lombok toString: handoff and login codes are credentials. */
@Getter
@Setter
public class AppAuthBridgeLoginReqVO {
    @NotBlank @Size(max=128) private String handoffCode;
    @NotBlank @Size(max=256) private String loginCode;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,128}") private String requestId;
    @NotBlank @Size(max=64) private String sourceAppId;
}
