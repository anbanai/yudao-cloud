package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SfLogisticsAccountRespVO {
    private Long id;
    private String name;
    private String partnerIdMasked;
    private Boolean defaultFlag;
    private Integer status;
}
