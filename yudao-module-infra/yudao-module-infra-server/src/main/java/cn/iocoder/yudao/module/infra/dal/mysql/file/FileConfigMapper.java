package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FileConfigMapper extends BaseMapperX<FileConfigDO> {

    default PageResult<FileConfigDO> selectPage(FileConfigPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileConfigDO>()
                .likeIfPresent(FileConfigDO::getName, reqVO.getName())
                .eqIfPresent(FileConfigDO::getStorage, reqVO.getStorage())
                .betweenIfPresent(FileConfigDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FileConfigDO::getId));
    }

    default FileConfigDO selectByMaster() {
        return selectByMaster(false);
    }

    default FileConfigDO selectByMaster(boolean privateStorage) {
        return selectOne(new LambdaQueryWrapperX<FileConfigDO>()
                .eq(FileConfigDO::getMaster, true)
                .eq(FileConfigDO::getPrivateStorage, privateStorage));
    }

    default FileConfigDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(FileConfigDO::getId, id);
    }

    default FileConfigDO selectByMasterForUpdate(boolean privateStorage) {
        return selectOneForUpdate(new LambdaQueryWrapperX<FileConfigDO>()
                .eq(FileConfigDO::getMaster, true)
                .eq(FileConfigDO::getPrivateStorage, privateStorage));
    }

    /**
     * 按固定 ID 顺序锁定同一访问类型的全部配置，避免与批量删除产生跨节点死锁。
     */
    default List<FileConfigDO> selectByPrivateForUpdate(boolean privateStorage) {
        return selectList(new LambdaQueryWrapperX<FileConfigDO>()
                .eq(FileConfigDO::getPrivateStorage, privateStorage)
                .orderByAsc(FileConfigDO::getId)
                .last("FOR UPDATE"));
    }

    /**
     * 使用 MySQL/MariaDB 共享行锁查询默认配置。调用方必须处于事务中。
     */
    default FileConfigDO selectByMasterForShare(boolean privateStorage) {
        return selectOne(new LambdaQueryWrapperX<FileConfigDO>()
                .apply("master_group = {0}", privateStorage ? 1 : 0)
                .last("LOCK IN SHARE MODE"));
    }

    default List<FileConfigDO> selectByIdsForUpdate(List<Long> ids) {
        return selectList(new LambdaQueryWrapperX<FileConfigDO>()
                .in(FileConfigDO::getId, ids)
                .orderByAsc(FileConfigDO::getId)
                .last("FOR UPDATE"));
    }

    default void updateMasterByPrivate(boolean master, boolean privateStorage) {
        update(new FileConfigDO().setMaster(master), new LambdaUpdateWrapper<FileConfigDO>()
                .eq(FileConfigDO::getPrivateStorage, privateStorage));
    }

}
