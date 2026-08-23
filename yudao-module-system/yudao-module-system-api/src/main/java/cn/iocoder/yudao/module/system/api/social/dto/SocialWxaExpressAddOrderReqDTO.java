package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;

/**
 * 微信物流助手生成运单请求。
 *
 * <p>字段保持微信 addOrder 的命名语义，但不把微信 SDK 类型暴露到业务模块。</p>
 */
@Data
public class SocialWxaExpressAddOrderReqDTO {

    private Integer addSource;
    private String wxAppid;
    private String orderId;
    private String openid;
    private String deliveryId;
    private String bizId;
    private String customRemark;
    private Integer tagid;
    private Long expectTime;
    private Person sender;
    private Person receiver;
    private Cargo cargo;
    private Shop shop;
    private Insured insured;
    private Service service;

    @Data
    public static class Person {
        private String name;
        private String tel;
        private String mobile;
        private String company;
        private String postCode;
        private String country;
        private String province;
        private String city;
        private String area;
        private String address;
    }

    @Data
    public static class Cargo {
        private Integer count;
        private Double weight;
        private Double spaceX;
        private Double spaceY;
        private Double spaceZ;
        private List<CargoDetail> detailList;
    }

    @Data
    public static class CargoDetail {
        private String name;
        private Integer count;
    }

    @Data
    public static class Shop {
        private String wxaPath;
        private String imgUrl;
        private String goodsName;
        private Integer goodsCount;
        private List<ShopDetail> detailList;
    }

    @Data
    public static class ShopDetail {
        private String goodsName;
        private String goodsImgUrl;
        private String goodsDesc;
    }

    @Data
    public static class Insured {
        private Integer useInsured;
        private Integer insuredValue;
    }

    @Data
    public static class Service {
        private Integer serviceType;
        private String serviceName;
    }
}
