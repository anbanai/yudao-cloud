package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WechatLogisticsControllerHistoryOnlyTest {

    @Test
    void controller_doesNotExposeNewWechatWaybillCreation() {
        Set<String> postPaths = Arrays.stream(WechatLogisticsController.class.getDeclaredMethods())
                .map(this::getAnnotation)
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toSet());

        assertThat(postPaths).doesNotContain("/waybills/create", "/waybills/batch-create");
    }

    private PostMapping getAnnotation(Method method) {
        return method.getAnnotation(PostMapping.class);
    }
}
