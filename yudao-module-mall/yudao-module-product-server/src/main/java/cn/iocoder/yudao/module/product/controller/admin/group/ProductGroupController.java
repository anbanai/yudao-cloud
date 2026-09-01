package cn.iocoder.yudao.module.product.controller.admin.group;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品分组")
@RestController
@RequestMapping("/product/group")
@Validated
public class ProductGroupController {

    @Resource
    private ProductGroupService groupService;

    @PostMapping("/create")
    @Operation(summary = "创建商品分组")
    @PreAuthorize("@ss.hasPermission('product:group:create')")
    public CommonResult<Long> createGroup(@Valid @RequestBody ProductGroupSaveReqVO reqVO) {
        return success(groupService.createGroup(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商品分组")
    @PreAuthorize("@ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> updateGroup(@Valid @RequestBody ProductGroupSaveReqVO reqVO) {
        groupService.updateGroup(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品分组")
    @PreAuthorize("@ss.hasPermission('product:group:delete')")
    public CommonResult<Boolean> deleteGroup(@RequestParam("id") Long id) {
        groupService.deleteGroup(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得商品分组")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<ProductGroupRespVO> getGroup(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(groupService.getGroup(id), ProductGroupRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商品分组分页")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<PageResult<ProductGroupRespVO>> getGroupPage(@Valid ProductGroupPageReqVO reqVO) {
        return success(BeanUtils.toBean(groupService.getGroupPage(reqVO), ProductGroupRespVO.class));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获得商品分组精简列表")
    public CommonResult<List<ProductGroupSimpleRespVO>> getSimpleGroupList() {
        return success(BeanUtils.toBean(groupService.getGroupList(null, false), ProductGroupSimpleRespVO.class));
    }

    @GetMapping(value = "/spu-page", params = "groupId")
    @Operation(summary = "获得分组成员分页")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<PageResult<ProductGroupSpuRespVO>> getSpuPage(@Valid ProductGroupSpuPageReqVO reqVO) {
        return success(groupService.getAdminSpuPage(reqVO));
    }

    @GetMapping(value = "/spu-page", params = "groupIds")
    @Operation(summary = "获得装修分组商品分页")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<PageResult<ProductGroupSpuRespVO>> getDecorationSpuPage(
            @Valid AppProductGroupSpuPageReqVO reqVO) {
        PageResult<ProductSpuDO> page = groupService.getAppSpuPage(reqVO);
        return success(BeanUtils.toBean(page, ProductGroupSpuRespVO.class));
    }

    @PostMapping("/spu-add")
    @Operation(summary = "批量加入分组成员")
    @PreAuthorize("@ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> addSpus(@Valid @RequestBody ProductGroupSpuBatchReqVO reqVO) {
        groupService.addSpus(reqVO);
        return success(true);
    }

    @DeleteMapping("/spu-remove")
    @Operation(summary = "批量移除分组成员")
    @PreAuthorize("@ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> removeSpus(@Valid @RequestBody ProductGroupSpuBatchReqVO reqVO) {
        groupService.removeSpus(reqVO);
        return success(true);
    }

    @PutMapping("/spu-sort")
    @Operation(summary = "更新分组成员排序")
    @PreAuthorize("@ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> updateSpuSort(@Valid @RequestBody ProductGroupSpuSortReqVO reqVO) {
        groupService.updateSpuSort(reqVO);
        return success(true);
    }

    @GetMapping("/spu-group-ids")
    @Operation(summary = "获得商品所属分组编号")
    @Parameter(name = "spuId", required = true)
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<List<Long>> getSpuGroupIds(@RequestParam("spuId") Long spuId) {
        return success(groupService.getGroupIdsBySpuId(spuId));
    }
}
