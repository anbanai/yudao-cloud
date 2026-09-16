package cn.iocoder.yudao.module.member.service.config;

import cn.iocoder.yudao.module.member.controller.admin.config.vo.MemberConfigSaveReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.config.MemberConfigDO;
import cn.iocoder.yudao.module.member.dal.mysql.config.MemberConfigMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointGiveTimingEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberConfigServiceImplTest {

    @Test
    void saveConfig_preservesGiveTimingWhenLegacyRequestOmitsIt() {
        MemberConfigMapper mapper = mock(MemberConfigMapper.class);
        when(mapper.selectList()).thenReturn(List.of(MemberConfigDO.builder()
                .id(1L)
                .pointTradeGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType())
                .build()));
        MemberConfigServiceImpl service = new MemberConfigServiceImpl();
        ReflectionTestUtils.setField(service, "memberConfigMapper", mapper);
        MemberConfigSaveReqVO request = new MemberConfigSaveReqVO();
        request.setPointTradeDeductEnable(true);
        request.setPointTradeDeductUnitPrice(100);
        request.setPointTradeDeductMaxPrice(1000);
        request.setPointTradeGivePoint(10);

        service.saveConfig(request);

        ArgumentCaptor<MemberConfigDO> captor = ArgumentCaptor.forClass(MemberConfigDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(MemberPointGiveTimingEnum.RECEIVE.getType(), captor.getValue().getPointTradeGiveTiming());
    }

}
