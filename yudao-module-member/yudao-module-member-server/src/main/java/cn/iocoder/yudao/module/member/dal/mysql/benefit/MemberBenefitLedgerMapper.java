package cn.iocoder.yudao.module.member.dal.mysql.benefit;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitLedgerDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Mapper
public interface MemberBenefitLedgerMapper extends BaseMapperX<MemberBenefitLedgerDO> {

    default boolean existsByBizId(String bizId, Integer eventType) {
        return selectCount(new LambdaQueryWrapper<MemberBenefitLedgerDO>()
                .eq(MemberBenefitLedgerDO::getBizId, bizId)
                .eq(MemberBenefitLedgerDO::getEventType, eventType)) > 0;
    }
}
