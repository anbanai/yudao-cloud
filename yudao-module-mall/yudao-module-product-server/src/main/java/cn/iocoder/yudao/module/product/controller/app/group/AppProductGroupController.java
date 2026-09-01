package cn.iocoder.yudao.module.product.controller.app.group;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSimpleRespVO;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 商品分组")
@RestController
@RequestMapping("/product/group")
@Validated
public class AppProductGroupController {

    @Resource
    private ProductGroupService groupService;

    @GetMapping("/list-by-ids")
    @Operation(summary = "获得启用的商品分组列表")
    @PermitAll
    public CommonResult<List<AppProductGroupSimpleRespVO>> getGroupList(
            @RequestParam("ids") @Size(max = 15, message = "最多查询 15 个商品分组") Set<Long> ids) {
        List<ProductGroupDO> groups = groupService.getGroupList(ids, true);
        return success(BeanUtils.toBean(groups, AppProductGroupSimpleRespVO.class));
    }

    @GetMapping("/spu-page")
    @Operation(summary = "获得商品分组商品分页")
    @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> getSpuPage(@Valid AppProductGroupSpuPageReqVO reqVO) {
        PageResult<ProductSpuDO> page = groupService.getAppSpuPage(reqVO);
        page.getList().forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        return success(BeanUtils.toBean(page, AppProductSpuRespVO.class));
    }
}
