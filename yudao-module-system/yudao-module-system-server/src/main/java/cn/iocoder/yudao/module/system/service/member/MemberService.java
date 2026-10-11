package cn.iocoder.yudao.module.system.service.member;

/**
 * Member Service 接口
 *
 * @author 芋道源码
 */
public interface MemberService {

    /**
     * 获得会员用户的手机号码
     *
     * @param id 会员用户编号
     * @return 手机号码
     */
    String getMemberUserMobile(Long id);

    /**
     * 获得会员用户的邮箱
     *
     * @param id 会员用户编号
     * @return 邮箱
     */
    String getMemberUserEmail(Long id);

    /**
     * Checks whether a member account already uses the mobile number.
     *
     * @param mobile mobile number
     * @return whether the mobile is registered
     */
    boolean isMemberMobileExists(String mobile);

}
