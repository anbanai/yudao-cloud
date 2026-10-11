package cn.iocoder.yudao.module.infra.controller.merchant.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "商家资质上传 Response VO")
@Data
@Accessors(chain = true)
public class MerchantQualificationUploadRespVO {

    @Schema(description = "私有文件编号")
    private Long fileId;

    @Schema(description = "15 分钟有效的预览地址")
    private String previewUrl;

    @Schema(description = "上传业务场景")
    private String scene;

}
