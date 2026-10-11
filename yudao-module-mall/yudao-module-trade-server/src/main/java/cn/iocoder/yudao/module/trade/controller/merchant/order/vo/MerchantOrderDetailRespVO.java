package cn.iocoder.yudao.module.trade.controller.merchant.order.vo;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderSubRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantOrderDetailRespVO {

    private TradeOrderSubRespVO subOrder;
    private String orderNo;
    private LocalDateTime orderCreateTime;
    private Integer terminal;
    private String buyerRemark;
    private Receiver receiver;
    private List<Item> items;

    @Data
    public static class Receiver {
        private String name;
        private String mobile;
        private Integer areaId;
        private String detailAddress;
    }

    @Data
    public static class Item {
        private Long id;
        private Long spuId;
        private String spuName;
        private Long skuId;
        private String picUrl;
        private Integer count;
        private Integer price;
        private Integer payPrice;
        private Integer afterSaleStatus;
        private List<TradeOrderItemDO.Property> properties;
    }

}
