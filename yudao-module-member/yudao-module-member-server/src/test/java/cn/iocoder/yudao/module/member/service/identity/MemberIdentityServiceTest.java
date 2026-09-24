package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberIdentityServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private IdentityBridgeProperties config;
    private IdentityRepository repository;
    private JdbcTemplate jdbc;
    private IdentityCenterClient center;
    private IdentityWechatClient wechat;
    private MemberUserService users;
    private SmsCodeApi sms;
    private MemberIdentityService service;
    private MemberUserDO user;
    private final String requestId = "request-0000000001";

    @BeforeEach void setup() throws Exception {
        config = new IdentityBridgeProperties();
        config.setEnabled(true); config.setBackendInstance("B-server"); config.setAppId("wxB"); config.setTenantId(1L);
        config.setSourceAppIds(Set.of("wxA"));
        config.setCenterUrl("https://identity.example.invalid"); config.setAppSecret("B-secret"); config.setKeyId("key");
        config.setHmacSecret("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        config.setIdentityOperatorName("operator"); config.setPhoneSharingConsentText("Explicit phone sharing purpose");
        config.setPhoneSharingPrivacyUrl("https://identity.example.invalid/privacy"); config.setPhoneSharingPolicyVersion("1");
        config.setEncryptionKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        DriverManagerDataSource datasource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("sql/mysql/identity-bridge/001_schema.sql"))) { root = root.getParent(); }
        String schema = Files.readString(root.resolve("sql/mysql/identity-bridge/001_schema.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "").replaceAll("(?m)^--.*$", "");
        for (String statement : schema.split(";")) { if (!statement.isBlank()) { jdbc.execute(statement); } }
        jdbc.update("INSERT INTO member_identity_scope(scope,ready) VALUES('1:wxB:B-server',1)");
        repository = new IdentityRepository(jdbc);
        center = mock(IdentityCenterClient.class); wechat = mock(IdentityWechatClient.class);
        users = mock(MemberUserService.class); sms = mock(SmsCodeApi.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), MemberUserDO.class);
        MemberUserMapper userMapper = mock(MemberUserMapper.class);
        user = new MemberUserDO().setId(10L).setStatus(0);
        when(users.getUser(10L)).thenReturn(user);
        when(userMapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(invocation -> { user.setMobile(null); return 1; });
        when(users.createUser(any(), any(), any(), any())).thenReturn(user);
        when(userMapper.updateById(any(MemberUserDO.class))).thenAnswer(invocation -> {
            MemberUserDO update = invocation.getArgument(0); user.setMobile(update.getMobile()); return 1;
        });
        when(users.getUserByMobile(anyString())).thenAnswer(invocation -> Objects.equals(user.getMobile(), invocation.getArgument(0)) ? user : null);
        when(sms.isIdentityVerificationSafe()).thenReturn(CommonResult.success(true));
        when(wechat.exchange(anyString())).thenReturn("openid-B");
        when(wechat.verifiedPhone(anyString())).thenReturn("13800000000");
        when(center.post(eq("claim"), anyMap())).thenReturn(node("{\"handoff_id\":\"h1\",\"subject_id\":\"s1\",\"source_app_id\":\"wxA\",\"phone\":null}"));
        when(center.post(eq("complete"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"phone\":null}"));
        when(center.post(eq("resolve"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"linked\":true,\"phone\":null}"));
        when(center.post(eq("phone"), anyMap())).thenReturn(node("{\"accepted\":true,\"phone_version\":1}"));
        service = new MemberIdentityService(config, repository, center, wechat, users, userMapper,
                new DataSourceTransactionManager(datasource), sms);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private JsonNode node(String value) throws Exception { return json.readTree(value); }
    private MemberIdentityService.Login bridge() { return service.bridge("opaque", "fresh", requestId, "wxA"); }
    private MemberIdentityService.Status sharePhone(boolean enabled) {
        return service.setPhoneSharing(10L, enabled, enabled ? service.status(10L).phoneSharingConsentRevision() : null);
    }
    private int events(String state) { return jdbc.queryForObject("SELECT COUNT(*) FROM member_identity_outbox WHERE state=?", Integer.class, state); }

    @Test void disabledNeverCallsWechatOrCenter() {
        config.setEnabled(false);
        assertThrows(RuntimeException.class, this::bridge);
        verifyNoInteractions(wechat, center);
    }
    @Test void tenantCannotOverrideRuntimeScope() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, this::bridge);
        verifyNoInteractions(wechat, center);
    }
    @Test void fixedSmsConfigurationBlocksSharedPhoneAuthentication() {
        when(sms.isIdentityVerificationSafe()).thenReturn(CommonResult.success(false));
        assertThrows(RuntimeException.class, this::bridge);
        verifyNoInteractions(wechat, center);
    }
    @Test void unknownSourceAppCannotRedeem() {
        assertThrows(RuntimeException.class, () -> service.bridge("opaque", "fresh", requestId, "other"));
        verifyNoInteractions(wechat, center);
    }
    @Test void centerSourceMustMatchReferrer() throws Exception {
        when(center.post(eq("claim"), anyMap())).thenReturn(node("{\"handoff_id\":\"h1\",\"subject_id\":\"s1\",\"source_app_id\":\"other\"}"));
        assertThrows(RuntimeException.class, this::bridge);
        assertNull(repository.account("1:wxB:B-server", "openid-B"));
    }
    @Test void unreviewedHistoryBlocksMembershipCreation() {
        jdbc.update("UPDATE member_identity_scope SET ready=0");
        assertThrows(RuntimeException.class, this::bridge);
        verify(users, never()).createUser(any(), any(), any(), any());
    }
    @Test void noPhoneStillCreatesTechnicalMemberAndDurableCompletion() {
        assertEquals(10L, bridge().memberId());
        assertEquals("COMPLETED", IdentityRepository.string(repository.operation("1:wxB:B-server", requestId), "state"));
        assertEquals(1L, IdentityRepository.number(repository.member("1:wxB:B-server", 10L), "linked"));
        assertNull(user.getMobile());
    }
    @Test void remoteCompleteTimeoutLeavesReservationAndRetryRecoversSameMember() throws Exception {
        when(center.post(eq("complete"), anyMap())).thenThrow(IdentityPolicy.unavailable())
                .thenReturn(node("{\"subject_id\":\"s1\",\"phone\":null}"));
        assertThrows(RuntimeException.class, this::bridge);
        assertEquals("CLAIMED", IdentityRepository.string(repository.operation("1:wxB:B-server", requestId), "state"));
        assertEquals(10L, service.bridge("opaque", "new-code", requestId, "wxA").memberId());
        verify(users, times(1)).createUser(any(), any(), any(), any());
        verify(wechat).exchange("new-code");
    }
    @Test void operationCannotReplayToDifferentOpenid() {
        bridge(); when(wechat.exchange(anyString())).thenReturn("other-openid");
        assertThrows(RuntimeException.class, this::bridge);
        assertNull(repository.account("1:wxB:B-server", "other-openid"));
    }
    @Test void subjectCannotBindToSecondOpenid() {
        bridge(); when(wechat.exchange(anyString())).thenReturn("other-openid");
        assertThrows(RuntimeException.class, () -> service.bridge("opaque2", "fresh", "request-0000000002", "wxA"));
        assertEquals("openid-B", IdentityRepository.string(repository.subject("1:wxB:B-server", "s1"), "openid"));
    }
    @Test void verifiedCenterPhoneSatisfiesMemberPhoneWithoutWechatPrompt() throws Exception {
        JsonNode phone = node("{\"subject_id\":\"s1\",\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100,\"version\":1}}");
        when(center.post(eq("complete"), anyMap())).thenReturn(phone);
        when(center.post(eq("resolve"), anyMap())).thenReturn(((com.fasterxml.jackson.databind.node.ObjectNode) phone.deepCopy()).put("linked", true));
        bridge();
        assertEquals("13800000000", user.getMobile());
        assertTrue(service.status(10L).phoneVerified());
        verify(wechat, never()).verifiedPhone(any());
    }
    @Test void importedExistingPhoneDoesNotBecomeProof() throws Exception {
        service.ordinary("fresh"); user.setMobile("13800000000");
        when(center.post(eq("complete"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100}}"));
        assertThrows(RuntimeException.class, this::bridge);
        assertNull(IdentityRepository.string(repository.member("1:wxB:B-server", 10L), "phone_cipher"));
    }
    @Test void otherMemberWithSamePhoneCannotBeMerged() throws Exception {
        when(users.getUserByMobile("13800000000")).thenReturn(new MemberUserDO().setId(99L));
        when(center.post(eq("complete"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100}}"));
        assertThrows(RuntimeException.class, this::bridge);
        assertNull(user.getMobile());
    }
    @Test void localVerifiedPhoneRequiresConsentBeforeOutbox() {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        assertEquals(0, events("PENDING"));
        sharePhone(true);
        assertEquals(1, events("PENDING"));
        String cipher = jdbc.queryForObject("SELECT payload_cipher FROM member_identity_outbox", String.class);
        assertFalse(cipher.contains("13800000000"));
    }
    @Test void revokeCancelsPendingAndStopsDispatch() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        sharePhone(false); service.retryOutbox();
        assertEquals(1, events("CANCELLED")); assertEquals(1, events("SENT"));
        verify(center).post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent")) && !payload.containsKey("phone")));
    }
    @Test void localConsentRevocationWorksWhileCenterIsOffline() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        when(center.post(eq("resolve"), anyMap())).thenThrow(IdentityPolicy.unavailable());
        assertFalse(sharePhone(false).phoneSharing());
        assertEquals(1, events("CANCELLED"));
    }
    @Test void revokedCenterGrantStopsDispatch() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        when(center.post(eq("resolve"), anyMap())).thenReturn(node("{\"linked\":false,\"phone\":null}"));
        service.retryOutbox();
        assertEquals(1, events("CANCELLED")); verify(center, never()).post(eq("phone"), anyMap());
    }
    @Test void transientPhoneFailureIsDurableAndRetried() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        when(center.post(eq("phone"), anyMap())).thenThrow(IdentityPolicy.unavailable()).thenReturn(node("{\"accepted\":true}"));
        service.retryOutbox(); assertEquals(1, events("PENDING"));
        assertEquals(1L, jdbc.queryForObject("SELECT attempts FROM member_identity_outbox", Long.class));
        jdbc.update("UPDATE member_identity_outbox SET next_attempt=0");
        service.retryOutbox(); assertEquals(1, events("SENT"));
    }
    @Test void conflictingVerifiedPhonePreservesOldMember() {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        when(wechat.verifiedPhone(any())).thenReturn("13900000000");
        assertThrows(RuntimeException.class, () -> service.verifyWechatPhone(10L, "new-phone-code"));
        assertEquals("13800000000", user.getMobile());
    }
    @Test void sentOriginPhoneIsWithdrawnWithNewVersionWithoutReverification() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        service.retryOutbox(); assertEquals(1, events("SENT"));
        sharePhone(false); service.retryOutbox();
        assertEquals(2, events("SENT"));
        verify(center).post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent"))
                && Long.valueOf(2).equals(payload.get("version")) && !payload.containsKey("phone")));
        assertEquals("13800000000", user.getMobile()); // Independent B proof is retained.
    }
    @Test void centerPhoneIsClearedAfterRemotePhoneScopeRevocation() throws Exception {
        when(center.post(eq("complete"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100,\"version\":1}}"));
        bridge(); assertEquals("13800000000", user.getMobile());
        assertFalse(service.status(10L).phoneVerified());
        assertNull(user.getMobile());
        assertNull(IdentityRepository.string(repository.member("1:wxB:B-server", 10L), "phone_cipher"));
    }
    @Test void localProofSurvivesRemoteIdentityRevocationAndOrdinaryLoginStillWorks() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        when(center.post(eq("resolve"), anyMap())).thenReturn(node("{\"linked\":false,\"phone\":null}"));
        assertEquals(10L, service.ordinary("new-wechat-proof").memberId());
        assertEquals("13800000000", user.getMobile());
        assertTrue(service.status(10L).phoneVerified());
        assertFalse(service.status(10L).linked());
    }
    @Test void centerOutagePreservesOrdinaryMemberAccessAndLocalPhoneProof() {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        when(center.post(eq("resolve"), anyMap())).thenThrow(IdentityPolicy.unavailable());
        assertEquals(10L, service.ordinary("fresh-during-outage").memberId());
        assertEquals("13800000000", user.getMobile());
        assertFalse(service.status(10L).linked()); assertTrue(service.status(10L).phoneVerified());
    }
    @Test void centerOutageHidesSharedOnlyPhoneButKeepsOrdinaryMemberAccess() throws Exception {
        when(center.post(eq("complete"), anyMap())).thenReturn(node("{\"subject_id\":\"s1\",\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100,\"version\":1}}"));
        bridge(); assertEquals("13800000000", user.getMobile());
        when(center.post(eq("resolve"), anyMap())).thenThrow(IdentityPolicy.unavailable());
        assertEquals(10L, service.ordinary("fresh-during-outage").memberId());
        assertNull(user.getMobile()); assertFalse(service.status(10L).phoneVerified());
    }
    @Test void delayedResolveCannotRestorePhoneAfterNewerRevocation() throws Exception {
        JsonNode active = node("{\"subject_id\":\"s1\",\"linked\":true,\"phone\":{\"number\":\"13800000000\",\"country_code\":\"86\",\"verified_at\":100,\"version\":1}}");
        when(center.post(eq("complete"), anyMap())).thenReturn(active);
        bridge();
        var firstEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        var secondEntered = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(center.post(eq("resolve"), anyMap())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(3, java.util.concurrent.TimeUnit.SECONDS));
                return active;
            }
            secondEntered.countDown();
            return node("{\"subject_id\":\"s1\",\"linked\":true,\"phone\":null}");
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        Runnable resolve = () -> {
            TenantContextHolder.setTenantId(1L);
            try { service.resolve(10L); } finally { TenantContextHolder.clear(); }
        };
        try {
            var first = pool.submit(resolve);
            assertTrue(firstEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            var second = pool.submit(resolve);
            assertFalse(secondEntered.await(150, java.util.concurrent.TimeUnit.MILLISECONDS), "Second center read must wait until the first response is applied");
            releaseFirst.countDown();
            first.get(3, java.util.concurrent.TimeUnit.SECONDS); second.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertNull(user.getMobile());
            assertNull(IdentityRepository.string(repository.member("1:wxB:B-server", 10L), "phone_cipher"));
        } finally { releaseFirst.countDown(); pool.shutdownNow(); }
    }
    @Test void changedPolicyRequiresNewExplicitConsent() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        config.setPhoneSharingPolicyVersion("next-policy");
        assertFalse(service.status(10L).phoneSharing());
        service.retryOutbox(); assertEquals(1, events("CANCELLED"));
        verify(center, never()).post(eq("phone"), argThat(payload -> Boolean.TRUE.equals(payload.get("share_consent"))));
        sharePhone(true); service.retryOutbox(); assertEquals(2, events("SENT"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM member_identity_consent", Integer.class));
    }
    @Test void missingDisplayedConsentRevisionCannotEnableSharing() {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.setPhoneSharing(10L, true, null));
        assertEquals(0, events("PENDING"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"operator", "text", "privacy"})
    void sameVersionDisclosureChangeWithdrawsSentConsent(String field) {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        service.retryOutbox(); assertEquals(1, events("SENT"));
        switch (field) {
            case "operator" -> config.setIdentityOperatorName("changed operator");
            case "text" -> config.setPhoneSharingConsentText("changed purpose");
            case "privacy" -> config.setPhoneSharingPrivacyUrl("https://identity.example.invalid/new-privacy");
        }
        service.retryOutbox();
        assertEquals(0L, IdentityRepository.number(repository.member("1:wxB:B-server", 10L), "phone_sharing"));
        assertEquals(2, events("SENT"));
    }
    @Test void changedPolicyWithdrawsSentConsentWithoutMemberVisit() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        service.retryOutbox(); assertEquals(1, events("SENT"));
        config.setPhoneSharingPolicyVersion("next-policy");
        when(center.post(eq("phone"), anyMap())).thenThrow(IdentityPolicy.unavailable())
                .thenReturn(node("{\"accepted\":true,\"phone_version\":1}"));

        service.retryOutbox();
        assertEquals(0L, IdentityRepository.number(repository.member("1:wxB:B-server", 10L), "phone_sharing"));
        assertEquals(1, events("PENDING"));
        Map<String, Object> withdrawal = repository.one("SELECT * FROM member_identity_outbox WHERE state='PENDING'");
        assertEquals(0L, IdentityRepository.number(withdrawal, "share_consent"));
        assertEquals(2L, IdentityRepository.number(withdrawal, "version"));
        assertNull(withdrawal.get("payload_cipher"));
        assertEquals("13800000000", user.getMobile());

        jdbc.update("UPDATE member_identity_outbox SET next_attempt=0 WHERE state='PENDING'");
        service.retryOutbox(); assertEquals(2, events("SENT"));
        service.retryOutbox(); assertEquals(2, events("SENT"));
        sharePhone(true); service.retryOutbox();
        assertEquals(3, events("SENT"));
        assertEquals(3L, IdentityRepository.number(repository.member("1:wxB:B-server", 10L), "origin_version"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"operator", "text", "privacy", "version"})
    void staleDisplayedDisclosureCannotAuthorizeCurrentTerms(String field) {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        MemberIdentityService.Status displayed = service.status(10L);
        assertTrue(displayed.phoneSharingConsentRevision().matches("[0-9a-f]{64}"));
        switch (field) {
            case "operator" -> config.setIdentityOperatorName("changed operator");
            case "text" -> config.setPhoneSharingConsentText("changed purpose");
            case "privacy" -> config.setPhoneSharingPrivacyUrl("https://identity.example.invalid/new-privacy");
            case "version" -> config.setPhoneSharingPolicyVersion("2");
        }
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.setPhoneSharing(10L, true, displayed.phoneSharingConsentRevision()));
        assertEquals(1004019001, error.getCode());
        assertEquals(0, events("PENDING"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_identity_consent", Integer.class));

        MemberIdentityService.Status current = service.status(10L);
        assertNotEquals(displayed.phoneSharingConsentRevision(), current.phoneSharingConsentRevision());
        assertTrue(service.setPhoneSharing(10L, true, current.phoneSharingConsentRevision()).phoneSharing());
        assertEquals(current.phoneSharingConsentRevision(), IdentityRepository.string(repository.member("1:wxB:B-server", 10L), "phone_consent_revision"));
        Map<String, Object> receipt = repository.one("SELECT * FROM member_identity_consent");
        assertEquals(current.phoneSharingConsentRevision(), receipt.get("consent_revision"));
        assertEquals(current.phoneSharingConsentText(), receipt.get("consent_text"));
        assertEquals(current.identityOperatorName(), receipt.get("operator_name"));
        assertEquals(current.phoneSharingPrivacyUrl(), receipt.get("privacy_url"));
        assertEquals(current.phoneSharingPolicyVersion(), receipt.get("policy_version"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void withdrawalDoesNotRequireCurrentDisclosureRevision(boolean missingRevision) {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        String displayedRevision = sharePhone(true).phoneSharingConsentRevision();
        config.setIdentityOperatorName("changed operator");
        clearInvocations(center);
        assertFalse(service.setPhoneSharing(10L, false, missingRevision ? null : displayedRevision).phoneSharing());
        verify(center, never()).post(eq("resolve"), anyMap());
        assertEquals(1, events("PENDING"));
        assertEquals(0L, jdbc.queryForObject("SELECT share_consent FROM member_identity_outbox WHERE state='PENDING'", Long.class));
    }
    @Test void sameVersionDisclosureChangeCancelsUnsentPositiveEvent() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        config.setPhoneSharingConsentText("changed purpose");
        service.retryOutbox();
        assertEquals(1, events("CANCELLED"));
        assertEquals(1, events("SENT"));
        verify(center, never()).post(eq("phone"), argThat(payload -> Boolean.TRUE.equals(payload.get("share_consent"))));
    }
    @Test void policyWithdrawalIsBoundedAndScopeIsolated() {
        config.setPhoneSharingPolicyVersion("policy-2");
        for (long member = 1; member <= 51; member++) {
            jdbc.update("INSERT INTO member_identity_account(scope,openid,member_id,subject_id,linked,phone_sharing,phone_consent_version,origin_version) VALUES(?,?,?,?,1,1,?,7)",
                    "1:wxB:B-server", "openid-" + member, member, "subject-" + member, "policy-1");
        }
        jdbc.update("INSERT INTO member_identity_account(scope,openid,member_id,subject_id,linked,phone_sharing,phone_consent_version) VALUES(?,?,?,?,1,1,?)",
                "1:wxB:B-server", "current-openid", 100L, "current-subject", "policy-2");
        jdbc.update("UPDATE member_identity_account SET phone_consent_revision=? WHERE scope=? AND member_id=?",
                service.status(999L).phoneSharingConsentRevision(), "1:wxB:B-server", 100L);
        jdbc.update("INSERT INTO member_identity_account(scope,openid,member_id,subject_id,linked,phone_sharing,phone_consent_version) VALUES(?,?,?,?,1,1,?)",
                "2:wxC:C-server", "other-openid", 200L, "other-subject", "policy-1");

        service.retryOutbox();
        assertEquals(50, events("SENT"));
        assertEquals(50, jdbc.queryForObject("SELECT COUNT(*) FROM member_identity_account WHERE scope='1:wxB:B-server' AND phone_sharing=0 AND origin_version=8", Integer.class));
        service.retryOutbox();
        assertEquals(51, events("SENT"));
        assertEquals(1L, IdentityRepository.number(repository.member("1:wxB:B-server", 100L), "phone_sharing"));
        assertEquals(1L, IdentityRepository.number(repository.member("2:wxC:C-server", 200L), "phone_sharing"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_identity_outbox WHERE scope='2:wxC:C-server'", Integer.class));
    }
    @Test void policyVersionComparisonIsCaseSensitive() {
        bridge(); service.verifyWechatPhone(10L, "phone-code");
        config.setPhoneSharingPolicyVersion("Policy-v1");
        sharePhone(true); service.retryOutbox();
        config.setPhoneSharingPolicyVersion("policy-v1");

        service.retryOutbox();
        assertEquals(0L, IdentityRepository.number(repository.member("1:wxB:B-server", 10L), "phone_sharing"));
        assertEquals(2, events("SENT"));
    }
    @Test void acknowledgedNewerConsentTerminatesOlderWithdrawalRetry() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        service.retryOutbox();
        sharePhone(false);
        when(center.post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent")))))
                .thenThrow(IdentityPolicy.unavailable()).thenThrow(IdentityPolicy.conflict());
        service.retryOutbox(); assertEquals(1, events("PENDING"));

        sharePhone(true); service.retryOutbox(); assertEquals(2, events("SENT"));
        jdbc.update("UPDATE member_identity_outbox SET next_attempt=0 WHERE state='PENDING'");
        service.retryOutbox();
        assertEquals(0, events("PENDING"));
        assertEquals(1, events("CANCELLED"));
        verify(center, times(1)).post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent"))));

        sharePhone(false); service.retryOutbox();
        assertEquals(1, events("PENDING"));
        assertEquals(4L, jdbc.queryForObject("SELECT version FROM member_identity_outbox WHERE state='PENDING'", Long.class));
        when(center.post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent")))))
                .thenReturn(node("{\"accepted\":true,\"phone_version\":1}"));
        jdbc.update("UPDATE member_identity_outbox SET next_attempt=0 WHERE state='PENDING'");
        service.retryOutbox(); assertEquals(3, events("SENT"));
    }
    @Test void unacknowledgedNewerConsentDoesNotDiscardWithdrawal() throws Exception {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        service.retryOutbox(); sharePhone(false);
        when(center.post(eq("phone"), anyMap())).thenThrow(IdentityPolicy.unavailable());
        service.retryOutbox();
        sharePhone(true); service.retryOutbox(); assertEquals(2, events("PENDING"));

        when(center.post(eq("phone"), argThat(payload -> Boolean.FALSE.equals(payload.get("share_consent")))))
                .thenReturn(node("{\"accepted\":true,\"phone_version\":1}"));
        jdbc.update("UPDATE member_identity_outbox SET next_attempt=0 WHERE share_consent=0");
        service.retryOutbox();
        assertEquals(2, events("SENT"));
        assertEquals(1, events("PENDING"));
        assertEquals(0, events("CANCELLED"));
        assertEquals(3L, jdbc.queryForObject("SELECT version FROM member_identity_outbox WHERE state='PENDING'", Long.class));
    }
    @Test void reconsentReactivatesCancelledUnsentEvent() {
        bridge(); service.verifyWechatPhone(10L, "phone-code"); sharePhone(true);
        sharePhone(false); sharePhone(true);
        service.retryOutbox(); assertEquals(2, events("SENT"));
    }
}
