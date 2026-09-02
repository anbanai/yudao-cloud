package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import cn.iocoder.yudao.module.trade.enums.logistics.SfLabelSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LogisticsDiagnosticPayloadReqVO {

    @NotNull
    private Integer paperWidthMm = 76;
    @NotNull
    private Integer paperHeightMm = 130;

    @JsonIgnore
    @AssertTrue(message = "测试纸张只支持 76x130 或 100x150 mm")
    public boolean isPaperSpecSupported() {
        return SfLabelSpec.supports(paperWidthMm, paperHeightMm, 203);
    }
}
