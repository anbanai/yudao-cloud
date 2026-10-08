package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台 - 商品分组运营筛选 Request VO")
public class ProductGroupSpuFilterReqVO extends ProductSpuPageReqVO {
    @Size(max = 15)
    private List<@NotNull @Positive Long> groupIds;
    private Boolean ungrouped;

    @AssertTrue(message = "未分组和指定分组不能同时筛选")
    @JsonIgnore
    public boolean isGroupFilterValid() {
        return !Boolean.TRUE.equals(ungrouped) || groupIds == null || groupIds.isEmpty();
    }
}
