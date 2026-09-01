package cn.iocoder.yudao.module.product.controller.app.group.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户 App - 商品分组商品分页 Request VO")
public class AppProductGroupSpuPageReqVO extends PageParam {
    public static final String SORT_FIELD_PRICE = "price";
    public static final String SORT_FIELD_SALES_COUNT = "salesCount";
    public static final String SORT_FIELD_CREATE_TIME = "createTime";

    @NotEmpty(message = "商品分组不能为空")
    @Size(max = 15, message = "最多选择 15 个商品分组")
    private List<Long> groupIds;
    private String keyword;
    private String sortField;
    private Boolean sortAsc;

    @AssertTrue(message = "排序字段不合法")
    public boolean isSortFieldValid() {
        return sortField == null || sortField.isBlank()
                || SORT_FIELD_PRICE.equals(sortField) || SORT_FIELD_SALES_COUNT.equals(sortField)
                || SORT_FIELD_CREATE_TIME.equals(sortField);
    }

    @AssertTrue(message = "每页最多展示 50 个商品")
    public boolean isPageSizeValid() {
        return getPageSize() == null || getPageSize() <= 50;
    }
}
