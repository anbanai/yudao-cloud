package cn.iocoder.yudao.module.trade.framework.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SfOpenApiSignerTest {

    @Test
    void sign_usesSfUrlEncodedMd5Contract() {
        String digest = SfOpenApiSigner.sign("{\"orderId\":\"A001\"}", 1710000000L, "secret");

        assertThat(digest).isEqualTo("1a5QIVwMJRyjJLFwfNibUw==");
    }

}
