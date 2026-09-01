package cn.iocoder.yudao.module.trade.controller.internal.logistics.vo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrintBridgeStatusReqVOTest {

    @Test
    void deserialize_usesPrintBridgeOccurredAtField() {
        String json = """
                {
                  "event": "status",
                  "event_id": "EVENT-001",
                  "request_id": "REQ-001",
                  "job_id": "JOB-001",
                  "status": "success",
                  "occurred_at": "2026-07-06T10:00:00Z"
                }
                """;

        PrintBridgeStatusReqVO request = JsonUtils.parseObject(json, PrintBridgeStatusReqVO.class);

        assertThat(request.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-07-06T10:00:00Z"));
    }

}
