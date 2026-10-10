package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.BeanDescription;
import com.yeepay.yop.sdk.service.common.response.YopResponse;
import com.yeepay.yop.sdk.service.common.response.YosUploadResponse;
import com.yeepay.yop.sdk.utils.JsonUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compatibility adapter for YOP SDK 4.4.x on the application's Jackson version.
 *
 * <p>The official SDK's {@code KeepAsRawStringDeserializer} assumes that Jackson exposes the
 * source as {@code String} or {@code StringReader}. Jackson 2.21 may expose a different source
 * reference, although the SDK still provides the supported {@code JsonUtils} mapper hook. This
 * module changes only extraction of {@code YopResponse.result}; request signing, response
 * signature verification, encryption and callback parsing remain in the official SDK.</p>
 */
final class YeepaySdkJacksonCompatibility {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private YeepaySdkJacksonCompatibility() {
    }

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        JsonUtils.getObjectMapper().registerModule(resultModule());
    }

    private static Module resultModule() {
        SimpleModule module = new SimpleModule("yudao-yeepay-result-compatibility");
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                          BeanDescription beanDescription,
                                                          BeanDeserializerBuilder builder) {
                if (beanDescription.getBeanClass() != YopResponse.class
                        && beanDescription.getBeanClass() != YosUploadResponse.class) {
                    return builder;
                }
                SettableBeanProperty property = builder.findProperty(
                        new com.fasterxml.jackson.databind.PropertyName("result"));
                if (property != null) {
                    builder.addOrReplaceProperty(property.withValueDeserializer(new RawJsonDeserializer()), true);
                }
                return builder;
            }
        });
        return module;
    }

    private static final class RawJsonDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context)
                throws IOException {
            if (parser.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                return parser.getText();
            }
            if (parser.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL) {
                return null;
            }
            JsonNode node = parser.readValueAsTree();
            return node == null ? null : node.toString();
        }
    }
}
