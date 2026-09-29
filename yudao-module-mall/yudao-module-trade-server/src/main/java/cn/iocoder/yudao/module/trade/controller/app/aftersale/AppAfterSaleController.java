package cn.iocoder.yudao.module.trade.controller.app.aftersale;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSaleRespVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppReturnShipmentCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppReturnShipmentRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnTraceDO;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleReturnShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 交易售后")
@RestController
@RequestMapping("/trade/after-sale")
@Validated
@Slf4j
public class AppAfterSaleController {

    @Resource
    private AfterSaleService afterSaleService;

    @Resource
    private AfterSaleReturnShipmentService returnShipmentService;

    @GetMapping(value = "/page")
    @Operation(summary = "获得售后分页")
    public CommonResult<PageResult<AppAfterSaleRespVO>> getAfterSalePage(AppAfterSalePageReqVO pageReqVO) {
        PageResult<AfterSaleDO> pageResult = afterSaleService.getAfterSalePage(getLoginUserId(), pageReqVO);
        return success(BeanUtils.toBean(pageResult, AppAfterSaleRespVO.class));
    }

    @GetMapping(value = "/get")
    @Operation(summary = "获得售后订单")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    public CommonResult<AppAfterSaleRespVO> getAfterSale(@RequestParam("id") Long id) {
        AfterSaleDO afterSale = afterSaleService.getAfterSale(getLoginUserId(), id);
        return success(BeanUtils.toBean(afterSale, AppAfterSaleRespVO.class));
    }

    @PostMapping(value = "/create")
    @Operation(summary = "申请售后")
    public CommonResult<Long> createAfterSale(@Valid @RequestBody AppAfterSaleCreateReqVO createReqVO) {
        return success(afterSaleService.createAfterSale(getLoginUserId(), createReqVO));
    }

    @PutMapping(value = "/delivery")
    @Operation(summary = "退回货物")
    public CommonResult<Boolean> deliveryAfterSale(@Valid @RequestBody AppAfterSaleDeliveryReqVO deliveryReqVO) {
        afterSaleService.deliveryAfterSale(getLoginUserId(), deliveryReqVO);
        return success(true);
    }

    @PostMapping("/{id}/return-shipment/preview")
    @Operation(summary = "预览退货上门取件")
    public CommonResult<AppReturnShipmentRespVO> previewReturnShipment(@PathVariable("id") Long id) {
        return success(toReturnShipmentResp(returnShipmentService.preview(getLoginUserId(), id),
                returnShipmentService.getTraces(getLoginUserId(), id)));
    }

    @PostMapping("/{id}/return-shipment/create")
    @Operation(summary = "创建退货上门取件")
    public CommonResult<AppReturnShipmentRespVO> createReturnShipment(@PathVariable("id") Long id,
                                                                        @Valid @RequestBody AppReturnShipmentCreateReqVO request) {
        AfterSaleReturnShipmentDO shipment = returnShipmentService.create(getLoginUserId(), id, request);
        return success(toReturnShipmentResp(shipment, returnShipmentService.getTraces(getLoginUserId(), id)));
    }

    @GetMapping("/{id}/return-shipment")
    @Operation(summary = "获得退货上门取件")
    public CommonResult<AppReturnShipmentRespVO> getReturnShipment(@PathVariable("id") Long id) {
        AfterSaleReturnShipmentDO shipment = returnShipmentService.get(getLoginUserId(), id);
        return success(toReturnShipmentResp(shipment, returnShipmentService.getTraces(getLoginUserId(), id)));
    }

    @PostMapping("/{id}/return-shipment/cancel")
    @Operation(summary = "取消退货上门取件")
    public CommonResult<Boolean> cancelReturnShipment(@PathVariable("id") Long id) {
        returnShipmentService.cancel(getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/{id}/return-shipment/traces")
    @Operation(summary = "获得退货物流轨迹")
    public CommonResult<List<AppReturnShipmentRespVO.Trace>> getReturnShipmentTraces(@PathVariable("id") Long id) {
        return success(returnShipmentService.getTraces(getLoginUserId(), id).stream().map(trace -> {
            AppReturnShipmentRespVO.Trace item = new AppReturnShipmentRespVO.Trace();
            item.setStatus(trace.getStatus());
            item.setDescription(trace.getDescription());
            item.setLocation(trace.getLocation());
            item.setOccurredTime(trace.getOccurredTime());
            return item;
        }).toList());
    }

    @DeleteMapping(value = "/cancel")
    @Operation(summary = "取消售后")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    public CommonResult<Boolean> cancelAfterSale(@RequestParam("id") Long id) {
        afterSaleService.cancelAfterSale(getLoginUserId(), id);
        return success(true);
    }

    private AppReturnShipmentRespVO toReturnShipmentResp(AfterSaleReturnShipmentDO shipment,
                                                          List<AfterSaleReturnTraceDO> traces) {
        if (shipment == null) return null;
        AppReturnShipmentRespVO response = BeanUtils.toBean(shipment, AppReturnShipmentRespVO.class);
        response.setPickupContactName(shipment.getPickupName());
        response.setPickupContactMobile(shipment.getPickupMobile());
        response.setReturnAddress(shipment.getWarehouseAddress());
        if (shipment.statusEnum() != null) response.setStatusName(shipment.statusEnum().getName());
        if (shipment.feePayerEnum() != null) response.setFeePayerName(shipment.feePayerEnum().getName());
        response.setTraces(traces.stream().map(trace -> {
            AppReturnShipmentRespVO.Trace item = new AppReturnShipmentRespVO.Trace();
            item.setStatus(trace.getStatus());
            item.setDescription(trace.getDescription());
            item.setLocation(trace.getLocation());
            item.setOccurredTime(trace.getOccurredTime());
            return item;
        }).toList());
        return response;
    }

}
