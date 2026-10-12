package cn.iocoder.yudao.module.product.dal.mysql.sku;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapperX<ProductSkuDO> {

    /**
     * 查询商品 SKU（包含已删除）
     * 注意：使用 @Results 手动指定 typeHandler，否则 @Select 不会应用 autoResultMap，properties 字段无法解析 JSON
     */
    @Select("SELECT * FROM product_sku WHERE id = #{id}")
    @Results({
            @Result(column = "properties", property = "properties", typeHandler = JacksonTypeHandler.class),
    })
    ProductSkuDO selectByIdIncludeDeleted(@Param("id") Long id);

    default List<ProductSkuDO> selectListBySpuId(Long spuId) {
        return selectList(ProductSkuDO::getSpuId, spuId);
    }

    /**
     * 查询交易链路可见租户范围内的 SKU。交易链路需要显式保留平台全局租户和当前租户。
     */
    default List<ProductSkuDO> selectAvailableByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                .in(ProductSkuDO::getId, ids)
                .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId())
                .apply("spu_id IN (SELECT id FROM product_spu WHERE status = {0} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2}) AND merchant_id IN "
                                + "(SELECT id FROM product_merchant WHERE status = {3} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2})))",
                        ProductSpuStatusEnum.ENABLE.getStatus(), false, TenantContextHolder.getRequiredTenantId(),
                        ProductMerchantStatusEnum.APPROVED.getStatus())));
    }

    /** 查询库存汇总使用的 SKU，保留已冻结商家的历史 SKU，但限定平台全局与当前租户。 */
    default List<ProductSkuDO> selectByIdsInTenantScope(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                .in(ProductSkuDO::getId, ids)
                .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId())));
    }

    default List<ProductSkuDO> selectAvailableListBySpuId(Long spuId) {
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                .eq(ProductSkuDO::getSpuId, spuId)
                .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId())
                .apply("spu_id IN (SELECT id FROM product_spu WHERE status = {0} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2}) AND merchant_id IN "
                                + "(SELECT id FROM product_merchant WHERE status = {3} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2})))",
                        ProductSpuStatusEnum.ENABLE.getStatus(), false, TenantContextHolder.getRequiredTenantId(),
                        ProductMerchantStatusEnum.APPROVED.getStatus())));
    }

    default List<ProductSkuDO> selectAvailableListBySpuId(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                .in(ProductSkuDO::getSpuId, spuIds)
                .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId())
                .apply("spu_id IN (SELECT id FROM product_spu WHERE status = {0} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2}) AND merchant_id IN "
                                + "(SELECT id FROM product_merchant WHERE status = {3} AND deleted = {1} "
                                + "AND tenant_id IN (0, {2})))",
                        ProductSpuStatusEnum.ENABLE.getStatus(), false, TenantContextHolder.getRequiredTenantId(),
                        ProductMerchantStatusEnum.APPROVED.getStatus())));
    }

    default List<ProductSkuDO> selectListBySpuId(Collection<Long> spuIds) {
        return selectList(ProductSkuDO::getSpuId, spuIds);
    }

    default void deleteBySpuId(Long spuId) {
        delete(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getSpuId, spuId));
    }

    /**
     * 更新 SKU 库存（增加）、销量（减少）
     *
     * @param id        编号
     * @param incrCount 增加库存（正数）
     */
    default void updateStockIncr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount > 0);
        TenantUtils.executeIgnore(() -> {
            LambdaUpdateWrapper<ProductSkuDO> lambdaUpdateWrapper = new LambdaUpdateWrapper<ProductSkuDO>()
                    .setSql(" stock = stock + " + incrCount
                            + ", sales_count = sales_count - " + incrCount)
                    .eq(ProductSkuDO::getId, id)
                    .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId());
            update(null, lambdaUpdateWrapper);
        });
    }

    /**
     * 更新 SKU 库存（减少）、销量（增加）
     *
     * @param id        编号
     * @param incrCount 减少库存（负数）
     * @return 更新条数
     */
    default int updateStockDecr(Long id, Integer incrCount) {
        Assert.isTrue(incrCount < 0);
        int count = -incrCount; // 取正
        return TenantUtils.executeIgnore(() -> {
            LambdaUpdateWrapper<ProductSkuDO> updateWrapper = new LambdaUpdateWrapper<ProductSkuDO>()
                    .setSql(" stock = stock - " + count
                            + ", sales_count = sales_count + " + count)
                    .eq(ProductSkuDO::getId, id)
                    .ge(ProductSkuDO::getStock, count)
                    .apply("product_sku.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId());
            return update(null, updateWrapper);
        });
    }

    /**
     * PolarDB 热点行优化：更新 SKU 库存（增加）。
     *
     * @param id        编号
     * @param incrCount 增加库存数量（正数）
     * @return 更新条数
     */
    @Update("UPDATE /*+ COMMIT_ON_SUCCESS ROLLBACK_ON_FAIL */ product_sku "
            + "SET stock = stock + #{incrCount}, sales_count = sales_count - #{incrCount} "
            + "WHERE id = #{id} AND tenant_id IN (0, #{tenantId})")
    int updateStockIncrHotspot(@Param("id") Long id, @Param("incrCount") Integer incrCount,
                               @Param("tenantId") Long tenantId);

    /**
     * PolarDB 热点行优化：更新 SKU 库存（减少）。
     *
     * @param id    编号
     * @param count 减少库存数量（正数）
     * @return 更新条数
     */
    @Update("UPDATE /*+ COMMIT_ON_SUCCESS ROLLBACK_ON_FAIL */ product_sku "
            + "SET stock = stock - #{count}, sales_count = sales_count + #{count} "
            + "WHERE id = #{id} AND stock >= #{count} AND tenant_id IN (0, #{tenantId})")
    int updateStockDecrHotspot(@Param("id") Long id, @Param("count") Integer count,
                               @Param("tenantId") Long tenantId);

}
