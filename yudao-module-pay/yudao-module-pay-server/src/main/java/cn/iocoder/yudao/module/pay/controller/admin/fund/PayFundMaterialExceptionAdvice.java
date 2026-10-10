package cn.iocoder.yudao.module.pay.controller.admin.fund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApiImpl;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 入网请求在反序列化/校验失败时也不能进入打印资料的通用异常处理。 */
@RestControllerAdvice(assignableTypes = {PayFundController.class, PayFundApiImpl.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PayFundMaterialExceptionAdvice {

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class})
    public CommonResult<?> invalidMaterialBody(Exception ex) {
        return CommonResult.error(400, "商家入网请求参数无效");
    }
}
