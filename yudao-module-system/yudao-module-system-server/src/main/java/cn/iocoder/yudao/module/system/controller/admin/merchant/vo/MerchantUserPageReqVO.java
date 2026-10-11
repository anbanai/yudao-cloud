package cn.iocoder.yudao.module.system.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 商家账号分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantUserPageReqVO extends PageParam {

    @Schema(description = "账号，模糊匹配", example = "seller")
    private String username;

    @Schema(description = "昵称，模糊匹配", example = "Tea")
    private String nickname;

    @Schema(description = "手机号，模糊匹配", example = "15601691300")
    private String mobile;

    @Schema(description = "商家编号", example = "1001")
    private Long merchantId;

    @Schema(description = "账号状态，参见 CommonStatusEnum", example = "0")
    private Integer status;

    @Schema(description = "创建时间范围", example = "[2026-01-01 00:00:00, 2026-01-31 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
