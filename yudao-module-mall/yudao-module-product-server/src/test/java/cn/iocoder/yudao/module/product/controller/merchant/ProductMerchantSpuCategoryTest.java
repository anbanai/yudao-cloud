package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMerchantSpuCategoryTest {

    @Mock
    private MerchantSecurityService merchantSecurityService;
    @Mock
    private ProductCategoryService categoryService;

    @InjectMocks
    private ProductMerchantSpuController controller;

    @Test
    void getCategoryList_returnsOnlyCategoriesUsableForProducts() {
        when(merchantSecurityService.requireMerchantAccountId()).thenReturn(100L);
        ProductCategoryDO root = category(1L, "根分类");
        ProductCategoryDO leaf = category(2L, "茶叶");
        when(categoryService.getEnableCategoryList()).thenReturn(List.of(root, leaf));
        when(categoryService.getCategoryLevel(1L)).thenReturn(1);
        when(categoryService.getCategoryLevel(2L)).thenReturn(2);

        List<?> result = controller.getCategoryList().getData();

        assertEquals(1, result.size());
        verify(merchantSecurityService).requireMerchantAccountId();
    }

    private static ProductCategoryDO category(Long id, String name) {
        return new ProductCategoryDO().setId(id).setName(name).setParentId(0L).setSort(id.intValue());
    }
}
