package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_IMAGE_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @InjectMocks
    private ProductCategoryServiceImpl productCategoryService;

    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private FileApi fileApi;
    @Mock
    private ProductSpuService productSpuService;

    @Test
    void createCategory_rejectsInvalidProductImage() {
        ProductCategorySaveReqVO reqVO = new ProductCategorySaveReqVO()
                .setParentId(ProductCategoryDO.PARENT_ID_NULL)
                .setName("分类")
                .setPicUrl("https://example.com/large.gif");
        when(fileApi.validateImageUrl(reqVO.getPicUrl(), "product"))
                .thenReturn(CommonResult.success(false));

        assertServiceException(() -> productCategoryService.createCategory(reqVO), SPU_IMAGE_INVALID);

        verify(productCategoryMapper, never()).insert(any(ProductCategoryDO.class));
        verify(fileApi).validateImageUrl(eq(reqVO.getPicUrl()), eq("product"));
    }

}
