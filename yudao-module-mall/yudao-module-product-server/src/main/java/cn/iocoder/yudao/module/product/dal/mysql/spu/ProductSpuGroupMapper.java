package cn.iocoder.yudao.module.product.dal.mysql.spu;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProductSpuGroupMapper {
    @Select("SELECT group_id FROM product_group_spu WHERE spu_id=#{spuId} AND tenant_id=#{tenantId} AND deleted=b'0' ORDER BY sort,id")
    List<Long> selectGroupIds(@Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    @Select("SELECT group_id FROM product_group_spu WHERE spu_id=#{spuId} AND tenant_id=#{tenantId} AND deleted=b'0'")
    List<Long> selectGroupIdsBySpuId(@Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM product_group_spu WHERE spu_id=#{spuId} AND tenant_id=#{tenantId}")
    int deleteBySpuId(@Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    @Insert("INSERT INTO product_group_spu(group_id,spu_id,sort,tenant_id,deleted,create_time,update_time) VALUES(#{groupId},#{spuId},#{sort},#{tenantId},b'0',NOW(),NOW())")
    int insertRelation(@Param("groupId") Long groupId, @Param("spuId") Long spuId,
                       @Param("sort") Integer sort, @Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(*) FROM product_group_spu WHERE group_id=#{groupId} AND spu_id=#{spuId} AND tenant_id=#{tenantId} AND deleted=b'0'")
    int countRelation(@Param("groupId") Long groupId, @Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM product_group_spu WHERE group_id=#{groupId} AND spu_id=#{spuId} AND tenant_id=#{tenantId}")
    int deleteRelation(@Param("groupId") Long groupId, @Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    @Update("UPDATE product_group_spu SET sort=#{sort},update_time=NOW() WHERE group_id=#{groupId} AND spu_id=#{spuId} AND tenant_id=#{tenantId} AND deleted=b'0'")
    int updateSort(@Param("groupId") Long groupId, @Param("spuId") Long spuId, @Param("sort") Integer sort, @Param("tenantId") Long tenantId);

    @Select({"<script>", "SELECT spu_id AS spuId, group_id AS groupId FROM product_group_spu WHERE tenant_id=#{tenantId} AND deleted=b'0' AND spu_id IN",
            "<foreach collection='spuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>", "ORDER BY sort,id", "</script>"})
    List<java.util.Map<String, Object>> selectSpuGroupPairs(@Param("spuIds") List<Long> spuIds, @Param("tenantId") Long tenantId);
}
