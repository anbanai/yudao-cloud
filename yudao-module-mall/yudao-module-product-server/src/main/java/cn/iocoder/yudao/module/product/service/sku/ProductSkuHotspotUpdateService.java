package cn.iocoder.yudao.module.product.service.sku;

import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PolarDB 热点行库存更新事务。
 *
 * Hint UPDATE 必须在显式事务中执行，并且是该事务的最后一条 SQL。
 */
@Service
public class ProductSkuHotspotUpdateService {

    @Resource
    private ProductSkuMapper productSkuMapper;

    /**
     * 仅执行一条带 PolarDB Hint 的库存更新 SQL。
     *
     * @param id SKU 编号
     * @param incrCount 库存变更数量，正数增加、负数减少
     * @return 更新条数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int updateStock(Long id, Integer incrCount) {
        if (incrCount > 0) {
            return productSkuMapper.updateStockIncrHotspot(id, incrCount);
        }
        if (incrCount < 0) {
            return productSkuMapper.updateStockDecrHotspot(id, -incrCount);
        }
        return 0;
    }

}
