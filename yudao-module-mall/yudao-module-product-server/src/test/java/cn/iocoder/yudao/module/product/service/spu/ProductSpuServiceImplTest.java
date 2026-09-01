package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSpuServiceImplTest {

    @InjectMocks
    private ProductSpuServiceImpl productSpuService;

    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductCategoryService categoryService;
    @Mock
    private ProductBrandService brandService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductGroupService productGroupService;

    @Test
    void testGetAppSpuPageExpandsAllSelectedCategoryChildren() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO().setCategoryIds(List.of(10L, 20L));
        when(categoryService.getCategoryList(any())).thenReturn(List.of(
                new ProductCategoryDO().setId(11L).setParentId(10L),
                new ProductCategoryDO().setId(21L).setParentId(20L)));

        productSpuService.getSpuPage(reqVO);

        ArgumentCaptor<ProductCategoryListReqVO> categoryReqCaptor =
                ArgumentCaptor.forClass(ProductCategoryListReqVO.class);
        verify(categoryService).getCategoryList(categoryReqCaptor.capture());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), categoryReqCaptor.getValue().getStatus());
        assertEquals(List.of(10L, 20L), categoryReqCaptor.getValue().getParentIds());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> categoryIdsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productSpuMapper).selectPage(same(reqVO), categoryIdsCaptor.capture());
        assertEquals(Set.of(10L, 11L, 20L, 21L), categoryIdsCaptor.getValue());
    }

    @Test
    void testUpdateSpuKeepsCategoryAndSynchronizesOptionalGroups() {
        ProductSkuSaveReqVO sku = new ProductSkuSaveReqVO()
                .setPrice(100).setMarketPrice(120).setCostPrice(80).setStock(5);
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO().setId(100L)
                .setCategoryId(9L).setBrandId(8L).setSpecType(false)
                .setSkus(List.of(sku)).setGroupIds(null);
        when(productSpuMapper.selectById(100L)).thenReturn(new ProductSpuDO().setId(100L).setStatus(1));
        when(categoryService.getCategoryLevel(9L)).thenReturn(2);

        productSpuService.updateSpu(reqVO);

        ArgumentCaptor<ProductSpuDO> spuCaptor = ArgumentCaptor.forClass(ProductSpuDO.class);
        verify(productSpuMapper).updateById(spuCaptor.capture());
        assertEquals(9L, spuCaptor.getValue().getCategoryId());
        verify(categoryService).validateCategory(9L);
        verify(productGroupService).syncSpuGroups(100L, null);
    }

}
