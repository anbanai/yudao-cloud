package cn.iocoder.yudao.module.member.service.config;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.member.controller.admin.config.vo.MemberConfigSaveReqVO;
import cn.iocoder.yudao.module.member.convert.config.MemberConfigConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.config.MemberConfigDO;
import cn.iocoder.yudao.module.member.dal.mysql.config.MemberConfigMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointGiveTimingEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 会员配置 Service 实现类
 *
 * @author QingX
 */
@Service
@Validated
public class MemberConfigServiceImpl implements MemberConfigService {

    @Resource
    private MemberConfigMapper memberConfigMapper;

    @Override
    public void saveConfig(MemberConfigSaveReqVO saveReqVO) {
        MemberConfigDO dbConfig = getConfig();
        if (saveReqVO.getPointTradeGiveTiming() == null) {
            Integer pointGiveTiming = dbConfig == null ? null : dbConfig.getPointTradeGiveTiming();
            saveReqVO.setPointTradeGiveTiming(pointGiveTiming != null
                    ? pointGiveTiming : MemberPointGiveTimingEnum.PAY.getType());
        } else if (MemberPointGiveTimingEnum.getByType(saveReqVO.getPointTradeGiveTiming()) == null) {
            throw new IllegalArgumentException("不支持的积分发放时机");
        }
        // 存在，则进行更新
        if (dbConfig != null) {
            memberConfigMapper.updateById(MemberConfigConvert.INSTANCE.convert(saveReqVO).setId(dbConfig.getId()));
            return;
        }
        // 不存在，则进行插入
        memberConfigMapper.insert(MemberConfigConvert.INSTANCE.convert(saveReqVO));
    }

    @Override
    public MemberConfigDO getConfig() {
        List<MemberConfigDO> list = memberConfigMapper.selectList();
        return CollectionUtils.getFirst(list);
    }

}
