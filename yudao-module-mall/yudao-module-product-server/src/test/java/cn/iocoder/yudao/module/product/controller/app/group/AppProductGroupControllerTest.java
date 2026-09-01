package cn.iocoder.yudao.module.product.controller.app.group;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppProductGroupControllerTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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
