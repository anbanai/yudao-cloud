package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuAccessRespDTO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AppProductSpuControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppProductSpuController controller;
    @Mock
    private ProductSpuService spuService;

    @Test
    void testGetSpuList_filtersImmutableServiceResultInOrder() {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        ProductSpuDO first = new ProductSpuDO().setId(3L).setSalesCount(7).setVirtualSalesCount(2);
        ProductSpuDO hidden = new ProductSpuDO().setId(2L);
        ProductSpuDO last = new ProductSpuDO().setId(1L).setSalesCount(3).setVirtualSalesCount(1);
        when(spuService.getAvailableSpuList(ids)).thenReturn(List.of(first, hidden, last));
        when(spuService.checkAccess(3L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(true));
        when(spuService.checkAccess(2L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(false));
        when(spuService.checkAccess(1L, null, 1)).thenReturn(new ProductSpuAccessRespDTO().setVisible(true));

        TenantContextHolder.setTenantId(1L);
        try {
            List<AppProductSpuRespVO> result = controller.getSpuList(ids).getCheckedData();
            assertThat(result).extracting(AppProductSpuRespVO::getId).containsExactly(3L, 1L);
            assertThat(result).extracting(AppProductSpuRespVO::getSalesCount).containsExactly(9, 4);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
