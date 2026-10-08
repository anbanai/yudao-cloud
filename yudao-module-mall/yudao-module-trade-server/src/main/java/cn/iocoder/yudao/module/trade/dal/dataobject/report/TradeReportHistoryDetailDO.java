package cn.iocoder.yudao.module.trade.dal.dataobject.report;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_report_history_detail")
@KeySequence("trade_report_history_detail_seq")
public class TradeReportHistoryDetailDO extends TenantBaseDO {
    private Long id;
    private Long historyId;
    private Long spuId;
    private Long skuId;
    private String productName;
    private String picUrl;
    private String barCode;
    private String properties;
    private Integer quantity;
    private Long amount;
    private Integer orderCount;
}
