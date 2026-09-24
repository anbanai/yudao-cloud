package cn.iocoder.yudao.module.member.service.identity;

import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

/** Server-owned configuration for one tenant's mini-program; never returned from public APIs. */
@Getter
@Setter
public class IdentityBridgeScope {
    private boolean enabled = false;
    private String centerUrl = "";
    private boolean allowLocalHttp = false;
    private String appId = "";
    private String appSecret = "";
    private String backendInstance = "";
    private Long tenantId = null;
    private String keyId = "";
    private String hmacSecret = "";
    private String encryptionKey = "";
    private Set<String> sourceAppIds = new HashSet<>();
    private String phoneSharingConsentText = "";
    private String phoneSharingPrivacyUrl = "";
    private String phoneSharingPolicyVersion = "";
    private String identityOperatorName = "";
}
