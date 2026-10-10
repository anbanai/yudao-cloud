package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_IMAGE_INVALID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ProductMerchantService merchantService;
    @Mock
    private FileApi fileApi;

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
                .setCategoryId(9L).setBrandId(8L).setMerchantId(1L).setSpecType(false)
                .setSkus(List.of(sku)).setGroupIds(null);
        when(productSpuMapper.selectById(100L)).thenReturn(new ProductSpuDO().setId(100L).setStatus(1));
        when(categoryService.getCategoryLevel(9L)).thenReturn(2);
        when(brandService.getBrand(8L)).thenReturn(new ProductBrandDO().setId(8L).setMerchantId(1L));

        productSpuService.updateSpu(reqVO);

        ArgumentCaptor<ProductSpuDO> spuCaptor = ArgumentCaptor.forClass(ProductSpuDO.class);
        verify(productSpuMapper).updateById(spuCaptor.capture());
        assertEquals(9L, spuCaptor.getValue().getCategoryId());
        verify(categoryService).validateCategory(9L);
        verify(productGroupService).syncSpuGroups(100L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<img src=https://example.com/large.gif>",
            "<picture><source srcset='https://example.com/large.gif 2x'><img src='https://example.com/ok.jpg'></picture>",
            "<video poster='https://example.com/large.gif' src='https://example.com/video.mp4'></video>",
            "<img data-src='https://example.com/large.gif'>",
            "<img src='' data-src='https://example.com/large.gif'>",
            "<img src='https://example.com/ok.jpg' original-src='https://example.com/large.gif'>",
            "<img src='data&#58;image/png;base64,abc'>",
            "<video poster='data&#58;image/png;base64,abc'></video>",
            "<svg><image href='data:image/png;base64,abc'></image></svg>",
            "<p style='background-image:url(data:image/png;base64,abc)'>商品详情</p>",
            "<p style='background-image:url(https://example.com/large.gif)'>商品详情</p>",
            "<p style='background-image:image-set(\"https://example.com/large.gif\" 1x)'>商品详情</p>",
            "<style>.hero{background-image:-webkit-image-set(\"data:image/png;base64,abc\" 1x)}</style>",
            "<style>@import \"https://example.com/product.css\";</style>",
            "<link rel='stylesheet' href='https://example.com/product.css'>",
            "<p style='background-image:u&#92;72l(data:image/png;base64,abc)'>商品详情</p>",
            "<p style='background-image:u/**/rl(data:image/png;base64,abc)'>商品详情</p>"
    })
    void testCreateSpuRejectsImageUrlOutsideProductImagePolicy(String description) {
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO().setName("商品")
                .setKeyword("关键词").setIntroduction("简介").setDescription(description)
                .setCategoryId(9L).setBrandId(8L).setMerchantId(1L)
                .setPicUrl(null).setSpecType(false)
                .setSkus(List.of(new ProductSkuSaveReqVO().setPicUrl(null)
                        .setPrice(100).setStock(1)));
        when(categoryService.getCategoryLevel(9L)).thenReturn(2);
        when(brandService.getBrand(8L)).thenReturn(new ProductBrandDO().setId(8L).setMerchantId(1L));
        if (description.contains("large.gif") && !description.contains("style=")) {
            when(fileApi.validateImageUrl(anyString(), eq("product"))).thenAnswer(invocation ->
                    CommonResult.success(!invocation.<String>getArgument(0).contains("large.gif")));
        }

        assertServiceException(() -> productSpuService.createSpu(reqVO), SPU_IMAGE_INVALID);

        verify(productSpuMapper, org.mockito.Mockito.never()).insert(any(ProductSpuDO.class));
    }

    @Test
    void testValidateSpuListChecksMerchantAvailability() {
        ProductSpuDO spu = new ProductSpuDO().setId(100L).setName("茶叶")
                .setStatus(ProductSpuStatusEnum.ENABLE.getStatus()).setMerchantId(9L);
        when(productSpuMapper.selectByIds(Set.of(100L))).thenReturn(List.of(spu));

        productSpuService.validateSpuList(Set.of(100L));

        verify(merchantService).validateMerchantAvailable(9L);
    }

}
