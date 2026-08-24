package cn.iocoder.yudao.framework.common.util.string;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StrUtils} 的单元测试
 */
public class StrUtilsTest {

    @Test
    public void testSplitToLong_nullOrNullString() {
        // null、空串、以及数据库脏数据字面量 "null" 均不抛异常，返回空集合
        assertEquals(Collections.emptyList(), StrUtils.splitToLong(null, ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToLong("", ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToLong("null", ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToLong("NULL", ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToLong(" null ", ","));
    }

    @Test
    public void testSplitToLong_normal() {
        assertEquals(Arrays.asList(1L, 2L, 3L), StrUtils.splitToLong("1,2,3", ","));
    }

    @Test
    public void testSplitToLongSet_nullOrNullString() {
        assertEquals(Collections.emptySet(), StrUtils.splitToLongSet(null, ","));
        assertEquals(Collections.emptySet(), StrUtils.splitToLongSet("null", ","));
        assertEquals(Collections.emptySet(), StrUtils.splitToLongSet("", ","));
    }

    @Test
    public void testSplitToInteger_nullOrNullString() {
        assertEquals(Collections.emptyList(), StrUtils.splitToInteger(null, ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToInteger("null", ","));
        assertEquals(Collections.emptyList(), StrUtils.splitToInteger("", ","));
    }
}
