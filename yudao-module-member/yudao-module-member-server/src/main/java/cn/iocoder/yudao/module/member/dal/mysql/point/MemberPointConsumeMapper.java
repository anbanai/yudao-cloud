package cn.iocoder.yudao.module.member.dal.mysql.point;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointConsumeDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;

@Mapper
public interface MemberPointConsumeMapper extends BaseMapperX<MemberPointConsumeDO> {
    default boolean existsByBizId(Long userId, String bizId) {
        return selectCount(new LambdaQueryWrapperX<MemberPointConsumeDO>()
                .eq(MemberPointConsumeDO::getUserId, userId)
                .eq(MemberPointConsumeDO::getBizId, bizId)) > 0;
    }

    default List<MemberPointConsumeDO> selectUnrestored(Long userId, String bizId) {
        return selectList(new LambdaQueryWrapperX<MemberPointConsumeDO>()
                .eq(MemberPointConsumeDO::getUserId, userId)
                .eq(MemberPointConsumeDO::getBizId, bizId)
                .apply("COALESCE(restored, 0) < quantity")
                .orderByAsc(MemberPointConsumeDO::getId));
    }

    default int markRestored(Long id, int quantity) {
        return update(null, new LambdaUpdateWrapper<MemberPointConsumeDO>()
                .eq(MemberPointConsumeDO::getId, id)
                .apply("COALESCE(restored, 0) + " + quantity + " <= quantity")
                .setSql("restored = COALESCE(restored, 0) + " + quantity));
    }
}
