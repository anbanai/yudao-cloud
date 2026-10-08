package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class TradeReportExcelVO {
    @ExcelProperty("商品名称")
    private String productName;
    @ExcelProperty("销售属性")
    private String properties;
    @ExcelProperty("商家编码")
    private String barCode;
    @ExcelProperty("商品数量")
    private Integer quantity;
    @ExcelProperty("订单数")
    private Integer orderCount;
    @ExcelProperty("金额(分)")
    private Long amount;
}
