package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.module.product.controller.app.category.vo.AppCategoryRespVO;
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
    void getCategoryList_includesTopLevelCategoriesAndSortsThem() {
        when(merchantSecurityService.requireMerchantAccountId()).thenReturn(100L);
        ProductCategoryDO root = category(1L, "根分类");
        ProductCategoryDO leaf = category(2L, "茶叶").setParentId(root.getId());
        when(categoryService.getEnableCategoryList()).thenReturn(List.of(leaf, root));

        List<AppCategoryRespVO> result = controller.getCategoryList().getData();

        assertEquals(List.of(1L, 2L), result.stream().map(AppCategoryRespVO::getId).toList());
        verify(merchantSecurityService).requireMerchantAccountId();
    }

    private static ProductCategoryDO category(Long id, String name) {
        return new ProductCategoryDO().setId(id).setName(name).setParentId(0L).setSort(id.intValue());
    }
}
