package cn.iocoder.yudao.module.promotion.controller.app.coupon.vo.template;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "首页新人券配置及领取资格")
@Data
public class AppNewcomerCouponRespVO {

    @Schema(description = "优惠券模板编号")
    private Long templateId;
    @Schema(description = "优惠券名称")
    private String name;
    @Schema(description = "优惠券说明")
    private String description;
    @Schema(description = "满减门槛，单位分")
    private Integer usePrice;
    @Schema(description = "优惠类型")
    private Integer discountType;
    @Schema(description = "折扣百分比")
    private Integer discountPercent;
    @Schema(description = "优惠金额，单位分")
    private Integer discountPrice;
    @Schema(description = "折扣上限，单位分")
    private Integer discountLimitPrice;
    @Schema(description = "商品范围")
    private Integer productScope;
    @Schema(description = "商品范围编号")
    private List<Long> productScopeValues;
    @Schema(description = "有效期类型")
    private Integer validityType;
    @Schema(description = "固定有效期开始")
    private LocalDateTime validStartTime;
    @Schema(description = "固定有效期结束")
    private LocalDateTime validEndTime;
    @Schema(description = "领取后有效期开始天数")
    private Integer fixedStartTerm;
    @Schema(description = "领取后有效期结束天数")
    private Integer fixedEndTerm;
    @Schema(description = "是否展示首页入口")
    private Boolean showEntry;
    @Schema(description = "当前是否可领取")
    private Boolean canTake;
    @Schema(description = "不可领取原因代码")
    private String reasonCode;
    @Schema(description = "不可领取原因")
    private String reason;
}
