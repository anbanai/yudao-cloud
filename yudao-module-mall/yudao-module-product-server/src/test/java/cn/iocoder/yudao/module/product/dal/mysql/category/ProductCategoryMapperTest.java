package cn.iocoder.yudao.module.product.dal.mysql.category;

import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCategoryMapperTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductCategoryDO.class);
    }

    @Test
    void testParentIdsFilterUsesParentIdColumn() {
        ProductCategoryListReqVO reqVO = new ProductCategoryListReqVO().setParentIds(List.of(10L, 20L));

        String sqlSegment = ProductCategoryMapper.buildListQuery(reqVO).getSqlSegment();

        assertTrue(sqlSegment.contains("parent_id IN"), sqlSegment);
        assertFalse(sqlSegment.contains(" id IN"), sqlSegment);
    }

}
