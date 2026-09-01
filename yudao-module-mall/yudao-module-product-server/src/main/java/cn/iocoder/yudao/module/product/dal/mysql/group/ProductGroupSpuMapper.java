package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuRespVO;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductGroupSpuMapper extends BaseMapperX<ProductGroupSpuDO> {

    default List<ProductGroupSpuDO> selectListBySpuId(Long spuId) {
        return selectList(ProductGroupSpuDO::getSpuId, spuId);
    }

    default List<ProductGroupSpuDO> selectListByGroupId(Long groupId) {
        return selectList(ProductGroupSpuDO::getGroupId, groupId);
    }

    default ProductGroupSpuDO selectByGroupIdAndSpuId(Long groupId, Long spuId) {
        return selectOne(ProductGroupSpuDO::getGroupId, groupId, ProductGroupSpuDO::getSpuId, spuId);
    }

    default Long selectCountByGroupId(Long groupId) {
        return selectCount(ProductGroupSpuDO::getGroupId, groupId);
    }

    @Delete("<script>DELETE FROM product_group_spu WHERE tenant_id = #{tenantId} AND group_id = #{groupId} AND spu_id IN <foreach collection='spuIds' item='spuId' open='(' separator=',' close=')'>#{spuId}</foreach></script>")
    int deleteByGroupIdAndSpuIds(@Param("tenantId") Long tenantId, @Param("groupId") Long groupId,
                                 @Param("spuIds") Collection<Long> spuIds);

    @Delete("<script>DELETE FROM product_group_spu WHERE tenant_id = #{tenantId} AND spu_id = #{spuId} AND group_id IN <foreach collection='groupIds' item='groupId' open='(' separator=',' close=')'>#{groupId}</foreach></script>")
    int deleteBySpuIdAndGroupIds(@Param("tenantId") Long tenantId, @Param("spuId") Long spuId,
                                 @Param("groupIds") Collection<Long> groupIds);

    @Delete("DELETE FROM product_group_spu WHERE tenant_id = #{tenantId} AND spu_id = #{spuId}")
    int deleteBySpuId(@Param("tenantId") Long tenantId, @Param("spuId") Long spuId);

    default int updateSort(Long groupId, Long spuId, Integer sort) {
        return update(null, new LambdaUpdateWrapper<ProductGroupSpuDO>()
                .set(ProductGroupSpuDO::getSort, sort)
                .eq(ProductGroupSpuDO::getGroupId, groupId)
                .eq(ProductGroupSpuDO::getSpuId, spuId));
    }

    default PageResult<ProductGroupSpuRespVO> selectAdminSpuPage(ProductGroupSpuPageReqVO reqVO) {
        MPJLambdaWrapperX<ProductGroupSpuDO> query = new MPJLambdaWrapperX<ProductGroupSpuDO>()
                .selectAll(ProductSpuDO.class)
                .selectAs(ProductGroupSpuDO::getSort, ProductGroupSpuRespVO::getGroupSort)
                .innerJoin(ProductSpuDO.class, ProductSpuDO::getId, ProductGroupSpuDO::getSpuId)
                .likeIfPresent(ProductSpuDO::getName, reqVO.getKeyword())
                .eq(ProductGroupSpuDO::getGroupId, reqVO.getGroupId())
                .eqIfPresent(ProductSpuDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProductGroupSpuDO::getSort).orderByDesc(ProductGroupSpuDO::getId);
        return selectJoinPage(reqVO, ProductGroupSpuRespVO.class, query);
    }

    default PageResult<ProductSpuDO> selectAppSpuPage(AppProductGroupSpuPageReqVO reqVO) {
        MPJLambdaWrapperX<ProductGroupSpuDO> query = new MPJLambdaWrapperX<ProductGroupSpuDO>()
                .selectAll(ProductSpuDO.class)
                .innerJoin(ProductSpuDO.class, ProductSpuDO::getId, ProductGroupSpuDO::getSpuId)
                .innerJoin(ProductGroupDO.class, ProductGroupDO::getId, ProductGroupSpuDO::getGroupId)
                .in(ProductGroupSpuDO::getGroupId, reqVO.getGroupIds())
                .eq(ProductGroupDO::getStatus, cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus())
                .eq(ProductSpuDO::getStatus, ProductSpuStatusEnum.ENABLE.getStatus())
                .likeIfPresent(ProductSpuDO::getName, reqVO.getKeyword());
        boolean multi = reqVO.getGroupIds().stream().distinct().count() > 1;
        if (multi) {
            query.distinct();
        }
        appendAppSort(query, reqVO, multi);
        return selectJoinPage(reqVO, ProductSpuDO.class, query);
    }

    static void appendAppSort(MPJLambdaWrapperX<ProductGroupSpuDO> query,
                              AppProductGroupSpuPageReqVO reqVO, boolean multi) {
        boolean asc = Boolean.TRUE.equals(reqVO.getSortAsc());
        if (AppProductGroupSpuPageReqVO.SORT_FIELD_PRICE.equals(reqVO.getSortField())) {
            query.orderBy(true, asc, ProductSpuDO::getPrice);
        } else if (AppProductGroupSpuPageReqVO.SORT_FIELD_SALES_COUNT.equals(reqVO.getSortField())) {
            query.last("ORDER BY (t1.sales_count + t1.virtual_sales_count) "
                    + (asc ? "ASC" : "DESC") + ", t1.sort DESC, t1.id DESC");
            return;
        } else if (AppProductGroupSpuPageReqVO.SORT_FIELD_CREATE_TIME.equals(reqVO.getSortField())) {
            query.orderBy(true, asc, ProductSpuDO::getCreateTime);
        } else if (!multi) {
            query.orderByDesc(ProductGroupSpuDO::getSort).orderByDesc(ProductGroupSpuDO::getId);
            return;
        }
        query.orderByDesc(ProductSpuDO::getSort).orderByDesc(ProductSpuDO::getId);
    }
}
