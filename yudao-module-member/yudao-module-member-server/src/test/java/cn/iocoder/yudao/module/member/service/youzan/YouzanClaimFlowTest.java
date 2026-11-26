package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.controller.app.user.AppYouzanClaimController;
import cn.iocoder.yudao.module.member.controller.app.user.vo.AppMemberUserUpdateMobileByWeixinReqVO;
import cn.iocoder.yudao.module.member.service.importing.*;
import cn.iocoder.yudao.module.member.service.level.*;
import cn.iocoder.yudao.module.member.service.point.RecoveredPointCreditService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.jdbc.Sql;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import({YouzanAssetClaimService.class, YouzanClaimStore.class, RecoveredPointCreditService.class,
        MemberLevelServiceImpl.class, MemberLevelRecordServiceImpl.class, MemberYouzanImportService.class,
        YouzanClaimFlowTest.JdbcConfig.class, AppYouzanClaimController.class})
@TestPropertySource(properties = {"yudao.member.youzan.claim-source.1=9", "mybatis-plus.encryptor.password=synthetic-key-01"})
@Sql(statements="SELECT 1", executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class YouzanClaimFlowTest extends BaseDbUnitTest {
    static class JdbcConfig {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean @Lazy(false) com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantInterceptor(
                com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
            return new cn.iocoder.yudao.framework.tenant.config.YudaoTenantAutoConfiguration()
                    .tenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties(), interceptor);
        }
    }
    static final String PORT = System.getenv("CMP175_MYSQL_CONTAINER_PORT");
    @DynamicPropertySource static void disposableDatabase(DynamicPropertyRegistry props) {
        if (PORT == null) {
            props.add("spring.datasource.url", () -> "jdbc:h2:mem:cmp175;MODE=MYSQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=value;");
        } else {
            int port = Integer.parseInt(PORT);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local test port");
            props.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:"+port+"/cmp175_ephemeral?useSSL=false&allowPublicKeyRetrieval=true");
            props.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            props.add("spring.datasource.username", () -> "cmp175_fixture");
            props.add("spring.datasource.password", () -> System.getenv("CMP175_FIXTURE_PASSWORD"));
            props.add("spring.sql.init.mode", () -> "never");
        }
    }
    @Resource JdbcTemplate jdbc;
    @Resource MemberUserMapper users;
    @Resource MemberYouzanImportService imports;
    @Resource YouzanAssetClaimService claims;
    @Resource AppYouzanClaimController endpoint;
    @Resource MemberLevelService levelService;
    @MockBean WeixinClaimIdentityService weixin;
    @MockBean MemberUserService profiles;
    @MockBean MemberExperienceRecordService experience;
    private static final String MOBILE = "13800000001";

    @BeforeEach void seed() throws Exception {
        TenantContextHolder.setTenantId(1L);
        String tables = Files.readString(Path.of("../../sql/mysql/member_youzan_import.sql")).split("INSERT INTO `system_menu`")[0];
        for (String ddl : tables.replace("b'0'", "false").split(";")) if (!ddl.isBlank()) jdbc.execute(ddl);
        if (PORT == null) {
            jdbc.execute("alter table member_youzan_point_snapshot add column if not exists claimed_user_id bigint");
            jdbc.execute("alter table member_youzan_point_snapshot add column if not exists claimed_at timestamp");
        }
        jdbc.execute("create table if not exists member_point_record(id bigint auto_increment primary key,user_id bigint,biz_type int,biz_id varchar(64),point int,total_point int,title varchar(64),description varchar(255),status int,effective_time timestamp,point_calculate_price int,point_give_base int,point_give_multiplier int,creator varchar(64),updater varchar(64),create_time timestamp,update_time timestamp,deleted boolean default false,tenant_id bigint default 1)");
        jdbc.execute("create table if not exists member_level_record(id bigint auto_increment primary key,user_id bigint,level_id bigint,level int,discount_percent int,experience int,user_experience int,remark varchar(255),description varchar(255),creator varchar(64),updater varchar(64),create_time timestamp,update_time timestamp,deleted boolean default false,tenant_id bigint default 1)");
        for (String table : List.of("member_user","member_level","member_youzan_import_grant","member_youzan_point_snapshot","member_youzan_import_batch","member_point_record","member_level_record","member_point_batch")) jdbc.update("delete from " + table);
        jdbc.update("insert into member_youzan_import_grant values(1,9,2)");
        for (int tier = 1; tier <= 5; tier++) jdbc.update("insert into member_level (id,name,level,experience,discount_percent,icon,background_url,tenant_id) values(?,?,?,?,100,'','',1)", 100+tier,"L"+tier,tier,new int[]{0,100,1000,5000,10000}[tier-1]);
        jdbc.update("insert into member_user(id,mobile,status,register_ip,experience,level_id,point,tenant_id) values(11,'',0,'test',120,102,30,1)");
        when(profiles.getUserForUpdate(11L)).thenAnswer(call -> users.selectByIdForUpdate(11L));
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream(); var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            var sheet = book.createSheet(YouzanCustomerImportRules.SHEET_NAME);
            var header = sheet.createRow(0);
            var names = YouzanCustomerImportRules.HEADERS;
            for (int index = 0; index < names.size(); index++) header.createCell(index).setCellValue(names.get(index));
            var row = sheet.createRow(1);
            row.createCell(2).setCellValue(MOBILE); row.createCell(8).setCellValue("L3"); row.createCell(11).setCellValue("100");
            book.write(bytes);
            var batch = imports.preview(new MockMultipartFile("file","synthetic.xlsx","application/octet-stream",bytes.toByteArray()),9L);
            assertEquals(1,imports.confirm(batch.batchId()).importedRows());
        }
        assertEquals(30,scalar("select point from member_user where id=11"));
        assertEquals(0,scalar("select restore_status from member_youzan_point_snapshot"));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }
    int scalar(String sql) { return jdbc.queryForObject(sql,Integer.class); }
    void assertPaid() {
        assertEquals(130,scalar("select point from member_user where id=11"));
        assertEquals(120,scalar("select experience from member_user where id=11"));
        assertEquals(103,scalar("select level_id from member_user where id=11"));
        assertEquals(103,scalar("select recovered_floor_level_id from member_user where id=11"));
        assertEquals(1,scalar("select count(*) from member_point_record where user_id=11 and point=100 and total_point=130"));
        assertEquals(1,scalar("select count(*) from member_point_batch where user_id=11 and remaining_point=100 and expire_time>earned_time"));
        assertEquals(1,scalar("select count(*) from member_level_record where user_id=11 and experience=0"));
        assertEquals(1,scalar("select count(*) from member_youzan_point_snapshot where restore_status=1 and claimed_user_id=11 and claimed_at is not null"));
    }

    @Test void authorizedEndpointPaysOnce() {
        var request = new AppMemberUserUpdateMobileByWeixinReqVO(); request.setCode("synthetic-code");
        when(weixin.verifiedMobile(request.getCode())).thenReturn(MOBILE);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(11L);
            assertEquals("RESTORED",endpoint.recover(request).getCheckedData().status());
            assertEquals("ALREADY_RESTORED",endpoint.recover(request).getCheckedData().status());
        }
        assertPaid();
    }

    @Test void lastWriteFailureRollsBackEverything() {
        jdbc.execute("alter table member_youzan_point_snapshot add constraint test_stop check (restore_status = 0)");
        try { assertThrows(RuntimeException.class, () -> claims.recover(11L,MOBILE)); }
        finally { jdbc.execute("alter table member_youzan_point_snapshot drop constraint test_stop"); }
        assertEquals("",jdbc.queryForObject("select mobile from member_user where id=11",String.class));
        assertEquals(30,scalar("select point from member_user where id=11"));
        assertEquals(102,scalar("select level_id from member_user where id=11"));
        for (String table : List.of("member_point_record","member_point_batch","member_level_record")) assertEquals(0,scalar("select count(*) from "+table));
        claims.recover(11L,MOBILE); assertPaid();
    }

    @Test void ordinaryRecalculationRespectsOnlyTheRecoveredFloor() {
        claims.recover(11L,MOBILE); assertPaid();
        jdbc.update("insert into member_user(id,mobile,status,register_ip,experience,level_id,point,tenant_id) values(12,'',0,'test',120,103,30,2)");
        jdbc.update("update member_user set mobile='fixture',tenant_id=1 where id=12");
        when(profiles.getUserForUpdate(12L)).thenAnswer(call -> users.selectByIdForUpdate(12L));
        doAnswer(call -> jdbc.update("update member_user set level_id=?,experience=? where id=?",call.<Long>getArgument(1),call.<Integer>getArgument(2),call.<Long>getArgument(0)))
                .when(profiles).updateUserLevel(anyLong(),anyLong(),anyInt());
        for (long id : List.of(11L,12L)) levelService.addExperience(id,1,cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum.SIGN_IN,"synthetic-signin");
        assertEquals(103,scalar("select level_id from member_user where id=11"));
        assertEquals(102,scalar("select level_id from member_user where id=12"));
        assertEquals(121,scalar("select experience from member_user where id=11"));
    }

    @Test void concurrentClaimsPayOnce() throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        Callable<Integer> request = () -> { TenantContextHolder.setTenantId(1L);
            try { start.await(5,TimeUnit.SECONDS); return claims.recover(11L,MOBILE).addedPoint(); }
            finally { TenantContextHolder.clear(); } };
        try { var first = pool.submit(request); var second = pool.submit(request); start.countDown();
            assertEquals(100,first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS)); }
        finally { pool.shutdownNow(); }
        assertPaid();
    }

    @Test void deniedAndWrongSourceCannotBindOrPay() {
        var request = new AppMemberUserUpdateMobileByWeixinReqVO(); request.setCode("refused");
        when(weixin.verifiedMobile("refused")).thenThrow(new IllegalStateException("synthetic refusal"));
        assertThrows(RuntimeException.class,()->endpoint.recover(request));
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class,()->claims.recover(11L,MOBILE));
        TenantContextHolder.setTenantId(1L);
        jdbc.update("update member_youzan_point_snapshot set merchant_id=8");
        assertEquals("NOT_FOUND",claims.recover(11L,MOBILE).status());
        jdbc.update("update member_youzan_point_snapshot set merchant_id=9, encrypted_mobile='invalid'");
        assertThrows(RuntimeException.class,()->claims.recover(11L,MOBILE));
        assertEquals("",jdbc.queryForObject("select mobile from member_user where id=11",String.class));
        assertEquals(0,scalar("select count(*) from member_point_record"));
    }
}
