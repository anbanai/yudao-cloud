package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSpuPageReqVOTest {

    @Test
    void testSortFieldValidation() {
        ProductSpuPageReqVO reqVO = new ProductSpuPageReqVO();
        assertTrue(reqVO.isSortFieldValid());

        reqVO.setSortField(ProductSpuPageReqVO.SORT_FIELD_PRICE);
        assertTrue(reqVO.isSortFieldValid());
        reqVO.setSortField(ProductSpuPageReqVO.SORT_FIELD_SALES_COUNT);
        assertTrue(reqVO.isSortFieldValid());
        reqVO.setSortField(ProductSpuPageReqVO.SORT_FIELD_CREATE_TIME);
        assertTrue(reqVO.isSortFieldValid());

        reqVO.setSortField("unknown");
        assertFalse(reqVO.isSortFieldValid());
    }

}
