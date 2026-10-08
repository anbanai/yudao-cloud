package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuFilterReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 分组运营查询保持独立，原分类分页 Mapper 不接入关系表。 */
@Mapper
public interface ProductGroupOperationsMapper extends BaseMapperX<ProductSpuDO> {

    /** 所有成员写入先按主键升序锁商品，再锁分组，避免与商品编辑的行锁顺序相反。 */
    default List<ProductSpuDO> selectSpusForUpdate(Long tenantId, Collection<Long> spuIds) {
        if (CollUtil.isEmpty(spuIds)) return List.of();
        return selectList(new LambdaQueryWrapperX<ProductSpuDO>()
                .apply("tenant_id = {0}", tenantId).in(ProductSpuDO::getId, spuIds)
                .orderByAsc(ProductSpuDO::getId).last("FOR UPDATE"));
    }

    default PageResult<ProductSpuDO> selectFilterPage(ProductGroupSpuFilterReqVO req, Set<Long> categories, Long tenantId) {
        LambdaQueryWrapperX<ProductSpuDO> query = filterQuery(req, categories, tenantId);
        ProductSpuMapper.appendTabQuery(req.getTabType(), query);
        ProductSpuMapper.appendSortQuery(query, req.getSortField(), req.getSortAsc());
        return selectPage(req, query);
    }

    default Long selectFilterCount(ProductGroupSpuFilterReqVO req, Set<Long> categories, Long tenantId, Integer tab) {
        LambdaQueryWrapperX<ProductSpuDO> query = filterQuery(req, categories, tenantId);
        ProductSpuMapper.appendTabQuery(tab, query);
        return selectCount(query);
    }

    static LambdaQueryWrapperX<ProductSpuDO> filterQuery(ProductGroupSpuFilterReqVO req, Set<Long> categories, Long tenantId) {
        LambdaQueryWrapperX<ProductSpuDO> query = new LambdaQueryWrapperX<ProductSpuDO>()
                .likeIfPresent(ProductSpuDO::getName, req.getName())
                .inIfPresent(ProductSpuDO::getCategoryId, categories)
                .betweenIfPresent(ProductSpuDO::getCreateTime, req.getCreateTime());
        query.apply("tenant_id = {0}", tenantId);
        if (CollUtil.isNotEmpty(req.getGroupIds()) || Boolean.TRUE.equals(req.getUngrouped())) {
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            String sql = "EXISTS (SELECT 1 FROM product_group_spu r INNER JOIN product_group g ON g.id = r.group_id"
                    + " AND g.tenant_id = r.tenant_id WHERE r.spu_id = product_spu.id"
                    + " AND r.tenant_id = {0} AND r.deleted = FALSE AND g.deleted = FALSE";
            if (Boolean.TRUE.equals(req.getUngrouped())) {
                sql = "NOT " + sql;
            } else {
                params.addAll(req.getGroupIds());
                sql += " AND r.group_id IN (" + IntStream.range(1, params.size())
                        .mapToObj(index -> "{" + index + "}").collect(Collectors.joining(",")) + ")";
            }
            query.apply(sql + ")", params.toArray());
        }
        return query;
    }
}
