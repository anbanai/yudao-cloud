package cn.iocoder.yudao.module.trade.dal.mysql.order;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradePurchaseLockMapper {
    @Insert("INSERT INTO trade_purchase_lock(tenant_id,user_id) VALUES(#{tenantId},#{userId}) ON DUPLICATE KEY UPDATE user_id=user_id")
    int ensureRow(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("SELECT user_id FROM trade_purchase_lock WHERE tenant_id=#{tenantId} AND user_id=#{userId} FOR UPDATE")
    Long lock(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
