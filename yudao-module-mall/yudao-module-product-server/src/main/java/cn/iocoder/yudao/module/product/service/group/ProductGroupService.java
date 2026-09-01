package cn.iocoder.yudao.module.product.service.group;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;

public interface ProductGroupService {
    Long createGroup(@Valid ProductGroupSaveReqVO reqVO);
    void updateGroup(@Valid ProductGroupSaveReqVO reqVO);
    void deleteGroup(Long id);
    ProductGroupDO getGroup(Long id);
    PageResult<ProductGroupDO> getGroupPage(ProductGroupPageReqVO reqVO);
    List<ProductGroupDO> getGroupList(Collection<Long> ids, boolean onlyEnabled);
    List<Long> getGroupIdsBySpuId(Long spuId);
    PageResult<ProductGroupSpuRespVO> getAdminSpuPage(ProductGroupSpuPageReqVO reqVO);
    PageResult<ProductSpuDO> getAppSpuPage(AppProductGroupSpuPageReqVO reqVO);
    void addSpus(@Valid ProductGroupSpuBatchReqVO reqVO);
    void removeSpus(@Valid ProductGroupSpuBatchReqVO reqVO);
    void updateSpuSort(@Valid ProductGroupSpuSortReqVO reqVO);
    void syncSpuGroups(Long spuId, List<Long> groupIds);
    void deleteRelationsBySpuId(Long spuId);
}
