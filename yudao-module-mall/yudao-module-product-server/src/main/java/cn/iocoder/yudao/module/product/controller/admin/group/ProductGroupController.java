package cn.iocoder.yudao.module.product.controller.admin.group;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductGroupRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuGroupMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.annotation.security.PermitAll;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品分组")
@RestController
@RequestMapping("/product/group")
@Validated
public class ProductGroupController {
    @Resource private ProductGroupMapper groupMapper;
    @Resource private ProductSpuGroupMapper relationMapper;
    @Resource private ProductSpuService spuService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('product:group:create')")
    public CommonResult<Long> create(@RequestBody Map<String, Object> req) {
        ProductGroupDO group = convert(req);
        groupMapper.insert(group);
        return success(group.getId());
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> update(@RequestBody Map<String, Object> req) {
        ProductGroupDO group = convert(req);
        if (group.getId() == null || groupMapper.selectById(group.getId()) == null) return success(false);
        groupMapper.updateById(group);
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('product:group:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        groupMapper.deleteById(id);
        return success(true);
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<ProductGroupDO> get(@RequestParam Long id) { return success(groupMapper.selectById(id)); }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<PageResult<ProductGroupDO>> page(@Valid ProductGroupPageReqVO req) {
        return success(groupMapper.selectPage(req, new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ProductGroupDO>()
                .likeIfPresent(ProductGroupDO::getName, req.getName()).eqIfPresent(ProductGroupDO::getStatus, req.getStatus())
                .orderByAsc(ProductGroupDO::getSort).orderByDesc(ProductGroupDO::getId)));
    }

    @GetMapping("/list-all-simple")
    @PreAuthorize("@ss.hasPermission('product:group:query')")
    public CommonResult<List<ProductGroupDO>> listAll() { return success(groupMapper.selectSimpleList()); }

    @GetMapping("/list")
    @PermitAll
    public CommonResult<List<AppProductGroupRespVO>> publicList() {
        return success(BeanUtils.toBean(groupMapper.selectPublicList(), AppProductGroupRespVO.class));
    }

    @GetMapping("/list-by-ids")
    @PermitAll
    public CommonResult<List<AppProductGroupRespVO>> publicListByIds(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 15) return success(Collections.emptyList());
        return success(BeanUtils.toBean(groupMapper.selectPublicByIds(ids), AppProductGroupRespVO.class));
    }

    @GetMapping("/list-by-spu-id")
    @PermitAll
    public CommonResult<List<AppProductGroupRespVO>> publicListBySpuId(@RequestParam Long spuId) {
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        List<Long> groupIds = relationMapper.selectGroupIdsBySpuId(spuId, tenantId);
        if (groupIds.isEmpty()) return success(Collections.emptyList());
        return success(BeanUtils.toBean(groupMapper.selectPublicByIds(groupIds), AppProductGroupRespVO.class));
    }

    @GetMapping("/spu-group-ids")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<Long>> spuGroupIds(@RequestParam Long spuId) { return success(spuService.getGroupIds(spuId)); }

    @GetMapping("/spu-group-map")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<Map<Long, List<Long>>> spuGroupMap(@RequestParam List<Long> spuIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long spuId : spuIds) result.put(spuId, new ArrayList<>());
        for (Map<String, Object> row : relationMapper.selectSpuGroupPairs(spuIds,
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())) {
            Long spuId = ((Number) row.get("spuId")).longValue();
            result.computeIfAbsent(spuId, key -> new ArrayList<>()).add(((Number) row.get("groupId")).longValue());
        }
        return success(result);
    }

    @PostMapping("/spu-batch-update")
    @PreAuthorize("@ss.hasPermission('product:spu:update') or @ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> batchUpdate(@RequestBody Map<String, Object> req) {
        List<Long> spuIds = longs(req.get("spuIds"));
        List<Long> groupIds = longs(req.get("groupIds"));
        String operation = String.valueOf(req.get("operation"));
        if (spuIds.isEmpty() || spuIds.size() > 200 || groupIds.isEmpty() || groupIds.size() > 15
                || !("add".equals(operation) || "remove".equals(operation))) return success(false);
        Long tenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        if ("add".equals(operation)) for (Long groupId : groupIds) {
            ProductGroupDO group = groupMapper.selectById(groupId);
            if (group == null || !Integer.valueOf(0).equals(group.getStatus())) return success(false);
        }
        for (Long spuId : spuIds) for (Long groupId : groupIds) {
            if ("remove".equals(operation)) relationMapper.deleteRelation(groupId, spuId, tenant);
            else if (relationMapper.countRelation(groupId, spuId, tenant) == 0) relationMapper.insertRelation(groupId, spuId, 0, tenant);
        }
        return success(true);
    }

    @GetMapping("/spu-page")
    @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> groupPage(@Valid AppProductSpuPageReqVO req) {
        if (req.getGroupIds() == null || req.getGroupIds().isEmpty() || req.getGroupIds().size() > 15) return success(PageResult.empty(0L));
        List<ProductGroupDO> groups = groupMapper.selectPublicByIds(req.getGroupIds());
        if (groups.isEmpty()) return success(PageResult.empty(0L));
        req.setGroupIds(groups.stream().map(ProductGroupDO::getId).collect(Collectors.toList()));
        PageResult<ProductSpuDO> page = spuService.getVisibleSpuPage(req, getLoginUserId());
        List<ProductSpuDO> visible = page.getList();
        visible.forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        return success(BeanUtils.toBean(page, AppProductSpuRespVO.class));
    }

    @PostMapping("/spu-add")
    @PreAuthorize("@ss.hasPermission('product:spu:update') or @ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> addMembers(@RequestBody Map<String, Object> req) {
        Long groupId = longValue(req.get("groupId"));
        List<Long> spuIds = longs(req.get("spuIds"));
        return success(updateRelations(groupId, spuIds, "add"));
    }

    @DeleteMapping("/spu-remove")
    @PreAuthorize("@ss.hasPermission('product:spu:update') or @ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> removeMembers(@RequestBody Map<String, Object> req) {
        Long groupId = longValue(req.get("groupId"));
        List<Long> spuIds = longs(req.get("spuIds"));
        return success(updateRelations(groupId, spuIds, "remove"));
    }

    @PutMapping("/spu-sort")
    @PreAuthorize("@ss.hasPermission('product:spu:update') or @ss.hasPermission('product:group:update')")
    public CommonResult<Boolean> sortMember(@RequestBody Map<String, Object> req) {
        return success(relationMapper.updateSort(longValue(req.get("groupId")), longValue(req.get("spuId")),
                ((Number) req.get("sort")).intValue(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()) > 0);
    }

    @GetMapping("/spu-filter-page")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<PageResult<ProductSpuDO>> filterPage(@Valid ProductSpuPageReqVO req) { return success(spuService.getSpuPage(req)); }

    @GetMapping("/spu-filter-count")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<Map<Integer, Long>> filterCount(@Valid ProductSpuPageReqVO req) { return success(spuService.getTabsCount(req)); }

    @GetMapping("/spu-filter-export")
    @PreAuthorize("@ss.hasPermission('product:spu:export')")
    @ApiAccessLog(operateType = OperateTypeEnum.EXPORT)
    public void filterExport(@Valid ProductSpuPageReqVO req, HttpServletResponse response) throws IOException {
        req.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ProductSpuDO> spus = spuService.getSpuPage(req).getList();
        Class<cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO> type = cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO.class;
        ExcelUtils.write(response, "商品列表.xls", "数据", type, BeanUtils.toBean(spus, type));
    }

    private ProductGroupDO convert(Map<String, Object> req) {
        ProductGroupDO group = new ProductGroupDO();
        group.setId(longValue(req.get("id"))).setName(req.get("name") == null ? null : String.valueOf(req.get("name")))
                .setSort(intValue(req.get("sort"), 0)).setStatus(intValue(req.get("status"), 0))
                .setStorefrontVisible(req.get("storefrontVisible") == null || Boolean.TRUE.equals(req.get("storefrontVisible")))
                .setRemark(req.get("remark") == null ? null : String.valueOf(req.get("remark")));
        if (group.getName() == null || group.getName().trim().isEmpty() || group.getName().length() > 64) throw new IllegalArgumentException("分组名称须为 1..64 字符");
        return group;
    }
    private boolean updateRelations(Long groupId, List<Long> spuIds, String operation) {
        if (groupId == null || spuIds.isEmpty() || spuIds.size() > 200) return false;
        Long tenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        ProductGroupDO group = groupMapper.selectById(groupId);
        if (group == null || ("add".equals(operation) && !Integer.valueOf(0).equals(group.getStatus()))) return false;
        for (Long spuId : spuIds) {
            if ("remove".equals(operation)) relationMapper.deleteRelation(groupId, spuId, tenant);
            else if (relationMapper.countRelation(groupId, spuId, tenant) == 0) relationMapper.insertRelation(groupId, spuId, 0, tenant);
        }
        return true;
    }
    private static Integer intValue(Object value, int fallback) { return value instanceof Number ? ((Number) value).intValue() : fallback; }
    private static Long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : null; }
    private static List<Long> longs(Object value) {
        if (!(value instanceof Collection)) return Collections.emptyList();
        return ((Collection<?>) value).stream().map(ProductGroupController::longValue).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}
