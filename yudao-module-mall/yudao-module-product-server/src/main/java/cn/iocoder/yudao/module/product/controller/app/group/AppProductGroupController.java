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
import cn.iocoder.yudao.module.product.service.group.ProductGroupOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @Resource
    private ProductGroupOperationsService operationsService;

    // 兼容旧版小程序的分组入口。分组展示须由入口传入明确的分组编号。
    @GetMapping({"/list", "/list-by-spu-id"})
    @Operation(summary = "兼容旧版分组入口")
    @PermitAll
    public CommonResult<List<AppProductGroupSimpleRespVO>> getLegacyGroupList() {
        return success(List.of());
    }

    @GetMapping("/list-by-ids")
    @Operation(summary = "获得启用的商品分组列表")
    @PermitAll
    public CommonResult<List<AppProductGroupSimpleRespVO>> getGroupList(
            @RequestParam("ids") @NotEmpty @Size(max = 15, message = "最多查询 15 个商品分组")
            Set<@NotNull @Positive Long> ids) {
        List<ProductGroupDO> groups = operationsService.getEnabledGroupsByIds(ids);
        return success(BeanUtils.toBean(groups, AppProductGroupSimpleRespVO.class));
    }

    @GetMapping("/spu-page")
    @Operation(summary = "获得商品分组商品分页")
    @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> getSpuPage(@Valid AppProductGroupSpuPageReqVO reqVO) {
        PageResult<ProductSpuDO> page = groupService.getVisibleAppSpuPage(reqVO);
        page.getList().forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        return success(BeanUtils.toBean(page, AppProductSpuRespVO.class));
    }
}
