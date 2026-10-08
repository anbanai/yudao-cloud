package cn.iocoder.yudao.module.member.controller.admin.benefit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitDefinitionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitLedgerDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberLevelBenefitDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberUserBenefitDO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitAccountAdjustReqVO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitAccountOperateReqVO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitAccountPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitLedgerPageReqVO;
import cn.iocoder.yudao.module.member.service.benefit.MemberBenefitAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 会员权益")
@RestController
@RequestMapping("/member/benefit")
@Validated
public class MemberBenefitAdminController {

    @Resource
    private MemberBenefitAdminService adminService;

    @GetMapping("/definition/list")
    @Operation(summary = "获得权益定义列表")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<List<MemberBenefitDefinitionDO>> getDefinitions() {
        return success(adminService.getDefinitions());
    }

    @PostMapping("/definition/create")
    @Operation(summary = "创建权益定义")
    @PreAuthorize("@ss.hasPermission('member:benefit:create')")
    public CommonResult<Long> createDefinition(@RequestBody MemberBenefitDefinitionDO definition) {
        return success(adminService.createDefinition(definition));
    }

    @PutMapping("/definition/update")
    @Operation(summary = "更新权益定义")
    @PreAuthorize("@ss.hasPermission('member:benefit:update')")
    public CommonResult<Boolean> updateDefinition(@RequestBody MemberBenefitDefinitionDO definition) {
        adminService.updateDefinition(definition);
        return success(true);
    }

    @DeleteMapping("/definition/delete")
    @Operation(summary = "删除权益定义")
    @PreAuthorize("@ss.hasPermission('member:benefit:delete')")
    public CommonResult<Boolean> deleteDefinition(@RequestParam Long id) {
        adminService.deleteDefinition(id);
        return success(true);
    }

    @GetMapping("/level-binding/list")
    @Operation(summary = "获得等级权益绑定")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<List<MemberLevelBenefitDO>> getLevelBindings(@RequestParam(required = false) Long levelId) {
        return success(adminService.getLevelBindings(levelId));
    }

    @PostMapping("/level-binding/save")
    @Operation(summary = "保存等级权益绑定")
    @PreAuthorize("@ss.hasPermission('member:benefit:update')")
    public CommonResult<Boolean> saveLevelBinding(@RequestBody MemberLevelBenefitDO binding) {
        adminService.saveLevelBinding(binding);
        return success(true);
    }

    @DeleteMapping("/level-binding/delete")
    @Operation(summary = "删除等级权益绑定")
    @PreAuthorize("@ss.hasPermission('member:benefit:delete')")
    public CommonResult<Boolean> deleteLevelBinding(@RequestParam Long id) {
        adminService.deleteLevelBinding(id);
        return success(true);
    }

    @GetMapping("/account/list")
    @Operation(summary = "获得用户权益账户")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<List<MemberUserBenefitDO>> getUserAccounts(@RequestParam(required = false) Long userId) {
        return success(adminService.getUserAccounts(userId));
    }

    @GetMapping("/account/page")
    @Operation(summary = "分页获得用户权益账户")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<PageResult<MemberUserBenefitDO>> getUserAccountPage(@Valid MemberBenefitAccountPageReqVO reqVO) {
        return success(adminService.getUserAccountPage(reqVO));
    }

    @GetMapping("/ledger/list")
    @Operation(summary = "获得权益流水")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<List<MemberBenefitLedgerDO>> getLedgers(@RequestParam(required = false) Long userId,
                                                                  @RequestParam(required = false) Long accountId) {
        return success(adminService.getLedgers(userId, accountId));
    }

    @GetMapping("/ledger/page")
    @Operation(summary = "分页获得权益流水")
    @PreAuthorize("@ss.hasPermission('member:benefit:query')")
    public CommonResult<PageResult<MemberBenefitLedgerDO>> getLedgerPage(@Valid MemberBenefitLedgerPageReqVO reqVO) {
        return success(adminService.getLedgerPage(reqVO));
    }

    @PostMapping("/account/grant")
    @Operation(summary = "补发会员权益")
    @PreAuthorize("@ss.hasPermission('member:benefit:operate')")
    public CommonResult<Boolean> grant(@Valid @RequestBody MemberBenefitAccountAdjustReqVO reqVO) {
        adminService.grant(reqVO.getAccountId(), reqVO.getQuantity(), reqVO.getReason(), reqVO.getBizId(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/account/revoke")
    @Operation(summary = "撤销会员权益")
    @PreAuthorize("@ss.hasPermission('member:benefit:operate')")
    public CommonResult<Boolean> revoke(@Valid @RequestBody MemberBenefitAccountOperateReqVO reqVO) {
        adminService.revoke(reqVO.getAccountId(), reqVO.getReason(), reqVO.getBizId(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/account/adjust")
    @Operation(summary = "人工调整会员权益")
    @PreAuthorize("@ss.hasPermission('member:benefit:operate')")
    public CommonResult<Boolean> adjust(@Valid @RequestBody MemberBenefitAccountAdjustReqVO reqVO) {
        adminService.adjust(reqVO.getAccountId(), reqVO.getQuantity(), reqVO.getReason(), reqVO.getBizId(), getLoginUserId());
        return success(true);
    }
}
