package cn.iocoder.yudao.module.product.service.brand;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.mysql.brand.ProductBrandMapper;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_IMAGE_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBrandServiceImplTest {

    @InjectMocks
    private ProductBrandServiceImpl productBrandService;

    @Mock
    private ProductBrandMapper brandMapper;
    @Mock
    private FileApi fileApi;
    @Mock
    private ProductMerchantService merchantService;

    @Test
    void createBrand_rejectsInvalidProductImage() {
        ProductBrandCreateReqVO reqVO = new ProductBrandCreateReqVO();
        reqVO.setName("品牌");
        reqVO.setMerchantId(1L);
        reqVO.setPicUrl("https://example.com/large.gif");
        when(brandMapper.selectByName(reqVO.getName())).thenReturn(null);
        when(fileApi.validateImageUrl(reqVO.getPicUrl(), "product"))
                .thenReturn(CommonResult.success(false));

        assertServiceException(() -> productBrandService.createBrand(reqVO), SPU_IMAGE_INVALID);

        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
        verify(fileApi).validateImageUrl(reqVO.getPicUrl(), "product");
    }

}
