package cn.iocoder.yudao.module.infra.controller.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Schema(description = "商城基础 - 小程序分享封面配置")
public class ShareCoverConfigRespVO {

    @Schema(description = "系统默认封面")
    @JsonProperty("default")
    private String defaultCover = "";

    @Schema(description = "首页封面")
    private String home = "";

    @Schema(description = "个人中心封面")
    private String user = "";

    @Schema(description = "自定义页面封面，key 为页面标识")
    private Map<String, String> pages = new LinkedHashMap<>();
}
