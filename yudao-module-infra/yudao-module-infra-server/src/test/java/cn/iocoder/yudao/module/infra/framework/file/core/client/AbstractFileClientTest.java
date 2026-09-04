package cn.iocoder.yudao.module.infra.framework.file.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractFileClientTest {

    @Test
    void refreshFailureDoesNotPreventRetry() {
        TestConfig initial = new TestConfig("initial", false);
        TestConfig replacement = new TestConfig("replacement", true);
        TestClient client = new TestClient(initial);

        assertThrows(IllegalStateException.class, () -> client.refresh(replacement));
        replacement.fail = false;

        client.refresh(replacement);

        assertEquals(3, client.initializationCount);
    }

    private static final class TestClient extends AbstractFileClient<TestConfig> {

        private int initializationCount;

        private TestClient(TestConfig config) {
            super(1L, config);
            init();
        }

        @Override
        protected void doInit() {
            initializationCount++;
            if (config.fail) {
                throw new IllegalStateException("simulated initialization failure");
            }
        }

        @Override
        public String upload(byte[] content, String path, String type) {
            return path;
        }

        @Override
        public void delete(String path) {
        }

        @Override
        public byte[] getContent(String path) {
            return new byte[0];
        }
    }

    private static final class TestConfig implements FileClientConfig {

        private boolean fail;
        private final String name;

        private TestConfig(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestConfig && name.equals(((TestConfig) object).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

}
