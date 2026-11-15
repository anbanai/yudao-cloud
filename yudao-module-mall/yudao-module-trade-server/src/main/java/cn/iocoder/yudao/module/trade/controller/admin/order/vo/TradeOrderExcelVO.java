package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TradeOrderExcelVO {

    @ExcelProperty("订单号")
    private String no;
    @ExcelProperty("订单类型")
    private Integer type;
    @ExcelProperty("订单状态")
    private Integer status;
    @ExcelProperty("用户编号")
    private Long userId;
    @ExcelProperty("商品数量")
    private Integer productCount;
    @ExcelProperty("实付金额(分)")
    private Integer payPrice;
    @ExcelProperty("已退款金额(分)")
    private Integer refundPrice;
    @ExcelProperty("下单时间")
    private LocalDateTime createTime;

}
