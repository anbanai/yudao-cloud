package cn.iocoder.yudao.module.member.controller.app.benefit.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;
@Data
@Accessors(chain = true)
public class AppMemberBenefitRespVO {
    private Long accountId;
    private String code;
    private String name;
    private Integer type;
    private Integer actionType;
    private String description;
    private String icon;
    private Integer quantity;
    private Integer availableQuantity;
    private Integer usedQuantity;
    private Integer claimType;
    private Integer claimStatus;
    private Integer status;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    @Schema(description = "是否可以领取") private Boolean claimable;
}
