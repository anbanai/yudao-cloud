package cn.iocoder.yudao.module.member.dal.mysql.importing;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanPointSnapshotDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberYouzanPointSnapshotMapper extends BaseMapperX<MemberYouzanPointSnapshotDO> {

    default MemberYouzanPointSnapshotDO findSource(Long merchantId, String mobileHash, boolean lock) {
        return selectOne(new LambdaQueryWrapper<MemberYouzanPointSnapshotDO>()
                .eq(MemberYouzanPointSnapshotDO::getMerchantId, merchantId)
                .eq(MemberYouzanPointSnapshotDO::getMobileHash, mobileHash).last(lock, "FOR UPDATE"));
    }

    default List<MemberYouzanPointSnapshotDO> selectBySnapshotHash(String snapshotHash) {
        return selectList(new LambdaQueryWrapper<MemberYouzanPointSnapshotDO>()
                .eq(MemberYouzanPointSnapshotDO::getSnapshotHash, snapshotHash));
    }
}
