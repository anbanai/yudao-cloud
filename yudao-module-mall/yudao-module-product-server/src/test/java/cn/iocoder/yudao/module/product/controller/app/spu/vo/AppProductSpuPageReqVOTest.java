package cn.iocoder.yudao.module.product.controller.app.spu.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppProductSpuPageReqVOTest {

    @Test
    void testSortFieldValidation() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        assertTrue(reqVO.isSortFieldValid());

        reqVO.setSortField(AppProductSpuPageReqVO.SORT_FIELD_PRICE);
        assertTrue(reqVO.isSortFieldValid());
        reqVO.setSortField(AppProductSpuPageReqVO.SORT_FIELD_SALES_COUNT);
        assertTrue(reqVO.isSortFieldValid());
        reqVO.setSortField(AppProductSpuPageReqVO.SORT_FIELD_CREATE_TIME);
        assertTrue(reqVO.isSortFieldValid());

        reqVO.setSortField("unknown");
        assertFalse(reqVO.isSortFieldValid());
    }

}
