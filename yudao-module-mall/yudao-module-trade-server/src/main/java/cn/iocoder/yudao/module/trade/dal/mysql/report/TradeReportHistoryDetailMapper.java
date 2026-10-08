package cn.iocoder.yudao.module.trade.dal.mysql.report;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.report.TradeReportHistoryDetailDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeReportHistoryDetailMapper extends BaseMapperX<TradeReportHistoryDetailDO> {
    default List<TradeReportHistoryDetailDO> selectListByHistoryId(Long historyId, Long tenantId) {
        return selectList(new LambdaQueryWrapperX<TradeReportHistoryDetailDO>()
                .eq(TradeReportHistoryDetailDO::getHistoryId, historyId)
                .apply("tenant_id = {0}", tenantId));
    }
}
