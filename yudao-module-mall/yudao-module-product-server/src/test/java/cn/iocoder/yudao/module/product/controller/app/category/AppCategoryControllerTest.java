package cn.iocoder.yudao.module.product.controller.app.category;

import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppCategoryControllerTest {

    @Mock
    private ProductCategoryService categoryService;

    @InjectMocks
    private AppCategoryController controller;

    @Test
    void getProductCategoryList_returnsConfiguredDisplayProductCount() {
        ProductCategoryDO category = new ProductCategoryDO()
                .setId(1L)
                .setName("绿茶")
                .setPicUrl("https://example.com/category.png")
                .setSort(1)
                .setDisplayProductCount(12);
        when(categoryService.getEnableCategoryList()).thenReturn(new ArrayList<>(List.of(category)));

        Integer displayProductCount = controller.getProductCategoryList().getData().get(0).getDisplayProductCount();

        assertEquals(12, displayProductCount);
    }
}
