package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;

/**
 * 微信物流助手轨迹响应。
 */
@Data
public class SocialWxaExpressPathRespDTO {

    private String openid;
    private String deliveryId;
    private String waybillId;
    private Integer pathItemNum;
    private List<PathItem> pathItemList;

    @Data
    public static class PathItem {
        private Long actionTime;
        private Integer actionType;
        private String actionMsg;
    }
}
