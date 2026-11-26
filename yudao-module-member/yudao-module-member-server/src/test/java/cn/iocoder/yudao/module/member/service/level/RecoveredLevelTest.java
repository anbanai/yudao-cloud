package cn.iocoder.yudao.module.member.service.level;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.convert.level.MemberLevelConvert;
import cn.iocoder.yudao.module.member.convert.user.MemberUserConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(MemberLevelServiceImpl.class)
class RecoveredLevelTest extends BaseDbUnitTest {
    @Resource private MemberLevelService levels;
    @Resource private MemberUserMapper users;
    @Resource private javax.sql.DataSource dataSource;
    @Resource private PlatformTransactionManager transactions;
    @MockBean private MemberUserService userService;
    @MockBean private MemberExperienceRecordService experienceRecords;
    @MockBean private MemberLevelRecordService levelRecords;

    @BeforeEach
    void seed() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int[] ids = {95, 42, 37, 23, 71};
        int[] thresholds = {0, 100, 1000, 5000, 10000};
        for (int index = 0; index < ids.length; index++) {
            jdbc.update("INSERT INTO member_level (id,name,level,experience,discount_percent,icon,background_url) VALUES (?,?,?,?,?,'','')",
                    ids[index], "L" + (index + 1), index + 1, thresholds[index], 100 - index);
        }
        jdbc.update("INSERT INTO member_user (id,mobile,status,register_ip,experience,level_id,point) VALUES (11,'synthetic',0,'test',120,42,30)");
        when(userService.getUserForUpdate(11L)).thenAnswer(call -> users.selectByIdForUpdate(11L));
        doAnswer(call -> users.updateById(new MemberUserDO().setId(11L).setLevelId(call.getArgument(1)).setExperience(call.getArgument(2))))
                .when(userService).updateUserLevel(eq(11L), anyLong(), anyInt());
    }

    private void restore(long tier) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> levels.restoreHigherLevel(11L, tier));
    }

    private void check(long tier, int growth) {
        MemberUserDO user = users.selectById(11L);
        assertEquals(tier, user.getLevelId());
        assertEquals(growth, user.getExperience());
        assertEquals(30, user.getPoint());
    }

    @Test
    void restoredFloorSurvivesEarnAndRefund() {
        restore(37); restore(37);
        check(37, 120);
        verifyNoInteractions(experienceRecords);
        verify(levelRecords).createLevelRecord(argThat(record -> record.getExperience() == 0 && record.getUserExperience() == 120));
        levels.addExperience(11L, 1, MemberExperienceBizTypeEnum.SIGN_IN, "earn");
        check(37, 121);
        levels.addExperience(11L, 5000, MemberExperienceBizTypeEnum.ORDER_GIVE, "order");
        check(23, 5121);
        levels.addExperience(11L, 5100, MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL, "refund");
        check(37, 21);
    }

    @Test
    void lowerSnapshotDoesNotProtectOrdinaryAccountFromRecalculation() {
        restore(95);
        check(42, 120);
        assertNull(users.selectById(11L).getRecoveredFloorLevelId());
        levels.addExperience(11L, 100, MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL, "refund");
        check(95, 20);
    }

    @Test
    void failedAuditRollsBackFloorAndLevel() {
        doThrow(new IllegalStateException("synthetic audit failure")).when(levelRecords).createLevelRecord(any(MemberLevelRecordDO.class));
        assertThrows(IllegalStateException.class, () -> restore(37));
        check(42, 120);
        assertNull(users.selectById(11L).getRecoveredFloorLevelId());
        verifyNoInteractions(experienceRecords);
    }

    @Test
    void fiveConfiguredTiersUseIdsAndActualGrowthNotPoints() {
        var list = MemberLevelConvert.INSTANCE.convertList02(levels.getEnableLevelList());
        assertEquals(java.util.List.of(95L,42L,37L,23L,71L), list.stream().map(row -> row.getId()).toList());
        assertEquals(1000, list.get(2).getExperience());
        assertEquals(1000, list.get(2).getPointTradeGiveMultiplier());
        restore(37);
        var info = MemberUserConvert.INSTANCE.convert(users.selectById(11L), levels.getLevel(37L));
        assertEquals(37L, info.getLevel().getId());
        assertEquals(98, info.getLevel().getDiscountPercent());
        assertEquals(120, info.getExperience());
        assertEquals(30, info.getPoint());
    }
}
