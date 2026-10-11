package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMerchantCatalogControllerTest {

    private static final Long MERCHANT_ID = 10L;
    private static final Long OTHER_MERCHANT_ID = 20L;

    @Mock
    private MerchantSecurityService merchantSecurityService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductBrandService brandService;

    @InjectMocks
    private ProductMerchantSpuController spuController;
    @InjectMocks
    private ProductMerchantBrandController brandController;

    @Test
    void createSpu_ignoresClientMerchantId() {
        when(merchantSecurityService.requireOperatingMerchant()).thenReturn(merchant(MERCHANT_ID));
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO();
        reqVO.setMerchantId(OTHER_MERCHANT_ID);

        spuController.create(reqVO);

        verify(productSpuService).createSpu(reqVO);
        org.junit.jupiter.api.Assertions.assertEquals(MERCHANT_ID, reqVO.getMerchantId());
    }

    @Test
    void updateSpu_rejectsSpuOwnedByAnotherMerchant() {
        when(merchantSecurityService.requireOperatingMerchant()).thenReturn(merchant(MERCHANT_ID));
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(1L);
        spu.setMerchantId(OTHER_MERCHANT_ID);
        when(productSpuService.getSpu(1L)).thenReturn(spu);
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO();
        reqVO.setId(1L);
        reqVO.setMerchantId(MERCHANT_ID);

        assertThrows(AccessDeniedException.class, () -> spuController.update(reqVO));
    }

    @Test
    void createBrand_ignoresClientMerchantId() {
        when(merchantSecurityService.requireOperatingMerchant()).thenReturn(merchant(MERCHANT_ID));
        ProductBrandCreateReqVO reqVO = new ProductBrandCreateReqVO();
        reqVO.setMerchantId(OTHER_MERCHANT_ID);

        brandController.create(reqVO);

        verify(brandService).createBrand(reqVO);
        org.junit.jupiter.api.Assertions.assertEquals(MERCHANT_ID, reqVO.getMerchantId());
    }

    @Test
    void updateBrand_rejectsBrandOwnedByAnotherMerchant() {
        when(merchantSecurityService.requireOperatingMerchant()).thenReturn(merchant(MERCHANT_ID));
        ProductBrandDO brand = new ProductBrandDO();
        brand.setId(2L);
        brand.setMerchantId(OTHER_MERCHANT_ID);
        when(brandService.getBrand(2L)).thenReturn(brand);
        cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO reqVO =
                new cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO();
        reqVO.setId(2L);

        assertThrows(AccessDeniedException.class, () -> brandController.update(reqVO));
    }

    private static ProductMerchantDO merchant(Long merchantId) {
        ProductMerchantDO merchant = new ProductMerchantDO();
        merchant.setId(merchantId);
        merchant.setName("shop");
        merchant.setStatus(20);
        return merchant;
    }

}
