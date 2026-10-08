package cn.iocoder.yudao.module.trade.dal.dataobject.report;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_report_history")
@KeySequence("trade_report_history_seq")
public class TradeReportHistoryDO extends TenantBaseDO {
    private Long id;
    private String reportType;
    private String filtersJson;
    private Long orderCount;
    private Integer productTypeCount;
    private Integer productCount;
    private Long amount;
}
