package cn.iocoder.yudao.module.member.dal.mysql.importing;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanImportBatchDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberYouzanImportBatchMapper extends BaseMapperX<MemberYouzanImportBatchDO> {

    default MemberYouzanImportBatchDO selectResult(Long id) {
        return selectOne(new LambdaQueryWrapper<MemberYouzanImportBatchDO>()
                .select(MemberYouzanImportBatchDO.class, field -> !"encryptedPayload".equals(field.getProperty()))
                .eq(MemberYouzanImportBatchDO::getId, id));
    }

    default MemberYouzanImportBatchDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<MemberYouzanImportBatchDO>()
                .eq(MemberYouzanImportBatchDO::getId, id).last("FOR UPDATE"));
    }
}
