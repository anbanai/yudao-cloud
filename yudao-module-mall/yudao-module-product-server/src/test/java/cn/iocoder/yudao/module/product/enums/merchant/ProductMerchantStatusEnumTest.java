package cn.iocoder.yudao.module.product.enums.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMerchantStatusEnumTest {

    @Test
    void isAvailable_onlyApprovedMerchantCanOperate() {
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.APPROVED.getStatus())).isTrue();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.DRAFT.getStatus())).isFalse();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.PENDING.getStatus())).isFalse();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.REJECTED.getStatus())).isFalse();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.FROZEN.getStatus())).isFalse();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.CHANGE_PENDING.getStatus())).isFalse();
        assertThat(ProductMerchantStatusEnum.isAvailable(ProductMerchantStatusEnum.CHANGE_REJECTED.getStatus())).isFalse();
    }

    @Test
    void merchantHasDedicatedUserType() {
        assertThat(UserTypeEnum.MERCHANT.getValue()).isEqualTo(3);
        assertThat(UserTypeEnum.valueOf(3)).isEqualTo(UserTypeEnum.MERCHANT);
    }
}
