package cn.iocoder.yudao.module.trade.controller.merchant.aftersale.vo;

import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleBaseVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.log.AfterSaleLogRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderSubRespVO;
import lombok.Data;

import java.util.List;

@Data
public class MerchantAfterSaleDetailRespVO {

    private Long id;
    private AfterSaleBaseVO afterSale;
    private TradeOrderSubRespVO subOrder;
    private AfterSaleDetailRespVO.OrderItem orderItem;
    private List<AfterSaleLogRespVO> logs;

}
