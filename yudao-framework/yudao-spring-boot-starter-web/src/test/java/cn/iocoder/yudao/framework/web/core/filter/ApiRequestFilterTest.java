package cn.iocoder.yudao.framework.web.core.filter;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestFilterTest {

    @Test
    void shouldNotFilter_merchantApiPath_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/merchant-api/system/auth/login");

        assertThat(new TestApiRequestFilter().shouldNotFilterRequest(request)).isFalse();
    }

    @Test
    void shouldNotFilter_nonApiPath_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");

        assertThat(new TestApiRequestFilter().shouldNotFilterRequest(request)).isTrue();
    }

    private static class TestApiRequestFilter extends ApiRequestFilter {

        private TestApiRequestFilter() {
            super(new WebProperties());
        }

        private boolean shouldNotFilterRequest(HttpServletRequest request) {
            return shouldNotFilter(request);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
        }

    }

}
