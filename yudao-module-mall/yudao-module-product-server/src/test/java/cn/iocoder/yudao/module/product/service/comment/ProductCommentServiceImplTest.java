package cn.iocoder.yudao.module.product.service.comment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.comment.vo.ProductCommentCreateReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.comment.ProductCommentDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.comment.ProductCommentMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_IMAGE_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCommentServiceImplTest {

    @InjectMocks
    private ProductCommentServiceImpl productCommentService;

    @Mock
    private ProductCommentMapper productCommentMapper;
    @Mock
    private FileApi fileApi;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductSkuService productSkuService;

    @Test
    void createComment_rejectsInvalidProductImage() {
        ProductCommentCreateReqVO reqVO = new ProductCommentCreateReqVO()
                .setSkuId(2L)
                .setUserAvatar("https://example.com/avatar.png")
                .setPicUrls(List.of("https://example.com/large.gif"));
        when(productSkuService.getSku(reqVO.getSkuId(), true))
                .thenReturn(new ProductSkuDO().setId(reqVO.getSkuId()).setSpuId(3L));
        when(productSpuService.getSpu(3L, true)).thenReturn(new ProductSpuDO().setId(3L));
        when(fileApi.validateImageUrl(reqVO.getPicUrls().get(0), "product"))
                .thenReturn(CommonResult.success(false));

        assertServiceException(() -> productCommentService.createComment(reqVO), SPU_IMAGE_INVALID);

        verify(productCommentMapper, never()).insert(any(ProductCommentDO.class));
        verify(fileApi).validateImageUrl(eq(reqVO.getPicUrls().get(0)), eq("product"));
    }

}
