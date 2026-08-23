package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 微信物流助手配置 Request VO")
@Data
public class WechatLogisticsConfigSaveReqVO {

    @Schema(description = "微信小程序用户类型", example = "1")
    private Integer userType = 1;
    @NotBlank(message = "快递公司编号不能为空")
    private String deliveryId = "SF";
    @NotBlank(message = "微信物流 biz_id 不能为空")
    private String bizId;
    @NotNull(message = "服务类型不能为空")
    private Integer serviceType;
    @NotBlank(message = "服务名称不能为空")
    private String serviceName;
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled = true;

    @NotBlank(message = "发件人姓名不能为空")
    private String senderName;
    private String senderTel;
    @NotBlank(message = "发件人手机号不能为空")
    private String senderMobile;
    private String senderCompany;
    private String senderPostCode;
    private String senderCountry = "中国";
    @NotBlank(message = "发件人省份不能为空")
    private String senderProvince;
    @NotBlank(message = "发件人城市不能为空")
    private String senderCity;
    @NotBlank(message = "发件人区县不能为空")
    private String senderArea;
    @NotBlank(message = "发件人详细地址不能为空")
    private String senderAddress;

    @NotNull(message = "默认包裹重量不能为空")
    @DecimalMin(value = "0.01", message = "默认包裹重量必须大于 0")
    private BigDecimal defaultWeight;
    @NotNull(message = "默认包裹长度不能为空")
    @DecimalMin(value = "0.01", message = "默认包裹长度必须大于 0")
    private BigDecimal defaultSpaceLength;
    @NotNull(message = "默认包裹宽度不能为空")
    @DecimalMin(value = "0.01", message = "默认包裹宽度必须大于 0")
    private BigDecimal defaultSpaceWidth;
    @NotNull(message = "默认包裹高度不能为空")
    @DecimalMin(value = "0.01", message = "默认包裹高度必须大于 0")
    private BigDecimal defaultSpaceHeight;
}
