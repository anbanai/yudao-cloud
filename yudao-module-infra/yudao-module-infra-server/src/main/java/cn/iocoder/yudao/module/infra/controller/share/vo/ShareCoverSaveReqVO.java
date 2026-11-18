package cn.iocoder.yudao.module.infra.controller.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Schema(description = "商城基础 - 保存小程序分享封面 Request VO")
public class ShareCoverSaveReqVO {

    @Schema(description = "系统默认封面")
    @Size(max = 500, message = "默认封面地址不能超过 500 个字符")
    @JsonProperty("default")
    private String defaultCover = "";

    @Schema(description = "首页封面")
    @Size(max = 500, message = "首页封面地址不能超过 500 个字符")
    private String home = "";

    @Schema(description = "个人中心封面")
    @Size(max = 500, message = "个人中心封面地址不能超过 500 个字符")
    private String user = "";

    @Valid
    @Size(max = 100, message = "自定义页面封面最多配置 100 项")
    private Map<@Size(max = 200, message = "页面标识不能超过 200 个字符") String,
            @Size(max = 500, message = "封面地址不能超过 500 个字符") String> pages = new LinkedHashMap<>();
}
