package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuAccessRespDTO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO;
import cn.iocoder.yudao.module.product.convert.brand.ProductBrandConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandServiceImpl;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Import({ProductBrandServiceImpl.class, AppProductSpuController.class})
class BrandDisplayContractTest extends BaseDbUnitTest {
    @Resource private ProductBrandServiceImpl brands;
    @Resource private AppProductSpuController storefront;
    @MockBean private FileApi files;
    @MockBean private ProductMerchantService merchants;
    @MockBean private ProductSpuService spus;
    @MockBean private ProductSkuService skus;
    @MockBean private ProductBrowseHistoryService history;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void persistsSwitchFromAdminJsonWithoutChangingListOrSaleStatus() throws Exception {
        String create = "{\"name\":\"Synthetic Tea\",\"merchantId\":2,\"picUrl\":\"test.png\",\"status\":0,\"sort\":1}";
        when(files.validateImageUrl("test.png", "product")).thenReturn(CommonResult.success(true));
        Long brandId = brands.createBrand(json.readValue(create, ProductBrandCreateReqVO.class));
        TenantContextHolder.setTenantId(brands.getBrand(brandId).getTenantId());
        ProductSpuDO spu = new ProductSpuDO().setId(31L).setName("Synthetic").setMerchantId(2L).setBrandId(brandId)
                .setStatus(1).setSalesCount(0).setVirtualSalesCount(0);
        when(spus.getAvailableSpu(31L)).thenReturn(spu);
        when(spus.getAvailableSpuList(Set.of(31L))).thenReturn(List.of(spu));
        when(spus.checkAccess(31L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(true));
        boolean expected = false;
        for (String switchValue : List.of("initial", "true", "omitted", "false")) {
            if (!switchValue.equals("initial")) {
                String extra = switchValue.equals("omitted") ? "" : ",\"showInDetail\":" + switchValue;
                brands.updateBrand(json.readValue(create.substring(0, create.length() - 1) + ",\"id\":" + brandId + extra + "}", ProductBrandUpdateReqVO.class));
                if (!switchValue.equals("omitted")) expected = Boolean.parseBoolean(switchValue);
            }
            JsonNode admin = json.valueToTree(ProductBrandConvert.INSTANCE.convert(brands.getBrand(brandId)));
            assertEquals(expected, admin.required("showInDetail").booleanValue());
            JsonNode detail = json.valueToTree(storefront.getSpuDetail(31L).getCheckedData());
            assertEquals(expected, detail.has("brandName"));
            if (expected) assertEquals("Synthetic Tea", detail.required("brandName").textValue());
            JsonNode list = json.valueToTree(storefront.getSpuList(Set.of(31L)).getCheckedData());
            assertEquals(1, list.size());
            assertFalse(list.get(0).has("brandName"));
            assertEquals(brandId, spu.getBrandId());
            assertEquals(1, spu.getStatus());
        }
    }
}
