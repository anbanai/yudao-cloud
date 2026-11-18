package cn.iocoder.yudao.module.member.controller.app.benefits;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.benefits.vo.AppMemberBenefitRespVO;
import cn.iocoder.yudao.module.member.controller.app.benefits.vo.AppMemberBenefitsLevelRespVO;
import cn.iocoder.yudao.module.member.controller.app.benefits.vo.AppMemberBenefitsOverviewRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.config.MemberConfigService;
import cn.iocoder.yudao.module.member.service.level.MemberLevelService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 会员权益")
@RestController
@RequestMapping("/member/benefits")
@Validated
public class AppMemberBenefitsController {

    @Resource
    private MemberUserService userService;
    @Resource
    private MemberLevelService levelService;
    @Resource
    private MemberConfigService configService;

    @GetMapping("/overview")
    @Operation(summary = "获得当前会员等级和权益")
    public CommonResult<AppMemberBenefitsOverviewRespVO> getOverview() {
        MemberUserDO user = userService.getUser(getLoginUserId());
        MemberLevelDO level = levelService.getLevel(user.getLevelId());
        AppMemberBenefitsOverviewRespVO result = buildOverview(user, level);
        return success(result);
    }

    @GetMapping("/levels")
    @Operation(summary = "获得全部启用会员等级及对应权益")
    public CommonResult<List<AppMemberBenefitsLevelRespVO>> getLevels() {
        return success(levelService.getEnableLevelList().stream()
                .sorted(Comparator.comparing(MemberLevelDO::getLevel))
                .map(this::buildLevelBenefits)
                .collect(Collectors.toList()));
    }

    private AppMemberBenefitsOverviewRespVO buildOverview(MemberUserDO user, MemberLevelDO level) {
        AppMemberBenefitsOverviewRespVO result = new AppMemberBenefitsOverviewRespVO();
        result.setLevel(level == null ? 0 : level.getLevel());
        result.setLevelName(level == null ? "普通会员" : level.getName());
        result.setPoint(user.getPoint());
        result.setExperience(user.getExperience());
        result.setNextExperience(findNextExperience(level));
        result.setBenefits(level == null ? Collections.emptyList() : buildBenefits(level));
        result.setLevels(levelService.getEnableLevelList().stream()
                .sorted(Comparator.comparing(MemberLevelDO::getLevel))
                .map(this::buildLevelBenefits)
                .collect(Collectors.toList()));
        if (configService.getConfig() != null) {
            Integer pointGive = configService.getConfig().getPointTradeGivePoint();
            result.setPointTradeGiveMultiplier(pointGive == null ? 1000 : pointGive * 10);
        }
        return result;
    }

    private Integer findNextExperience(MemberLevelDO current) {
        return levelService.getEnableLevelList().stream()
                .filter(item -> current == null || item.getLevel() > current.getLevel())
                .map(MemberLevelDO::getExperience).min(Integer::compareTo).orElse(null);
    }

    private List<AppMemberBenefitRespVO> buildBenefits(MemberLevelDO level) {
        AppMemberBenefitRespVO discount = new AppMemberBenefitRespVO();
        discount.setCode("LEVEL_DISCOUNT");
        discount.setName("会员折扣");
        discount.setDescription("当前等级享受 " + (level.getDiscountPercent() == null ? 100 : level.getDiscountPercent()) + " 折优惠");
        return Collections.singletonList(discount);
    }

    private AppMemberBenefitsLevelRespVO buildLevelBenefits(MemberLevelDO level) {
        AppMemberBenefitsLevelRespVO result = new AppMemberBenefitsLevelRespVO();
        result.setLevel(level.getLevel());
        result.setName(level.getName());
        result.setExperience(level.getExperience());
        result.setBenefits(buildBenefits(level));
        return result;
    }
}
