package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuAccessRespDTO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppBrandPresentationTest {
    @InjectMocks private AppProductSpuController controller;
    @Mock private ProductBrandService brands;
    @Mock private ProductSpuService spus;
    @Mock private ProductSkuService skus;
    @Mock private ProductBrowseHistoryService history;
    private final ObjectMapper json = new ObjectMapper();
    private final ProductSpuDO spu = new ProductSpuDO().setId(4L).setStatus(1).setMerchantId(2L)
            .setBrandId(3L).setSalesCount(0).setVirtualSalesCount(0).setStock(5).setPrice(99);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void onlyOptedInDetailOfSameMerchantAndTenantIncludesBrandName() {
        when(spus.getAvailableSpu(4L)).thenReturn(spu);
        when(spus.checkAccess(4L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(true));
        var brand = new ProductBrandDO().setName("合成品牌").setMerchantId(2L).setShowOnDetail(false);
        brand.setTenantId(1L);
        when(brands.getBrand(3L)).thenReturn(brand);
        var off = json.valueToTree(controller.getSpuDetail(4L).getCheckedData());
        assertFalse(off.has("brandName"));
        brand.setShowOnDetail(true);
        var on = json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(controller.getSpuDetail(4L).getCheckedData());
        assertEquals("合成品牌", on.remove("brandName").asText());
        assertEquals(off, on);
        brand.setMerchantId(7L);
        assertNull(controller.getSpuDetail(4L).getCheckedData().getBrandName());
        brand.setMerchantId(2L).setTenantId(9L);
        assertNull(controller.getSpuDetail(4L).getCheckedData().getBrandName());
        spu.setStatus(0);
        assertThrows(ServiceException.class, () -> controller.getSpuDetail(4L));
        verify(brands, times(4)).getBrand(3L);
    }

    @Test void neitherPublicListReadsOrSerializesBrand() {
        var page = new AppProductSpuPageReqVO();
        when(spus.getVisibleSpuPage(page, null)).thenReturn(new PageResult<>(List.of(spu), 1L));
        when(spus.getAvailableSpuList(Set.of(4L))).thenReturn(new ArrayList<>(List.of(spu)));
        when(spus.checkAccess(4L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(true));
        for (var row : List.of(controller.getSpuPage(page).getCheckedData().getList().get(0),
                controller.getSpuList(Set.of(4L)).getCheckedData().get(0))) {
            var node = json.valueToTree(row);
            assertFalse(node.has("brandName"));
            assertFalse(node.has("brandId"));
        }
        verifyNoInteractions(brands);
    }
}
