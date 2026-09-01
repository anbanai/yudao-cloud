package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductSpuSaveReqVOTest {

    @Test
    void testGroupIdsIsOptionalWithoutChangingRequiredCategory() throws NoSuchFieldException {
        Field categoryIdField = ProductSpuSaveReqVO.class.getDeclaredField("categoryId");
        assertNotNull(categoryIdField.getAnnotation(NotNull.class));

        Field groupIdsField = Arrays.stream(ProductSpuSaveReqVO.class.getDeclaredFields())
                .filter(field -> field.getName().equals("groupIds"))
                .findFirst().orElse(null);
        assertNotNull(groupIdsField, "商品保存请求应提供可选的 groupIds");
        assertEquals(List.class, groupIdsField.getType());
        assertEquals(Long.class, ((ParameterizedType) groupIdsField.getGenericType()).getActualTypeArguments()[0]);
        assertNull(groupIdsField.getAnnotation(NotNull.class));

        Field responseGroupIdsField = ProductSpuRespVO.class.getDeclaredField("groupIds");
        assertEquals(List.class, responseGroupIdsField.getType());
    }

}
