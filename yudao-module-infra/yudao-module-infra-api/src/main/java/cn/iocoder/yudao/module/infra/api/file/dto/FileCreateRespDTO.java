package cn.iocoder.yudao.module.infra.api.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 文件创建 Response DTO")
@Data
public class FileCreateRespDTO {

    @Schema(description = "文件编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "不含临时签名的文件地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

}
