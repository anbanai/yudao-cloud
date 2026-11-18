package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductSpuVisiblePaginationTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void filtersAcrossScannerBoundaryAndReturnsVisiblePageWithExactTotal() {
        ProductSpuServiceImpl service = createService(205, 200L);

        AppProductSpuPageReqVO req = new AppProductSpuPageReqVO();
        req.setPageNo(67);
        req.setPageSize(3);
        PageResult<ProductSpuDO> page = service.getVisibleSpuPage(req, null);

        assertThat(page.getTotal()).isEqualTo(204);
        assertThat(page.getList()).extracting(ProductSpuDO::getId).containsExactly(199L, 201L, 202L);
    }

    @Test
    void returnsExactTotalForOutOfRangeVisiblePage() {
        ProductSpuServiceImpl service = createService(205, 200L);

        AppProductSpuPageReqVO req = new AppProductSpuPageReqVO();
        req.setPageNo(69);
        req.setPageSize(3);
        PageResult<ProductSpuDO> page = service.getVisibleSpuPage(req, null);

        assertThat(page.getTotal()).isEqualTo(204);
        assertThat(page.getList()).isEmpty();
    }

    private ProductSpuServiceImpl createService(int count, long restrictedId) {
        TenantContextHolder.setTenantId(1L);
        ProductSpuServiceImpl service = new ProductSpuServiceImpl();
        ProductSpuMapper mapper = mock(ProductSpuMapper.class);
        ProductSpuGroupMapper groups = mock(ProductSpuGroupMapper.class);
        ReflectionTestUtils.setField(service, "productSpuMapper", mapper);
        ReflectionTestUtils.setField(service, "productSpuGroupMapper", groups);
        ReflectionTestUtils.setField(service, "memberUserApi", mock(MemberUserApi.class));
        ReflectionTestUtils.setField(service, "memberLevelApi", mock(MemberLevelApi.class));

        List<ProductSpuDO> all = LongStream.rangeClosed(1, count).mapToObj(id -> {
            ProductSpuDO spu = new ProductSpuDO();
            spu.setId(id);
            spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus());
            return spu;
        }).collect(Collectors.toList());
        all.get((int) restrictedId - 1).setBuyLimitLevel("L0"); // An anonymous visitor cannot see this SPU.
        when(groups.selectGroupIds(anyLong(), eq(1L))).thenReturn(Collections.emptyList());
        when(mapper.selectPage(any(AppProductSpuPageReqVO.class), anySet())).thenAnswer(invocation -> {
            AppProductSpuPageReqVO req = invocation.getArgument(0);
            int start = (req.getPageNo() - 1) * req.getPageSize();
            int end = Math.min(start + req.getPageSize(), all.size());
            List<ProductSpuDO> list = start >= all.size() ? Collections.emptyList() : all.subList(start, end);
            return new PageResult<>(new ArrayList<>(list), (long) all.size());
        });
        return service;
    }
}
