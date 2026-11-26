package cn.iocoder.yudao.module.product.service.brand;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Import(ProductBrandServiceImpl.class)
class ProductBrandVisibilityPersistenceTest extends BaseDbUnitTest {
    @Resource private ProductBrandService brands;
    @MockBean private FileApi files;
    @MockBean private ProductMerchantService merchants;

    @Test void defaultOffExplicitOnLegacyUpdatePreservesAndExplicitOff() {
        var create = new ProductBrandCreateReqVO();
        create.setName("合成品牌"); create.setMerchantId(2L); create.setSort(1);
        create.setPicUrl("/synthetic.png"); create.setStatus(0);
        when(files.validateImageUrl("/synthetic.png", "product")).thenReturn(CommonResult.success(true));
        Long id = brands.createBrand(create);
        assertEquals(false, brands.getBrand(id).getShowInDetail());
        var edit = new ProductBrandUpdateReqVO();
        edit.setId(id); edit.setMerchantId(2L); edit.setName("合成品牌"); edit.setPicUrl("/synthetic.png");
        edit.setSort(2); edit.setStatus(0);
        edit.setShowInDetail(true);
        brands.updateBrand(edit);
        assertEquals(true, brands.getBrand(id).getShowInDetail());
        edit.setShowInDetail(null);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var admin = json.valueToTree(cn.iocoder.yudao.module.product.convert.brand.ProductBrandConvert.INSTANCE.convert(brands.getBrand(id)));
        assertTrue(admin.required("showInDetail").booleanValue());
        brands.updateBrand(edit);
        assertEquals(true, brands.getBrand(id).getShowInDetail());
        edit.setShowInDetail(false);
        brands.updateBrand(edit);
        assertEquals(false, brands.getBrand(id).getShowInDetail());
        assertEquals(0, brands.getBrand(id).getStatus());
    }
}
