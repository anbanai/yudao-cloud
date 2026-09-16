package cn.iocoder.yudao.module.member.service.point;

import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointRecordStatusEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberPointRecordServiceImplTest {

    @Test
    void createPendingPointRecord_doesNotChangeAvailableBalance() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11"))
                .thenReturn(null);
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.createPendingPointRecord(2L, 12, MemberPointBizTypeEnum.ORDER_GIVE_PENDING, "11");

        verify(userService, never()).updateUserPoint(any(), any());
        ArgumentCaptor<MemberPointRecordDO> captor = ArgumentCaptor.forClass(MemberPointRecordDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(12, captor.getValue().getPoint());
        assertEquals(5, captor.getValue().getTotalPoint());
        assertEquals("订单积分奖励", captor.getValue().getTitle());
        assertEquals(MemberPointRecordStatusEnum.PENDING.getStatus(), captor.getValue().getStatus());
    }

    @Test
    void effectPendingPointRecord_updatesBalanceAndIsIdempotent() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L).setPoint(12)
                .setStatus(MemberPointRecordStatusEnum.PENDING.getStatus());
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11"))
                .thenReturn(record);
        when(mapper.updateStatus(7L, MemberPointRecordStatusEnum.PENDING.getStatus(),
                MemberPointRecordStatusEnum.EFFECTIVE.getStatus())).thenReturn(1);
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(userService.updateUserPoint(2L, 12)).thenReturn(true);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.effectPendingPointRecord(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING, "11");
        record.setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus());
        service.effectPendingPointRecord(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING, "11");

        verify(userService, times(1)).updateUserPoint(2L, 12);
        ArgumentCaptor<MemberPointRecordDO> captor = ArgumentCaptor.forClass(MemberPointRecordDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(17, captor.getValue().getTotalPoint());
    }

    @Test
    void cancelPendingPointRecord_doesNotChangeAvailableBalance() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L)
                .setStatus(MemberPointRecordStatusEnum.PENDING.getStatus());
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11"))
                .thenReturn(record);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.cancelPendingPointRecord(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING, "11");

        verify(userService, never()).updateUserPoint(any(), any());
        verify(mapper).updateStatus(7L, MemberPointRecordStatusEnum.PENDING.getStatus(),
                MemberPointRecordStatusEnum.CANCELLED.getStatus());
    }

    @Test
    void refundOrderItemPointRecord_cancelsPendingPointWithoutChangingBalance() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L).setPoint(12)
                .setStatus(MemberPointRecordStatusEnum.PENDING.getStatus());
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11")).thenReturn(record);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.refundOrderItemPointRecord(2L, "11");

        verify(mapper).updateStatus(7L, MemberPointRecordStatusEnum.PENDING.getStatus(),
                MemberPointRecordStatusEnum.CANCELLED.getStatus());
        verify(userService, never()).updateUserPoint(any(), any());
        verify(mapper, never()).insert(any(MemberPointRecordDO.class));
    }

    @Test
    void refundOrderItemPointRecord_deductsEffectivePointUsingRecordedAmount() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L).setPoint(12)
                .setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus());
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11")).thenReturn(record);
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), "11")).thenReturn(null);
        when(userService.updateUserPoint(2L, -12)).thenReturn(true);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.refundOrderItemPointRecord(2L, "11");

        verify(userService).updateUserPoint(2L, -12);
        ArgumentCaptor<MemberPointRecordDO> captor = ArgumentCaptor.forClass(MemberPointRecordDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), captor.getValue().getBizType());
        assertEquals("11", captor.getValue().getBizId());
        assertEquals(-12, captor.getValue().getPoint());
        assertEquals(-7, captor.getValue().getTotalPoint());
        assertEquals(MemberPointRecordStatusEnum.EFFECTIVE.getStatus(), captor.getValue().getStatus());
    }

    @Test
    void refundOrderItemPointRecord_isIdempotentWhenRollbackExists() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L).setPoint(12)
                .setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus());
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11")).thenReturn(record);
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), "11"))
                .thenReturn(new MemberPointRecordDO().setId(8L));
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.refundOrderItemPointRecord(2L, "11");

        verify(userService, never()).updateUserPoint(any(), any());
        verify(mapper, never()).insert(any(MemberPointRecordDO.class));
    }

    @Test
    void refundOrderItemPointRecord_doesNothingForCancelledPoint() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        MemberPointRecordDO record = new MemberPointRecordDO().setId(7L).setPoint(12)
                .setStatus(MemberPointRecordStatusEnum.CANCELLED.getStatus());
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(mapper.selectByUserIdAndBizTypeAndBizId(2L,
                MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11")).thenReturn(record);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.refundOrderItemPointRecord(2L, "11");

        verify(mapper, never()).updateStatus(anyLong(), anyInt(), anyInt());
        verify(userService, never()).updateUserPoint(any(), any());
        verify(mapper, never()).insert(any(MemberPointRecordDO.class));
    }

    @Test
    void createPointRecord_rewardRollbackStillRecordsWhenBalanceIsInsufficient() {
        MemberPointRecordMapper mapper = mock(MemberPointRecordMapper.class);
        MemberUserService userService = mock(MemberUserService.class);
        when(userService.getUserForUpdate(2L)).thenReturn(new MemberUserDO().setPoint(5));
        when(userService.updateUserPoint(2L, -12)).thenReturn(true);
        MemberPointRecordServiceImpl service = service(mapper, userService);

        service.createPointRecord(2L, -12, MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM, "11");

        verify(userService).updateUserPoint(2L, -12);
        ArgumentCaptor<MemberPointRecordDO> captor = ArgumentCaptor.forClass(MemberPointRecordDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(-7, captor.getValue().getTotalPoint());
        assertEquals(MemberPointRecordStatusEnum.EFFECTIVE.getStatus(), captor.getValue().getStatus());
    }

    private static MemberPointRecordServiceImpl service(MemberPointRecordMapper mapper,
                                                         MemberUserService userService) {
        MemberPointRecordServiceImpl service = new MemberPointRecordServiceImpl();
        ReflectionTestUtils.setField(service, "memberPointRecordMapper", mapper);
        ReflectionTestUtils.setField(service, "memberUserService", userService);
        return service;
    }
}
