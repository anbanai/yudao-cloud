package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import java.util.Objects;

public final class IdentityPolicy {
    private IdentityPolicy() { }
    public static <T> T safely(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (ServiceException safeError) { throw safeError; }
        catch (RuntimeException unexpected) { throw unavailable(); }
    }
    public static ServiceException conflict() { return new ServiceException(1004019001, "IDENTITY_CONFLICT"); }
    public static ServiceException unavailable() { return new ServiceException(1004019002, "身份连接暂不可用，请重试或使用微信登录"); }
    public static void checkPhone(String memberPhone, String verifiedPhone, String incomingPhone) {
        if (incomingPhone == null || incomingPhone.isBlank()) { return; }
        if (verifiedPhone != null && !Objects.equals(verifiedPhone, incomingPhone)) { throw conflict(); }
        if (memberPhone != null && !memberPhone.isBlank()
                && (!Objects.equals(memberPhone, incomingPhone) || !Objects.equals(verifiedPhone, incomingPhone))) {
            throw conflict();
        }
    }
}
