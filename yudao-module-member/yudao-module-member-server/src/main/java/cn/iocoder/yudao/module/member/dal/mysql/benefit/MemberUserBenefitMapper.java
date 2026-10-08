package cn.iocoder.yudao.module.member.dal.mysql.benefit;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberUserBenefitDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface MemberUserBenefitMapper extends BaseMapperX<MemberUserBenefitDO> {
    default MemberUserBenefitDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).last("FOR UPDATE"));
    }
    default int grant(Long id, int quantity) {
        return update(null, new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .setSql("granted_quantity = granted_quantity + " + quantity)
                .setSql("available_quantity = available_quantity + " + quantity));
    }

    default int revoke(Long id, int quantity) {
        return update(null, new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .ge(MemberUserBenefitDO::getAvailableQuantity, quantity)
                .setSql("granted_quantity = granted_quantity - " + quantity)
                .setSql("available_quantity = available_quantity - " + quantity));
    }

    default int adjust(Long id, int quantity) {
        LambdaUpdateWrapper<MemberUserBenefitDO> wrapper = new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .setSql("granted_quantity = granted_quantity + " + quantity)
                .setSql("available_quantity = available_quantity + " + quantity);
        if (quantity < 0) {
            wrapper.ge(MemberUserBenefitDO::getAvailableQuantity, -quantity);
        }
        return update(null, wrapper);
    }

    default int claim(Long id, java.time.LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .eq(MemberUserBenefitDO::getClaimType, 1).eq(MemberUserBenefitDO::getClaimStatus, 0)
                .gt(MemberUserBenefitDO::getPeriodEnd, now)
                .gt(MemberUserBenefitDO::getAvailableQuantity, 0)
                .setSql("claim_status = CASE WHEN available_quantity <= 1 THEN 1 ELSE 0 END")
                .setSql("claimed_quantity = claimed_quantity + 1")
                .setSql("available_quantity = available_quantity - 1"));
    }

    default int use(Long id) {
        return update(null, new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .gt(MemberUserBenefitDO::getAvailableQuantity, 0)
                .setSql("available_quantity = available_quantity - 1")
                .setSql("used_quantity = used_quantity + 1"));
    }

    default int expire(Long id, java.time.LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getId, id).eq(MemberUserBenefitDO::getStatus, 0)
                .le(MemberUserBenefitDO::getPeriodEnd, now)
                .set(MemberUserBenefitDO::getStatus, 1));
    }
}
