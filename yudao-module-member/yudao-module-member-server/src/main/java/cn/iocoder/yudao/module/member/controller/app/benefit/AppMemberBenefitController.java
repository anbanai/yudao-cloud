package cn.iocoder.yudao.module.member.controller.app.benefit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.benefit.vo.AppMemberBenefitOverviewRespVO;
import cn.iocoder.yudao.module.member.controller.app.benefit.vo.AppMemberBenefitRespVO;
import cn.iocoder.yudao.module.member.service.benefit.MemberBenefitService;
import cn.iocoder.yudao.module.member.service.level.MemberLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 会员权益")
@RestController
@RequestMapping("/member/benefits")
@Validated
@PreAuthorize("isAuthenticated()")
public class AppMemberBenefitController {

    @Resource
    private MemberBenefitService benefitService;
    @Resource
    private MemberLevelService levelService;

    @GetMapping("/overview")
    @Operation(summary = "获得会员权益总览")
    public CommonResult<AppMemberBenefitOverviewRespVO> getOverview() {
        return success(benefitService.getOverview(getLoginUserId()));
    }

    @GetMapping("/levels")
    @Operation(summary = "获得会员等级权益")
    @Parameter(name = "level", description = "等级序号", required = true, example = "1")
    public CommonResult<List<AppMemberBenefitRespVO>> getLevelBenefits(@RequestParam Integer level) {
        return success(levelService.getEnableLevelList().stream()
                .filter(item -> level.equals(item.getLevel()))
                .findFirst().map(item -> benefitService.getLevelBenefits(item.getId()))
                .orElse(List.of()));
    }

    @GetMapping("/records")
    @Operation(summary = "获得我的权益记录")
    public CommonResult<List<AppMemberBenefitRespVO>> getRecords() {
        return success(benefitService.getRecords(getLoginUserId()));
    }

    @PostMapping("/{accountId}/claim")
    @Operation(summary = "领取会员权益")
    @Parameter(name = "accountId", description = "权益账户编号", required = true, example = "1024")
    public CommonResult<Boolean> claim(@PathVariable Long accountId,
                                      @RequestParam("requestId") @NotBlank String requestId) {
        benefitService.claim(getLoginUserId(), accountId, requestId);
        return success(true);
    }

    @PostMapping("/{accountId}/use")
    @Operation(summary = "使用会员权益")
    public CommonResult<Boolean> use(@PathVariable Long accountId, @RequestParam("bizId") @NotBlank String bizId) {
        benefitService.use(getLoginUserId(), accountId, bizId);
        return success(true);
    }

}
