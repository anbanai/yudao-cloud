package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuRespVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupCountRespVO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductGroupSpuMapper extends BaseMapperX<ProductGroupSpuDO> {

    /** 已持有商品及目标分组锁时读取最新关系，不能复用外层事务的 RR 快照。 */
    default List<ProductGroupSpuDO> selectListBySpuIdForUpdate(Long tenantId, Long spuId) {
        return selectList(new LambdaQueryWrapperX<ProductGroupSpuDO>()
                .eq(ProductGroupSpuDO::getTenantId, tenantId).eq(ProductGroupSpuDO::getSpuId, spuId)
                .orderByAsc(ProductGroupSpuDO::getGroupId).last("FOR UPDATE"));
    }

    default List<ProductGroupSpuDO> selectListByGroupIdForUpdate(Long tenantId, Long groupId) {
        return selectList(new LambdaQueryWrapperX<ProductGroupSpuDO>()
                .eq(ProductGroupSpuDO::getTenantId, tenantId).eq(ProductGroupSpuDO::getGroupId, groupId)
                .orderByAsc(ProductGroupSpuDO::getSpuId).last("FOR UPDATE"));
    }

    @Select("<script>SELECT r.* FROM product_group_spu r"
            + " INNER JOIN product_group g ON g.id=r.group_id AND g.tenant_id=r.tenant_id AND g.deleted=FALSE"
            + " INNER JOIN product_spu s ON s.id=r.spu_id AND s.tenant_id=r.tenant_id AND s.deleted=FALSE"
            + " WHERE r.tenant_id=#{tenantId} AND r.deleted=FALSE AND r.spu_id IN"
            + " <foreach collection='spuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY r.group_id ASC</script>")
    List<ProductGroupSpuDO> selectRelationsBySpuIds(@Param("tenantId") Long tenantId,
                                                  @Param("spuIds") Collection<Long> spuIds);

    @Select("<script>SELECT r.group_id, COUNT(*) AS spu_count,"
            + " SUM(CASE WHEN s.status=1 THEN 1 ELSE 0 END) AS sale_spu_count"
            + " FROM product_group_spu r INNER JOIN product_spu s ON s.id=r.spu_id AND s.tenant_id=r.tenant_id"
            + " WHERE r.tenant_id=#{tenantId} AND r.deleted=FALSE AND s.deleted=FALSE AND r.group_id IN"
            + " <foreach collection='groupIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY r.group_id</script>")
    List<ProductGroupCountRespVO> selectGroupCounts(@Param("tenantId") Long tenantId,
                                                  @Param("groupIds") Collection<Long> groupIds);

    @Insert("<script>INSERT INTO product_group_spu (tenant_id,group_id,spu_id,sort,creator,updater) VALUES"
            + " <foreach collection='relations' item='r' separator=','>"
            + " (#{tenantId},#{r.groupId},#{r.spuId},0,#{operator},#{operator})</foreach>"
            + " ON DUPLICATE KEY UPDATE id=id</script>")
    int insertRelationsIfAbsent(@Param("tenantId") Long tenantId, @Param("operator") String operator,
                                @Param("relations") Collection<ProductGroupSpuDO> relations);

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
                .eq(ProductGroupSpuDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(ProductGroupDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(ProductGroupDO::getStatus, cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus())
                .eq(ProductGroupDO::getStorefrontVisible, true)
                .eq(ProductSpuDO::getStatus, ProductSpuStatusEnum.ENABLE.getStatus())
                .likeIfPresent(ProductSpuDO::getName, reqVO.getKeyword());
        query.apply("t1.tenant_id = {0}", TenantContextHolder.getRequiredTenantId());
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
