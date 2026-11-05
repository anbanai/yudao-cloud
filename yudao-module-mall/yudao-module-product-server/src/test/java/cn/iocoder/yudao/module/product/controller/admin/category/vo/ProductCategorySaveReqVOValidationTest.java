package cn.iocoder.yudao.module.product.controller.admin.category.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCategorySaveReqVOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void testDefaultDisplayProductCount() {
        assertEquals(6, new ProductCategorySaveReqVO().getDisplayProductCount());
    }

    @Test
    void testDisplayProductCountMustBeBetweenOneAndFifty() {
        ProductCategorySaveReqVO request = validRequest().setDisplayProductCount(1);
        assertTrue(validator.validate(request).isEmpty());

        request.setDisplayProductCount(50);
        assertTrue(validator.validate(request).isEmpty());

        request.setDisplayProductCount(0);
        assertFalse(validator.validate(request).isEmpty());

        request.setDisplayProductCount(51);
        assertFalse(validator.validate(request).isEmpty());
    }

    private ProductCategorySaveReqVO validRequest() {
        return new ProductCategorySaveReqVO()
                .setParentId(0L)
                .setName("茶")
                .setPicUrl("https://example.com/category.png")
                .setSort(1)
                .setStatus(0);
    }
}
