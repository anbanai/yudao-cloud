package cn.iocoder.yudao.framework.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTypeEnumTest {

    @Test
    void merchantHasStableUserTypeValue() {
        assertEquals(3, UserTypeEnum.valueOf("MERCHANT").getValue());
    }

}
