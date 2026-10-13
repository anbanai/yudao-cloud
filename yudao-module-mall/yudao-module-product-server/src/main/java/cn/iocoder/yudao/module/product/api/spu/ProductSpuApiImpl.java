package cn.iocoder.yudao.module.product.api.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商品 SPU API 接口实现类
 *
 * @author LeeYan9
 * @since 2022-09-06
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class ProductSpuApiImpl implements ProductSpuApi {

    @Resource
    private ProductSpuService spuService;

    @Resource
    private ProductMerchantService merchantService;

    @Override
    public CommonResult<List<ProductSpuRespDTO>> getSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.getSpuList(ids);
        return success(convertWithMerchant(spus));
    }

    @Override
    public CommonResult<List<ProductSpuRespDTO>> getAvailableSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.getAvailableSpuList(ids);
        return success(convertWithMerchant(spus));
    }

    @Override
    public CommonResult<List<ProductSpuRespDTO>> validateSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.validateSpuList(ids);
        return success(convertWithMerchant(spus));
    }

    @Override
    public CommonResult<ProductSpuRespDTO> getSpu(Long id) {
        ProductSpuDO spu = spuService.getSpu(id);
        return success(spu == null ? null : convertWithMerchant(java.util.List.of(spu)).get(0));
    }

    private List<ProductSpuRespDTO> convertWithMerchant(List<ProductSpuDO> spus) {
        List<ProductSpuRespDTO> result = BeanUtils.toBean(spus, ProductSpuRespDTO.class);
        result.forEach(dto -> {
            if (dto.getMerchantId() != null) {
                // 交易价格快照必须能解析平台全局商家；普通租户查询会把 tenant_id=0 的商家过滤掉。
                var merchant = merchantService.getMerchantForTrade(dto.getMerchantId());
                if (merchant != null) {
                    dto.setMerchantName(merchant.getStoreName() != null ? merchant.getStoreName() : merchant.getName());
                }
            }
        });
        return result;
    }

}
