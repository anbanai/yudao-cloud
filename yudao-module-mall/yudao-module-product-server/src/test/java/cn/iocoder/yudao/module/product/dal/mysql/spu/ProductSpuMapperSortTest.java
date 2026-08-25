package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSpuMapperSortTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductSpuDO.class);
    }

    @Test
    void testAppendSortQuery() {
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_CREATE_TIME, true, "create_time ASC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_CREATE_TIME, false, "create_time DESC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_CREATE_TIME, null, "create_time DESC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_PRICE, true, "price ASC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_PRICE, false, "price DESC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_PRICE, null, "price DESC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_SALES_COUNT, true,
                "sales_count + virtual_sales_count) ASC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_SALES_COUNT, false,
                "sales_count + virtual_sales_count) DESC");
        assertOrderBy(AppProductSpuPageReqVO.SORT_FIELD_SALES_COUNT, null,
                "sales_count + virtual_sales_count) DESC");
        assertOrderBy("unknown", true, "sort DESC");
        assertOrderBy(null, null, "sort DESC");
    }

    private static void assertOrderBy(String sortField, Boolean sortAsc, String expectedSql) {
        LambdaQueryWrapperX<ProductSpuDO> query = new LambdaQueryWrapperX<>();
        ProductSpuMapper.appendSortQuery(query, sortField, sortAsc);
        String sqlSegment = query.getSqlSegment();
        assertTrue(sqlSegment.contains(expectedSql), sqlSegment);
        int sortIndex = sqlSegment.indexOf("sort DESC");
        int idIndex = sqlSegment.indexOf("id DESC");
        assertTrue(sortIndex >= 0, sqlSegment);
        assertTrue(idIndex > sortIndex, sqlSegment);
    }

}
