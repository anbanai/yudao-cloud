package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductGroupMapper extends BaseMapperX<ProductGroupDO> {
    default List<ProductGroupDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<ProductGroupDO>().orderByAsc(ProductGroupDO::getSort).orderByAsc(ProductGroupDO::getId));
    }

    default List<ProductGroupDO> selectPublicByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<ProductGroupDO>()
                .in(ProductGroupDO::getId, ids)
                .eq(ProductGroupDO::getStatus, 0)
                .eq(ProductGroupDO::getStorefrontVisible, true)
                .orderByAsc(ProductGroupDO::getSort).orderByAsc(ProductGroupDO::getId));
    }

    default List<ProductGroupDO> selectPublicList() {
        return selectList(new LambdaQueryWrapperX<ProductGroupDO>()
                .eq(ProductGroupDO::getStatus, 0).eq(ProductGroupDO::getStorefrontVisible, true)
                .orderByAsc(ProductGroupDO::getSort).orderByAsc(ProductGroupDO::getId));
    }
}
