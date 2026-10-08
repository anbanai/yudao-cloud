package cn.iocoder.yudao.module.product.controller.app.group;

import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuFilterReqVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppProductGroupControllerTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void testAppGroupPageCannotDisablePagination() {
        var request = new AppProductGroupSpuPageReqVO().setGroupIds(List.of(1L));
        request.setPageSize(-1);
        assertFalse(validator.validate(request).isEmpty());
        request.setPageSize(50);
        assertTrue(validator.validate(request).isEmpty());
        request.setPageSize(51);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void testAdminCannotCombineUngroupedWithGroupIds() {
        var request = new ProductGroupSpuFilterReqVO().setUngrouped(true).setGroupIds(List.of(1L));
        assertFalse(validator.validate(request).isEmpty());
        request.setGroupIds(List.of());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void testListByIdsRejectsMoreThanFifteenGroups() throws NoSuchMethodException {
        Method method = AppProductGroupController.class.getMethod("getGroupList", Set.class);
        Set<Long> groupIds = LongStream.rangeClosed(1, 16).boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        var violations = validator.forExecutables().validateParameters(
                new AppProductGroupController(), method, new Object[]{groupIds});

        assertEquals(1, violations.size());
        assertEquals("最多查询 15 个商品分组", violations.iterator().next().getMessage());
    }
}
