package cn.iocoder.yudao.module.product.service.sku;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.product.convert.sku.ProductSkuConvert.INSTANCE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SKU_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SKU_STOCK_NOT_ENOUGH;

/**
 * 商品 SKU 库存更新实现。
 *
 * 将旧事务路径和 PolarDB 热点行路径分开，确保热点行 Hint SQL 使用独立事务。
 */
@Service
public class ProductSkuStockUpdateService {

    @Resource
    private ProductSkuMapper productSkuMapper;

    @Resource
    private ProductSkuHotspotUpdateService productSkuHotspotUpdateService;

    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSpuService productSpuService;

    /**
     * 原有库存更新路径：SKU 与 SPU 在同一个事务中更新。
     *
     * @param updateStockReqDTO 库存变更请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLegacy(ProductSkuUpdateStockReqDTO updateStockReqDTO) {
        updateStockReqDTO.getItems().forEach(item -> {
            if (item.getIncrCount() > 0) {
                productSkuMapper.updateStockIncr(item.getId(), item.getIncrCount());
            } else if (item.getIncrCount() < 0) {
                int updateStockCount = productSkuMapper.updateStockDecr(item.getId(), item.getIncrCount());
                if (updateStockCount == 0) {
                    throw exception(SKU_STOCK_NOT_ENOUGH);
                }
            }
        });

        updateSpuStock(updateStockReqDTO);
    }

    /**
     * PolarDB 热点行更新路径。
     *
     * 每条 Hint UPDATE 都由独立事务方法提交，不能加入调用方事务。
     * 多 SKU 请求由上层回退到 {@link #updateLegacy(ProductSkuUpdateStockReqDTO)}。
     * SKU 提交与 SPU 汇总更新不再是同一事务，SPU 更新失败时需要通过库存对账修复。
     *
     * @param updateStockReqDTO 单 SKU 库存变更请求
     */
    public void updateHotspot(ProductSkuUpdateStockReqDTO updateStockReqDTO) {
        ProductSkuUpdateStockReqDTO.Item item = updateStockReqDTO.getItems().get(0);
        if (item.getIncrCount() == 0) {
            return;
        }
        int updateStockCount = productSkuHotspotUpdateService.updateStock(item.getId(), item.getIncrCount());
        if (item.getIncrCount() < 0 && updateStockCount == 0) {
            throw exception(SKU_STOCK_NOT_ENOUGH);
        }
        if (updateStockCount == 0) {
            throw exception(SKU_NOT_EXISTS);
        }

        // SKU 已经通过 COMMIT_ON_SUCCESS 独立提交，SPU 汇总只能在后续短事务中更新。
        updateSpuStock(updateStockReqDTO);
    }

    private void updateSpuStock(ProductSkuUpdateStockReqDTO updateStockReqDTO) {
        List<ProductSkuDO> skus = productSkuMapper.selectByIds(
                convertSet(updateStockReqDTO.getItems(), ProductSkuUpdateStockReqDTO.Item::getId));
        if (CollUtil.isEmpty(skus)) {
            return;
        }
        Map<Long, Integer> spuStockIncrCounts = INSTANCE.convertSpuStockMap(updateStockReqDTO.getItems(), skus);
        productSpuService.updateSpuStock(spuStockIncrCounts);
    }

}
