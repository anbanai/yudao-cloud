package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantUserMapper extends BaseMapperX<MerchantUserDO> {

    @Update("UPDATE system_merchant_user SET merchant_id = #{merchantId} "
            + "WHERE id = #{userId} AND merchant_id IS NULL")
    int bindMerchantIdIfUnbound(Long userId, Long merchantId);

    default PageResult<MerchantUserDO> selectPage(MerchantUserPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantUserDO>()
                .likeIfPresent(MerchantUserDO::getUsername, reqVO.getUsername())
                .likeIfPresent(MerchantUserDO::getNickname, reqVO.getNickname())
                .likeIfPresent(MerchantUserDO::getMobile, reqVO.getMobile())
                .eqIfPresent(MerchantUserDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(MerchantUserDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MerchantUserDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantUserDO::getId));
    }

    default MerchantUserDO selectByUsername(String username) {
        return selectOne(MerchantUserDO::getUsername, username);
    }

    default MerchantUserDO selectByMobile(String mobile) {
        return selectOne(MerchantUserDO::getMobile, mobile);
    }

    default MerchantUserDO selectByEmail(String email) {
        return selectOne(MerchantUserDO::getEmail, email);
    }

}
