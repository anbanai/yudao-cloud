package cn.iocoder.yudao.module.infra.controller.admin.file.vo.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileConfigRespVOTest {

    @Test
    void serializePrivateStorageAsPrivate() {
        FileConfigRespVO response = new FileConfigRespVO();
        response.setPrivateStorage(true);

        JsonNode json = JsonUtils.parseObject(JsonUtils.toJsonString(response), JsonNode.class);

        assertTrue(json.path("private").asBoolean());
        assertFalse(json.has("privateStorage"));
    }

}
