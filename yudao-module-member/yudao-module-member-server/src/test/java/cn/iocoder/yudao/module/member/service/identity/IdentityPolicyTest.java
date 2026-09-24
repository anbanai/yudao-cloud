package cn.iocoder.yudao.module.member.service.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityPolicyTest {
    @Test void historicalPhoneIsNotProof() {
        assertThrows(RuntimeException.class, () -> IdentityPolicy.checkPhone("13800000000", null, "13800000000"));
    }
    @Test void differentVerifiedPhoneNeverOverwrites() {
        assertThrows(RuntimeException.class, () -> IdentityPolicy.checkPhone("13800000000", "13800000000", "13900000000"));
    }
    @Test void phoneAbsentDoesNotPreventIdentity() {
        assertDoesNotThrow(() -> IdentityPolicy.checkPhone(null, null, null));
    }
    @Test void verifiedMatchingPhoneIsAllowed() {
        assertDoesNotThrow(() -> IdentityPolicy.checkPhone("13800000000", "13800000000", "13800000000"));
    }
    @Test void canonicalHmacHasKnownVector() {
        assertEquals("POST\n/internal/identity/claim\n1700000000\nnonce\n44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", IdentityCrypto.canonical("/internal/identity/claim", "1700000000", "nonce", "{}"));
    }
    @Test void encryptionIsRandomizedAndAuthenticated() {
        IdentityCrypto crypto = new IdentityCrypto("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String a = crypto.encrypt("13800000000");
        assertNotEquals(a, crypto.encrypt("13800000000"));
        assertEquals("13800000000", crypto.decrypt(a));
        assertThrows(RuntimeException.class, () -> crypto.decrypt(a.substring(0, a.length()-4) + "AAAA"));
    }
}
