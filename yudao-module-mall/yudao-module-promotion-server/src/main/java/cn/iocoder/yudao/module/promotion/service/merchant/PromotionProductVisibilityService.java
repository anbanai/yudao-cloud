package cn.iocoder.yudao.module.promotion.service.merchant;

import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Resolves available products before pagination without joining another service's database. */
@Service
public class PromotionProductVisibilityService {
    @Resource
    private ProductSpuApi productSpuApi;

    public List<Long> getAvailableSpuIds(List<Long> candidates) {
        List<Long> ids = new ArrayList<>();
        // Bound Feign GET request length for large activity catalogs.
        for (int offset = 0; offset < candidates.size(); offset += 200) {
            ids.addAll(productSpuApi.getAvailableSpuList(candidates.subList(offset, Math.min(offset + 200, candidates.size())))
                    .getCheckedData().stream().map(ProductSpuRespDTO::getId).toList());
        }
        return ids;
    }
}
