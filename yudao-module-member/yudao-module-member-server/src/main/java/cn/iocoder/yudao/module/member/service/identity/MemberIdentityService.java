package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import static cn.iocoder.yudao.module.member.service.identity.IdentityRepository.*;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;

@Service
@RequiredArgsConstructor
public class MemberIdentityService {
    private final IdentityBridgeProperties properties;
    private final IdentityRepository repository;
    private final IdentityCenterClient center;
    private final IdentityWechatClient wechat;
    private final MemberUserService users;
    private final MemberUserMapper userMapper;
    private final PlatformTransactionManager transactionManager;
    private final SmsCodeApi sms;

    public boolean enabled() { return properties.isEnabled(); }
    public void requireSafeSms() {
        if (enabled() && !Boolean.TRUE.equals(sms.isIdentityVerificationSafe().getCheckedData())) {
            throw IdentityPolicy.unavailable();
        }
    }
    private String scope() {
        if (!enabled() || !Objects.equals(properties.getTenantId(), TenantContextHolder.getTenantId())) {
            throw IdentityPolicy.unavailable();
        }
        try { properties.validateCurrentScope(); }
        catch (IllegalArgumentException invalidConfiguration) { throw IdentityPolicy.unavailable(); }
        return properties.scope();
    }
    private IdentityCrypto crypto() { return new IdentityCrypto(properties.getEncryptionKey()); }
    private <T> T locked(Supplier<T> action) {
        String scope = scope();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Local reservations must survive a remote timeout / enclosing legacy login transaction.
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> { repository.lockReadyScope(scope); return action.get(); });
    }
    public record Login(Long memberId, String openid) { }
    public record Status(boolean linked, boolean phoneSharing, boolean phoneVerified, String maskedPhone,
                         String phoneSharingConsentText, String phoneSharingPrivacyUrl,
                         String phoneSharingPolicyVersion, String identityOperatorName,
                         String phoneSharingConsentRevision) { }
    private record PhoneConsentDisclosure(String scope, String operator, String text, String privacyUrl, String policyVersion) {
        String revision() {
            StringBuilder canonical = new StringBuilder("member-phone-consent-v1:");
            for (String value : new String[]{scope, operator, text, privacyUrl, policyVersion}) {
                canonical.append(value.length()).append(':').append(value);
            }
            return IdentityCrypto.sha256(canonical.toString());
        }
    }
    private PhoneConsentDisclosure phoneConsentDisclosure() {
        return new PhoneConsentDisclosure(properties.scope(), properties.getIdentityOperatorName(),
                properties.getPhoneSharingConsentText(), properties.getPhoneSharingPrivacyUrl(), properties.getPhoneSharingPolicyVersion());
    }
    private Status statusValue(boolean linked, boolean sharing, boolean verified, String masked) {
        PhoneConsentDisclosure disclosure = phoneConsentDisclosure();
        return new Status(linked, sharing, verified, masked, disclosure.text(), disclosure.privacyUrl(),
                disclosure.policyVersion(), disclosure.operator(), disclosure.revision());
    }

    public Login bridge(String handoffCode, String loginCode, String requestId, String sourceAppId) {
        String scope = scope();
        requireSafeSms();
        if (!properties.getSourceAppIds().contains(sourceAppId)) { throw IdentityPolicy.unavailable(); }
        // Always prove control of B's OpenID, including every recovery attempt.
        String openid = wechat.exchange(loginCode);
        JsonNode claim = center.post("claim", Map.of("code", handoffCode, "request_id", requestId,
                "openid", openid, "source_app_id", sourceAppId));
        if (!sourceAppId.equals(claim.path("source_app_id").asText())
                || claim.path("subject_id").asText().isBlank() || claim.path("handoff_id").asText().isBlank()) {
            throw IdentityPolicy.unavailable();
        }
        String subject = claim.get("subject_id").asText();
        String handoff = claim.get("handoff_id").asText();
        Long member = locked(() -> {
            Map<String, Object> operation = repository.operation(scope, requestId);
            if (operation != null && (!openid.equals(string(operation, "openid"))
                    || !handoff.equals(string(operation, "handoff_id"))
                    || !IdentityCrypto.sha256(handoffCode).equals(string(operation, "code_hash")))) {
                throw IdentityPolicy.conflict();
            }
            Map<String, Object> account = repository.account(scope, openid);
            Map<String, Object> existingSubject = repository.subject(scope, subject);
            if (existingSubject != null && !openid.equals(string(existingSubject, "openid"))) { throw IdentityPolicy.conflict(); }
            if (account != null && string(account, "subject_id") != null
                    && !subject.equals(string(account, "subject_id"))) { throw IdentityPolicy.conflict(); }
            if (account == null) { account = createAccount(scope, openid); }
            Long memberId = number(account, "member_id");
            validateUser(memberId);
            checkIncomingPhone(account, claim.path("phone"));
            repository.update("UPDATE member_identity_account SET subject_id=? WHERE scope=? AND openid=?", subject, scope, openid);
            if (operation == null) {
                repository.update("INSERT INTO member_identity_operation(scope,request_id,handoff_id,code_hash,openid,member_id,state,created_at) VALUES(?,?,?,?,?,?,?,?)",
                        scope, requestId, handoff, IdentityCrypto.sha256(handoffCode), openid, memberId, "CLAIMED", System.currentTimeMillis());
            }
            return memberId;
        });
        // The center complete operation is idempotent. A crash here is recovered with same request + fresh B login code.
        locked(() -> {
            // Serialize remote reads and local application so an older complete cannot restore revoked data.
            JsonNode completed = center.post("complete", Map.of("handoff_id", handoff, "request_id", requestId,
                    "openid", openid, "member_id", member.toString()));
            if (!subject.equals(completed.path("subject_id").asText())) { throw IdentityPolicy.conflict(); }
            Map<String, Object> account = repository.account(scope, openid);
            applyCenterPhone(account, completed.path("phone"));
            repository.update("UPDATE member_identity_account SET linked=1 WHERE scope=? AND openid=?", scope, openid);
            repository.update("UPDATE member_identity_operation SET state='COMPLETED' WHERE scope=? AND request_id=?", scope, requestId);
            return null;
        });
        return new Login(member, openid);
    }
    public Login ordinary(String loginCode) {
        String scope = scope();
        requireSafeSms();
        String openid = wechat.exchange(loginCode);
        Long member = locked(() -> {
            Map<String, Object> account = repository.account(scope, openid);
            if (account == null) { account = createAccount(scope, openid); }
            Long id = number(account, "member_id"); validateUser(id); return id;
        });
        resolve(member);
        return new Login(member, openid);
    }
    public String currentOpenid(Long member) {
        if (!enabled()) { return null; }
        return string(repository.member(scope(), member), "openid");
    }
    public Login phoneLogin(String loginCode, String phoneCode) {
        Login login = ordinary(loginCode);
        verifyWechatPhone(login.memberId(), phoneCode);
        return login;
    }
    private Map<String, Object> createAccount(String scope, String openid) {
        MemberUserDO member = users.createUser(null, null, getClientIP(), TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal());
        repository.update("INSERT INTO member_identity_account(scope,openid,member_id,linked,phone_sharing,phone_version) VALUES(?,?,?,0,0,0)", scope, openid, member.getId());
        return repository.account(scope, openid);
    }
    private MemberUserDO validateUser(Long member) {
        MemberUserDO user = users.getUser(member);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) { throw IdentityPolicy.unavailable(); }
        return user;
    }
    private void checkIncomingPhone(Map<String, Object> account, JsonNode phone) {
        if (phone == null || phone.isNull() || phone.isMissingNode()) { return; }
        String incoming = phone.path("number").asText();
        if (!incoming.matches("1[0-9]{10}") || !("86".equals(phone.path("country_code").asText())
                || "+86".equals(phone.path("country_code").asText())) || phone.path("verified_at").asLong() <= 0) {
            throw IdentityPolicy.unavailable();
        }
        Long member = number(account, "member_id");
        String verified = crypto().decrypt(string(account, "phone_cipher"));
        IdentityPolicy.checkPhone(validateUser(member).getMobile(), verified, incoming);
        MemberUserDO samePhone = users.getUserByMobile(incoming);
        if (samePhone != null && !member.equals(samePhone.getId())) { throw IdentityPolicy.conflict(); }
    }
    private void applyCenterPhone(Map<String, Object> account, JsonNode phone) {
        if (phone == null || phone.isNull() || phone.isMissingNode()) { clearCenterPhone(account); return; }
        checkIncomingPhone(account, phone);
        if (phone.path("version").asLong() < number(account, "center_phone_version")) { return; }
        String incoming = phone.get("number").asText();
        long member = number(account, "member_id");
        userMapper.updateById(new MemberUserDO().setId(member).setMobile(incoming));
        if (java.util.Set.of("wechat", "sms").contains(Objects.toString(account.get("phone_method"), ""))) {
            repository.update("UPDATE member_identity_account SET center_phone_version=? WHERE scope=? AND member_id=?", phone.path("version").asLong(), scope(), member);
            return;
        }
        repository.update("UPDATE member_identity_account SET phone_cipher=?,phone_method=?,verified_at=?,center_phone_version=? WHERE scope=? AND member_id=?",
                crypto().encrypt(incoming), "center", phone.get("verified_at").asLong(), phone.path("version").asLong(), scope(), member);
    }
    private void clearCenterPhone(Map<String, Object> account) {
        if (!"center".equals(string(account, "phone_method"))) { return; }
        String cached = crypto().decrypt(string(account, "phone_cipher"));
        if (cached != null) {
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MemberUserDO>()
                    .eq(MemberUserDO::getId, number(account, "member_id"))
                    .eq(MemberUserDO::getMobile, cached).set(MemberUserDO::getMobile, null));
        }
        repository.update("UPDATE member_identity_account SET phone_cipher=NULL,phone_method=NULL,verified_at=NULL,center_phone_version=0 WHERE scope=? AND member_id=?",
                scope(), number(account, "member_id"));
    }
    private void withdrawSharing(Map<String, Object> account) {
        repository.update("UPDATE member_identity_outbox SET state='CANCELLED' WHERE scope=? AND member_id=? AND state='PENDING' AND share_consent=1",
                scope(), number(account, "member_id"));
        if (number(account, "phone_sharing") == 1) { enqueueWithdrawal(account); }
        repository.update("UPDATE member_identity_account SET phone_sharing=0 WHERE scope=? AND member_id=?", scope(), number(account, "member_id"));
    }
    public void resolve(Long member) {
        if (!enabled()) { return; }
        String scope = scope();
        // The cross-process scope lock spans center read AND local application. A delayed older response
        // cannot overwrite a newer revocation, including concurrent bridge completion or outbox dispatch.
        locked(() -> {
            Map<String, Object> current = repository.member(scope, member);
            if (current == null || string(current, "subject_id") == null) { return null; }
            JsonNode result;
            try {
                result = center.post("resolve", Map.of("openid", string(current, "openid"), "member_id", member.toString()));
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException failure) {
                if (failure.getCode() != 1004019002) { throw failure; }
                clearCenterPhone(current);
                repository.update("UPDATE member_identity_account SET linked=0 WHERE scope=? AND member_id=?", scope, member);
                return null;
            }
            if (number(current, "phone_sharing") == 1 && !consentCurrent(current)) { withdrawSharing(current); }
            if (result.path("linked").asBoolean()) {
                if (!string(current, "subject_id").equals(result.path("subject_id").asText())) { throw IdentityPolicy.conflict(); }
                applyCenterPhone(current, result.path("phone"));
                repository.update("UPDATE member_identity_account SET linked=1 WHERE scope=? AND member_id=?", scope, member);
                if (number(current, "linked") != 1 && consentCurrent(current)) {
                    current.put("linked", 1);
                    enqueue(current);
                }
            } else {
                clearCenterPhone(current);
                withdrawSharing(current);
                repository.update("UPDATE member_identity_account SET linked=0 WHERE scope=? AND member_id=?", scope, member);
            }
            return null;
        });
    }
    public Status status(Long member) {
        if (!enabled()) { return statusValue(false, false, false, ""); }
        resolve(member);
        return localStatus(member);
    }
    private Status localStatus(Long member) {
        Map<String, Object> account = repository.member(scope(), member);
        if (account == null) { return statusValue(false, false, false, ""); }
        String phone = crypto().decrypt(string(account, "phone_cipher"));
        // An admin/import edit invalidates provenance until fresh trusted verification.
        boolean verified = phone != null && phone.equals(validateUser(member).getMobile());
        return statusValue(number(account, "linked") == 1, consentCurrent(account),
                verified, verified ? phone.substring(0, 3) + "****" + phone.substring(phone.length()-4) : "");
    }
    public Status setPhoneSharing(Long member, boolean enabled, String consentRevision) {
        if (enabled) { resolve(member); }
        locked(() -> {
            PhoneConsentDisclosure disclosure = phoneConsentDisclosure();
            String revision = disclosure.revision();
            if (enabled && !revision.equals(consentRevision)) { throw IdentityPolicy.conflict(); }
            Map<String, Object> account = repository.member(scope(), member);
            if (account == null || (enabled && number(account, "linked") != 1)) { throw IdentityPolicy.unavailable(); }
            repository.update("UPDATE member_identity_account SET phone_sharing=?,phone_consent_version=?,phone_consent_revision=?,phone_consented_at=? WHERE scope=? AND member_id=?",
                    enabled ? 1 : 0, disclosure.policyVersion(), revision, System.currentTimeMillis(), scope(), member);
            repository.update("INSERT INTO member_identity_consent(scope,member_id,enabled,policy_version,consent_revision,consent_text,operator_name,privacy_url,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    scope(), member, enabled ? 1 : 0, disclosure.policyVersion(), revision, disclosure.text(),
                    disclosure.operator(), disclosure.privacyUrl(), System.currentTimeMillis());
            if (!enabled) {
                repository.update("UPDATE member_identity_outbox SET state='CANCELLED' WHERE scope=? AND member_id=? AND state='PENDING' AND share_consent=1", scope(), member);
                enqueueWithdrawal(account);
            } else {
                account.put("phone_sharing", 1);
                account.put("phone_consent_version", disclosure.policyVersion());
                account.put("phone_consent_revision", revision);
                enqueue(account);
            }
            return null;
        });
        return localStatus(member);
    }
    public void verifyWechatPhone(Long member, String code) {
        scope(); requireSafeSms();
        verifiedPhone(member, wechat.verifiedPhone(code), "wechat");
    }
    /** Called only after successful useSmsCode for the member login/update scene and a safe generator check. */
    public void verifiedSmsPhone(Long member, String phone) {
        if (!enabled()) { return; }
        requireSafeSms(); verifiedPhone(member, phone, "sms");
    }
    private void verifiedPhone(Long member, String phone, String method) {
        locked(() -> {
            Map<String, Object> account = repository.member(scope(), member);
            // A phone-only account is not silently attached to an OpenID or center subject.
            if (account == null) {
                if ("wechat".equals(method)) {
                    MemberUserDO standalone = validateUser(member);
                    MemberUserDO occupied = users.getUserByMobile(phone);
                    if ((occupied != null && !member.equals(occupied.getId()))
                            || (standalone.getMobile() != null && !standalone.getMobile().isBlank() && !phone.equals(standalone.getMobile()))) {
                        throw IdentityPolicy.conflict();
                    }
                    userMapper.updateById(new MemberUserDO().setId(member).setMobile(phone));
                }
                return null;
            }
            MemberUserDO user = validateUser(member);
            String previous = crypto().decrypt(string(account, "phone_cipher"));
            if (previous != null && !previous.equals(phone)) { throw IdentityPolicy.conflict(); }
            if (user.getMobile() != null && !user.getMobile().isBlank() && !user.getMobile().equals(phone)) { throw IdentityPolicy.conflict(); }
            MemberUserDO occupied = users.getUserByMobile(phone);
            if (occupied != null && !member.equals(occupied.getId())) { throw IdentityPolicy.conflict(); }
            userMapper.updateById(new MemberUserDO().setId(member).setMobile(phone));
            repository.update("UPDATE member_identity_account SET phone_cipher=?,phone_method=?,verified_at=?,phone_version=phone_version+1 WHERE scope=? AND member_id=?",
                    crypto().encrypt(phone), method, System.currentTimeMillis(), scope(), member);
            enqueue(repository.member(scope(), member));
            return null;
        });
    }
    private boolean consentCurrent(Map<String, Object> account) {
        return number(account, "phone_sharing") == 1
                && phoneConsentDisclosure().revision().equals(string(account, "phone_consent_revision"));
    }
    private long nextOriginVersion(Map<String, Object> account) {
        long version = number(account, "origin_version") + 1;
        repository.update("UPDATE member_identity_account SET origin_version=? WHERE scope=? AND member_id=?", version, scope(), number(account, "member_id"));
        return version;
    }
    private void enqueueWithdrawal(Map<String, Object> account) {
        if (string(account, "subject_id") == null) { return; }
        long version = nextOriginVersion(repository.member(scope(), number(account, "member_id")));
        repository.update("INSERT INTO member_identity_outbox(scope,member_id,event_id,version,verification_version,share_consent,payload_cipher,state,next_attempt,attempts) VALUES(?,?,?,?,?,0,NULL,'PENDING',?,0)",
                scope(), number(account, "member_id"), UUID.randomUUID().toString(), version,
                number(account, "phone_version"), System.currentTimeMillis());
    }
    private void enqueue(Map<String, Object> account) {
        if (!consentCurrent(account) || number(account, "linked") != 1
                || !java.util.Set.of("wechat", "sms").contains(Objects.toString(account.get("phone_method"), ""))) { return; }
        String phone = crypto().decrypt(string(account, "phone_cipher"));
        if (phone == null || !phone.equals(validateUser(number(account, "member_id")).getMobile())) { return; }
        long version = nextOriginVersion(repository.member(scope(), number(account, "member_id")));
        repository.update("INSERT INTO member_identity_outbox(scope,member_id,event_id,version,verification_version,share_consent,payload_cipher,state,next_attempt,attempts) VALUES(?,?,?,?,?,1,?,'PENDING',?,0)",
                scope(), number(account, "member_id"), UUID.randomUUID().toString(), version, number(account, "phone_version"),
                crypto().encrypt(phone), System.currentTimeMillis());
    }
    /** Scope lock serializes consent changes and network send: a committed revocation prevents subsequent dispatch. */
    public void retryOutbox() {
        if (!enabled()) { return; }
        // Already-sent consent must expire without waiting for the member to visit again.
        locked(() -> {
            for (Map<String, Object> account : repository.expiredPhoneConsents(scope(), phoneConsentDisclosure().revision())) {
                withdrawSharing(account);
            }
            return null;
        });
        for (Map<String, Object> item : repository.pending(scope())) {
            locked(() -> {
                Map<String, Object> currentItem = repository.one("SELECT state,next_attempt FROM member_identity_outbox WHERE id=? AND scope=?", item.get("id"), scope());
                if (!"PENDING".equals(string(currentItem, "state")) || number(currentItem, "next_attempt") > System.currentTimeMillis()) { return null; }
                Map<String, Object> account = repository.member(scope(), number(item, "member_id"));
                boolean sharing = number(item, "share_consent") == 1;
                if (account == null || (sharing && (!consentCurrent(account) || number(account, "linked") != 1
                        || number(account, "phone_version") != number(item, "verification_version")
                        || number(account, "origin_version") != number(item, "version")))) {
                    repository.update("UPDATE member_identity_outbox SET state='CANCELLED' WHERE id=? AND scope=?", item.get("id"), scope());
                    return null;
                }
                // A later acknowledged event already replaced this withdrawal at the center.
                // Merely queued newer consent is insufficient: retain withdrawal until delivery is proven.
                if (!sharing && repository.one("SELECT id FROM member_identity_outbox WHERE scope=? AND member_id=? AND state='SENT' AND version>? LIMIT 1",
                        scope(), item.get("member_id"), item.get("version")) != null) {
                    repository.update("UPDATE member_identity_outbox SET state='CANCELLED' WHERE id=? AND scope=?", item.get("id"), scope());
                    return null;
                }
                try {
                    if (!sharing) {
                        JsonNode result = center.post("phone", Map.of("openid", string(account, "openid"), "member_id", string(account, "member_id"),
                                "event_id", string(item, "event_id"), "version", number(item, "version"), "share_consent", false));
                        if (!result.path("accepted").asBoolean()) { throw IdentityPolicy.unavailable(); }
                        repository.update("UPDATE member_identity_outbox SET state='SENT' WHERE id=? AND scope=?", item.get("id"), scope());
                        return null;
                    }
                    String phone = crypto().decrypt(string(item, "payload_cipher"));
                    if (!phone.equals(validateUser(number(item, "member_id")).getMobile())) { throw IdentityPolicy.conflict(); }
                    JsonNode resolved = center.post("resolve", Map.of("openid", string(account, "openid"), "member_id", string(account, "member_id")));
                    if (!resolved.path("linked").asBoolean()) {
                        clearCenterPhone(account);
                        withdrawSharing(account);
                        repository.update("UPDATE member_identity_account SET linked=0 WHERE scope=? AND member_id=?", scope(), item.get("member_id"));
                        repository.update("UPDATE member_identity_outbox SET state='CANCELLED' WHERE id=? AND scope=?", item.get("id"), scope());
                        return null;
                    }
                    JsonNode result = center.post("phone", Map.of("openid", string(account, "openid"), "member_id", string(account, "member_id"),
                            "event_id", string(item, "event_id"), "version", number(item, "version"), "phone", phone,
                            "country_code", "86", "verified_at", number(account, "verified_at"),
                            "verification_method", string(account, "phone_method"), "share_consent", true));
                    if (!result.path("accepted").asBoolean()) { throw IdentityPolicy.unavailable(); }
                    repository.update("UPDATE member_identity_outbox SET state='SENT' WHERE id=? AND scope=?", item.get("id"), scope());
                } catch (RuntimeException ignored) {
                    long attempts = number(item, "attempts") + 1;
                    repository.update("UPDATE member_identity_outbox SET attempts=?,next_attempt=? WHERE id=? AND scope=?", attempts,
                            System.currentTimeMillis() + Math.min(3600000L, 30000L * (1L << Math.min(attempts, 7))), item.get("id"), scope());
                }
                return null;
            });
        }
    }
}
