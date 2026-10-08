package cn.iocoder.yudao.module.trade.dal.mysql.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.controller.admin.report.vo.TradeReportHistoryPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.report.TradeReportHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeReportHistoryMapper extends BaseMapperX<TradeReportHistoryDO> {
    default PageResult<TradeReportHistoryDO> selectPage(TradeReportHistoryPageReqVO reqVO, Long tenantId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeReportHistoryDO>()
                .eqIfPresent(TradeReportHistoryDO::getReportType, reqVO.getReportType())
                .apply("tenant_id = {0}", tenantId)
                .orderByDesc(TradeReportHistoryDO::getId));
    }
}
