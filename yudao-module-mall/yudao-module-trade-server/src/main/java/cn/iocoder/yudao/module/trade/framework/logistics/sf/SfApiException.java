package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import lombok.Getter;

@Getter
public class SfApiException extends RuntimeException {

    private final String code;
    private final boolean unknownResult;

    public SfApiException(String code, String message, boolean unknownResult, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.unknownResult = unknownResult;
    }

    public SfApiException(String code, String message) {
        this(code, message, false, null);
    }
}
