package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Collection;

@Mapper
public interface ProductGroupMapper extends BaseMapperX<ProductGroupDO> {

    /** 与商品成员新增共用分组锁；删除必须在拿到锁后用当前读检查成员。 */
    default List<ProductGroupDO> selectGroupsForUpdate(Long tenantId, Collection<Long> groupIds) {
        if (groupIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProductGroupDO>()
                .eq(ProductGroupDO::getTenantId, tenantId).in(ProductGroupDO::getId, groupIds)
                .orderByAsc(ProductGroupDO::getId).last("FOR UPDATE"));
    }

    default ProductGroupDO selectByName(String name) {
        return selectOne(ProductGroupDO::getName, name);
    }

    default PageResult<ProductGroupDO> selectPage(ProductGroupPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductGroupDO>()
                .likeIfPresent(ProductGroupDO::getName, reqVO.getName())
                .eqIfPresent(ProductGroupDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProductGroupDO::getStorefrontVisible, reqVO.getStorefrontVisible())
                .orderByDesc(ProductGroupDO::getSort).orderByDesc(ProductGroupDO::getId));
    }

    default List<ProductGroupDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<ProductGroupDO>()
                .eq(ProductGroupDO::getStatus, status)
                .orderByDesc(ProductGroupDO::getSort).orderByDesc(ProductGroupDO::getId));
    }
}
