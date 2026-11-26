package cn.iocoder.yudao.module.product.service.spu;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuUpdateStatusReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuAccessRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.level.dto.MemberLevelRespDTO;
import java.math.BigDecimal;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.CATEGORY_LEVEL;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品 SPU Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductSpuServiceImpl implements ProductSpuService {

    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSkuService productSkuService;
    @Resource
    private ProductBrandService brandService;
    @Resource
    private ProductCategoryService categoryService;
    @Resource
    private ProductGroupService productGroupService;

    @Resource
    private ProductMerchantService merchantService;

    @Resource
    private FileApi fileApi;

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private MemberLevelApi memberLevelApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpu(ProductSpuSaveReqVO createReqVO) {
        // 校验分类、品牌
        validateCategory(createReqVO.getCategoryId());
        brandService.validateProductBrand(createReqVO.getBrandId());
        validateMerchantAndBrand(createReqVO.getMerchantId(), createReqVO.getBrandId());
        validateProductRules(createReqVO);
        // 校验 SKU
        List<ProductSkuSaveReqVO> skuSaveReqList = createReqVO.getSkus();
        productSkuService.validateSkuList(skuSaveReqList, createReqVO.getSpecType());
        validateProductImages(createReqVO);

        ProductSpuDO spu = BeanUtils.toBean(createReqVO, ProductSpuDO.class);
        applyProductDefaults(spu);
        // 初始化 SPU 中 SKU 相关属性
        initSpuFromSkus(spu, skuSaveReqList);
        // 插入 SPU
        productSpuMapper.insert(spu);
        // 插入 SKU
        productSkuService.createSkuList(spu.getId(), skuSaveReqList);
        productGroupService.syncSpuGroups(spu.getId(), createReqVO.getGroupIds());
        // 返回
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(ProductSpuSaveReqVO updateReqVO) {
        // 校验 SPU 是否存在
        ProductSpuDO spu = validateSpuExists(updateReqVO.getId());
        // 校验分类、品牌
        Long categoryId = updateReqVO.getCategoryId();
        boolean categoryChanged = !Objects.equals(categoryId, spu.getCategoryId());
        if (categoryChanged) {
            validateCategory(categoryId);
        } else {
            // 历史商品可保留原分类，仍需确认分类存在并且启用。
            categoryService.validateCategory(categoryId);
        }
        brandService.validateProductBrand(updateReqVO.getBrandId());
        validateMerchantAndBrand(updateReqVO.getMerchantId(), updateReqVO.getBrandId());
        validateProductRules(updateReqVO);
        // 校验SKU
        List<ProductSkuSaveReqVO> skuSaveReqList = updateReqVO.getSkus();
        productSkuService.validateSkuList(skuSaveReqList, updateReqVO.getSpecType());
        validateProductImages(updateReqVO);

        // 更新 SPU
        ProductSpuDO updateObj = BeanUtils.toBean(updateReqVO, ProductSpuDO.class).setStatus(spu.getStatus());
        if (!categoryChanged) updateObj.setCategoryId(null);
        if (updateReqVO.getIsPreSale() == null) updateObj.setIsPreSale(spu.getIsPreSale());
        if (updateReqVO.getDeliveryDays() == null) updateObj.setDeliveryDays(spu.getDeliveryDays());
        if (updateReqVO.getBuyLimit() == null) updateObj.setBuyLimit(spu.getBuyLimit());
        if (updateReqVO.getBuyLimitLevel() == null) updateObj.setBuyLimitLevel(spu.getBuyLimitLevel());
        if (updateReqVO.getPointDiscountRate() == null) updateObj.setPointDiscountRate(spu.getPointDiscountRate());
        if (updateReqVO.getMarketingConfig() == null) updateObj.setMarketingConfig(spu.getMarketingConfig());
        initSpuFromSkus(updateObj, skuSaveReqList);
        productSpuMapper.updateById(updateObj);
        // 批量更新 SKU
        productSkuService.updateSkuList(updateObj.getId(), updateReqVO.getSkus());
        productGroupService.syncSpuGroups(updateObj.getId(), updateReqVO.getGroupIds());
    }

    private void validateBrandIfPresent(Long brandId) {
        if (brandId != null) brandService.validateProductBrand(brandId);
    }

    private void applyProductDefaults(ProductSpuDO spu) {
        if (spu.getIsPreSale() == null) spu.setIsPreSale(false);
        if (spu.getBuyLimit() == null) spu.setBuyLimit(0);
        if (spu.getBuyLimitLevel() == null) spu.setBuyLimitLevel("ALL");
        if (spu.getPointDiscountRate() == null) spu.setPointDiscountRate(BigDecimal.ZERO);
    }

    private void validateProductRules(cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO req) {
        Boolean presale = req.getIsPreSale();
        Integer days = req.getDeliveryDays();
        if (Boolean.TRUE.equals(presale)) {
            if (days == null || days < 1 || days > 90) throw exception(SPU_MARKETING_CONFIG_INVALID, "预售周期须为 1..90 天");
        } else if (days != null && days != 0) {
            throw exception(SPU_MARKETING_CONFIG_INVALID, "现货不能设置预售周期");
        }
        if (req.getBuyLimit() != null && req.getBuyLimit() < 0) throw exception(SPU_MARKETING_CONFIG_INVALID, "限购数不能小于 0");
        if (req.getBuyLimitLevel() != null && !Arrays.asList("ALL", "L0", "L1", "L2", "L3").contains(req.getBuyLimitLevel())) {
            throw exception(SPU_MARKETING_CONFIG_INVALID, "限购等级无效");
        }
        BigDecimal ratio = req.getPointDiscountRate();
        if (ratio != null && (ratio.signum() < 0 || ratio.compareTo(BigDecimal.ONE) > 0)) throw exception(SPU_MARKETING_CONFIG_INVALID, "积分抵扣比例必须在 0..1");
        validateAudienceConfig(req.getMarketingConfig());
    }

    private void validateAudienceConfig(Map<String, Object> config) {
        if (config == null) return;
        for (String field : Arrays.asList("memberLevels", "userGroups")) {
            Object value = config.get(field);
            if (value != null && !(value instanceof List)) throw exception(SPU_MARKETING_CONFIG_INVALID, field + " 必须是数组");
        }
        Object limits = config.get("userGroupPurchaseLimits");
        if (limits != null && !(limits instanceof Map)) throw exception(SPU_MARKETING_CONFIG_INVALID, "userGroupPurchaseLimits 必须是对象");
        if (limits instanceof Map) for (Object value : ((Map<?, ?>) limits).values()) {
            if (!(value instanceof Number) || ((Number) value).intValue() < 0 || ((Number) value).doubleValue() != ((Number) value).intValue()) {
                throw exception(SPU_MARKETING_CONFIG_INVALID, "客户标签限购必须是非负整数");
            }
        }
    }

    @Override
    public List<Long> getGroupIds(Long spuId) {
        return productGroupService.getGroupIdsBySpuId(spuId);
    }

    @Override
    public ProductSpuAccessRespDTO checkAccess(Long id, Long userId, Integer count) {
        ProductSpuDO spu = validateSpuExists(id);
        return checkAccess(spu, userId, count, new AccessContext(userId));
    }

    private ProductSpuAccessRespDTO checkAccess(ProductSpuDO spu, Long userId, Integer count, AccessContext context) {
        Long id = spu.getId();
        ProductSpuAccessRespDTO result = new ProductSpuAccessRespDTO();
        result.setSpuId(id).setDeliveryDays(Boolean.TRUE.equals(spu.getIsPreSale()) ? spu.getDeliveryDays() : 0);
        if (!ProductSpuStatusEnum.isEnable(spu.getStatus())) return result.setVisible(false).setPurchasable(false).setReason("商品未上架");
        if (count == null || count < 1) return result.setVisible(true).setPurchasable(false).setReason("购买数量必须大于 0");
        int effective = ObjectUtil.defaultIfNull(spu.getBuyLimit(), 0);
        String levelRule = ObjectUtil.defaultIfNull(spu.getBuyLimitLevel(), "ALL");
        Map<String, Object> config = spu.getMarketingConfig();
        if (config != null && ((!isListOrMissing(config, "memberLevels"))
                || (!isListOrMissing(config, "userGroups"))
                || (!isMapOrMissing(config, "userGroupPurchaseLimits")))) {
            return result.setVisible(false).setPurchasable(false).setReason("商品人群配置无效");
        }
        List<?> allowedLevels = config == null ? Collections.emptyList() : (List<?>) config.getOrDefault("memberLevels", Collections.emptyList());
        List<?> allowedTags = config == null ? Collections.emptyList() : (List<?>) config.getOrDefault("userGroups", Collections.emptyList());
        Map<?, ?> tagLimits = config == null ? Collections.emptyMap() : (Map<?, ?>) config.getOrDefault("userGroupPurchaseLimits", Collections.emptyMap());
        for (Object cap : tagLimits.values()) {
            if (!(cap instanceof Number) || ((Number) cap).doubleValue() != ((Number) cap).intValue() || ((Number) cap).intValue() < 0) {
                return result.setVisible(false).setPurchasable(false).setReason("商品限购配置无效");
            }
        }
        boolean hasAudienceRules = !"ALL".equals(levelRule) || !allowedLevels.isEmpty() || !allowedTags.isEmpty() || !tagLimits.isEmpty();
        if (!hasAudienceRules || userId == null) {
            if (userId == null && hasAudienceRules) {
                return result.setVisible(false).setPurchasable(false).setEffectiveBuyLimit(effective).setReason("需要登录以校验商品规则");
            }
            return result.setVisible(true).setPurchasable(effective == 0 || count <= effective).setEffectiveBuyLimit(effective);
        }
        MemberUserRespDTO user = context.getUser();
        if (user == null || user.getId() == null) return result.setVisible(false).setPurchasable(false).setReason("用户数据不可用");
        int userLevel = -1;
        if (user.getLevelId() != null) {
            MemberLevelRespDTO level = context.getLevel();
            if (level == null) return result.setVisible(false).setPurchasable(false).setReason("会员等级数据不可用");
            userLevel = level.getLevel() == null ? -1 : level.getLevel();
        }
        if (!"ALL".equals(levelRule) && !levelRule.equals("L" + userLevel)) return result.setVisible(false).setPurchasable(false).setEffectiveBuyLimit(effective).setReason("当前会员等级不符合购买条件");
        if (!allowedLevels.isEmpty() && !containsId(allowedLevels, user.getLevelId())) return result.setVisible(false).setPurchasable(false).setReason("当前会员等级不符合购买条件");
        Set<String> matchingTags = new HashSet<>();
        if ((!allowedTags.isEmpty() || !tagLimits.isEmpty()) && user.getTagIds() == null) return result.setVisible(false).setPurchasable(false).setReason("客户标签数据不可用");
        Set<Object> configuredTags = new HashSet<>(allowedTags);
        configuredTags.addAll(tagLimits.keySet());
        for (Object rawTag : configuredTags) {
            long tag;
            try { tag = Long.parseLong(String.valueOf(rawTag)); } catch (NumberFormatException e) { return result.setVisible(false).setPurchasable(false).setReason("客户标签配置无效"); }
            if (tag <= 0) return result.setVisible(false).setPurchasable(false).setReason("客户标签配置无效");
            if (user.getTagIds().contains(tag)) matchingTags.add(String.valueOf(tag));
        }
        if (!allowedTags.isEmpty()) {
            Set<String> allowedTagIds = allowedTags.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
            if (Collections.disjoint(allowedTagIds, matchingTags)) return result.setVisible(false).setPurchasable(false).setReason("当前客户标签不符合购买条件");
        }
        for (String tag : matchingTags) {
            Object cap = tagLimits.get(tag);
            if (cap instanceof Number) effective = minPositive(effective, ((Number) cap).intValue());
        }
        return result.setVisible(true).setPurchasable(effective == 0 || count <= effective).setEffectiveBuyLimit(effective)
                .setReason(effective > 0 && count > effective ? "超过商品限购数量" : null);
    }

    private static boolean isListOrMissing(Map<String, Object> config, String field) {
        return !config.containsKey(field) || config.get(field) instanceof List;
    }

    private static boolean isMapOrMissing(Map<String, Object> config, String field) {
        return !config.containsKey(field) || config.get(field) instanceof Map;
    }

    private final class AccessContext {
        private final Long userId;
        private boolean userLoaded;
        private MemberUserRespDTO user;
        private boolean levelLoaded;
        private MemberLevelRespDTO level;

        private AccessContext(Long userId) { this.userId = userId; }

        private MemberUserRespDTO getUser() {
            if (!userLoaded) {
                userLoaded = true;
                user = memberUserApi.getUser(userId).getCheckedData();
            }
            return user;
        }

        private MemberLevelRespDTO getLevel() {
            if (!levelLoaded) {
                levelLoaded = true;
                MemberUserRespDTO currentUser = getUser();
                if (currentUser != null && currentUser.getLevelId() != null) {
                    level = memberLevelApi.getMemberLevel(currentUser.getLevelId()).getCheckedData();
                }
            }
            return level;
        }
    }

    private int minPositive(int current, int candidate) { return candidate <= 0 ? current : current == 0 ? candidate : Math.min(current, candidate); }

    private boolean containsId(List<?> configuredIds, Long actualId) {
        if (actualId == null) return false;
        String expected = String.valueOf(actualId);
        return configuredIds.stream().anyMatch(id -> expected.equals(String.valueOf(id)));
    }

    /**
     * 基于 SKU 的信息，初始化 SPU 的信息
     * 主要是计数相关的字段，例如说市场价、最大最小价、库存等等
     *
     * @param spu  商品 SPU
     * @param skus 商品 SKU 数组
     */
    private void initSpuFromSkus(ProductSpuDO spu, List<ProductSkuSaveReqVO> skus) {
        // sku 单价最低的商品的价格
        spu.setPrice(getMinValue(skus, ProductSkuSaveReqVO::getPrice));
        // sku 单价最低的商品的市场价格
        spu.setMarketPrice(getMinValue(skus, ProductSkuSaveReqVO::getMarketPrice));
        // sku 单价最低的商品的成本价格
        spu.setCostPrice(getMinValue(skus, ProductSkuSaveReqVO::getCostPrice));
        // skus 库存总数
        spu.setStock(getSumValue(skus, ProductSkuSaveReqVO::getStock, Math::addExact));
        // 若是 spu 已有状态则不处理
        if (spu.getStatus() == null) {
            spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus()); // 默认状态为上架
            spu.setSalesCount(0); // 默认商品销量
            spu.setBrowseCount(0); // 默认商品浏览量
        }
    }

    /**
     * 校验商品分类是否合法
     *
     * @param id 商品分类编号
     */
    private void validateCategory(Long id) {
        categoryService.validateCategory(id);
        // 校验层级
        if (categoryService.getCategoryLevel(id) < CATEGORY_LEVEL) {
            throw exception(SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR);
        }
    }

    private void validateMerchantAndBrand(Long merchantId, Long brandId) {
        merchantService.validateMerchantForManagement(merchantId);
        brandService.validateProductBrand(brandId);
        ProductBrandDO brand = brandService.getBrand(brandId);
        if (brand == null) {
            throw exception(BRAND_NOT_EXISTS);
        }
        if (!Objects.equals(merchantId, brand.getMerchantId())) {
            throw exception(BRAND_MERCHANT_MISMATCH);
        }
    }

    private void validateProductImages(ProductSpuSaveReqVO reqVO) {
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(reqVO.getPicUrl());
        if (reqVO.getSliderPicUrls() != null) {
            imageUrls.addAll(reqVO.getSliderPicUrls());
        }
        if (reqVO.getSkus() != null) {
            reqVO.getSkus().stream().map(ProductSkuSaveReqVO::getPicUrl).forEach(imageUrls::add);
        }
        if (reqVO.getDescription() != null) {
            Document description = Jsoup.parseBodyFragment(reqVO.getDescription());
            if (description.select("svg").size() > 0
                    || description.select("link[href]").stream().anyMatch(element ->
                    Arrays.stream(element.attr("rel").split("\\s+"))
                            .anyMatch("stylesheet"::equalsIgnoreCase))
                    || description.select("style").stream()
                    .anyMatch(element -> containsCssImageReference(element.data()))
                    || description.getAllElements().stream()
                    .anyMatch(element -> containsCssImageReference(element.attr("style")))) {
                throw exception(SPU_IMAGE_INVALID);
            }
            for (Element image : description.select(
                    "img[src], img[data-src], img[original-src], img[srcset], source[srcset], video[poster]")) {
                String source = image.attr("src");
                if (source.isBlank()) {
                    source = image.attr("data-src");
                }
                if (!source.isBlank()) {
                    imageUrls.add(source);
                }
                if (image.hasAttr("original-src")) {
                    imageUrls.add(image.attr("original-src"));
                }
                if (image.hasAttr("poster")) {
                    imageUrls.add(image.attr("poster"));
                }
                if (image.hasAttr("srcset")) {
                    Arrays.stream(image.attr("srcset").split(","))
                            .map(candidate -> candidate.trim().split("\\s+", 2)[0])
                            .filter(url -> !url.isBlank())
                            .forEach(imageUrls::add);
                }
            }
        }
        if (imageUrls.stream().anyMatch(this::isDataImageReference)) {
            throw exception(SPU_IMAGE_INVALID);
        }
        imageUrls.stream().filter(this::isExternalImageReference).distinct().forEach(url -> {
            if (!Boolean.TRUE.equals(fileApi.validateImageUrl(url, "product").getCheckedData())) {
                throw exception(SPU_IMAGE_INVALID);
            }
        });
    }

    private boolean isExternalImageReference(String url) {
        return url != null && !url.isBlank() && !isDataImageReference(url);
    }

    private boolean isDataImageReference(String url) {
        return url != null && url.stripLeading().regionMatches(true, 0, "data:", 0, 5);
    }

    private boolean containsCssImageReference(String style) {
        if (style == null) {
            return false;
        }
        String normalized = style.toLowerCase(Locale.ROOT);
        return normalized.contains("url") || normalized.contains("image-set") || normalized.contains("@import")
                || style.contains("\\") || style.contains("/*") || style.contains("*/");
    }

    @Override
    public List<ProductSpuDO> validateSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        // 获得商品信息
        List<ProductSpuDO> list = productSpuMapper.selectByIdsInTenantScope(ids);
        Map<Long, ProductSpuDO> spuMap = CollectionUtils.convertMap(list, ProductSpuDO::getId);
        // 校验
        ids.forEach(id -> {
            ProductSpuDO spu = spuMap.get(id);
            if (spu == null) {
                throw exception(SPU_NOT_EXISTS);
            }
            if (!ProductSpuStatusEnum.isEnable(spu.getStatus())) {
                throw exception(SPU_NOT_ENABLE, spu.getName());
            }
            // 商品可能在上架后被其商家冻结或撤销审核，购买链路必须再次校验商家状态。
            merchantService.validateMerchantAvailable(spu.getMerchantId());
        });
        return list;
    }

    @Override
    public void updateBrowseCount(Long id, int incrCount) {
        productSpuMapper.updateBrowseCount(id , incrCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id) {
        // 校验存在
        validateSpuExists(id);
        // 校验商品状态不是回收站不能删除
        ProductSpuDO spuDO = productSpuMapper.selectById(id);
        // 判断 SPU 状态是否为回收站
        if (ObjectUtil.notEqual(spuDO.getStatus(), ProductSpuStatusEnum.RECYCLE.getStatus())) {
            throw exception(SPU_NOT_RECYCLE);
        }
        // TODO 芋艿：【可选】参与活动中的商品，不允许删除？？？

        // 删除 SPU
        productSpuMapper.deleteById(id);
        // 删除关联的 SKU
        productSkuService.deleteSkuBySpuId(id);
        productGroupService.deleteRelationsBySpuId(id);
    }

    private ProductSpuDO validateSpuExists(Long id) {
        ProductSpuDO spuDO = productSpuMapper.selectById(id);
        if (spuDO == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        return spuDO;
    }

    @Override
    public ProductSpuDO getSpu(Long id) {
        ProductSpuDO spu = productSpuMapper.selectById(id);
        if (spu != null) spu.setGroupIds(getGroupIds(id));
        return spu;
    }

    @Override
    public ProductSpuDO getSpu(Long id, boolean includeDeleted) {
        if (includeDeleted) {
            ProductSpuDO spu = productSpuMapper.selectByIdIncludeDeleted(id);
            if (spu != null) spu.setGroupIds(getGroupIds(id));
            return spu;
        }
        return getSpu(id);
    }

    @Override
    public List<ProductSpuDO> getSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        Map<Long, ProductSpuDO> spuMap = convertMap(productSpuMapper.selectByIds(ids), ProductSpuDO::getId);
        // 需要按照 ids 顺序返回。例如说：店铺装修选择了 [3, 1, 2] 三个商品，返回结果还是 [3, 1, 2]  这样的顺序
        List<ProductSpuDO> spus = convertList(ids, spuMap::get);
        spus.stream().filter(Objects::nonNull).forEach(spu -> spu.setGroupIds(getGroupIds(spu.getId())));
        return spus;
    }

    @Override
    public List<ProductSpuDO> getAvailableSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        Map<Long, ProductSpuDO> spuMap = convertMap(productSpuMapper.selectAvailableByIds(ids), ProductSpuDO::getId);
        return convertList(ids, spuMap::get).stream().filter(Objects::nonNull).toList();
    }

    @Override
    public ProductSpuDO getAvailableSpu(Long id) {
        return productSpuMapper.selectAvailableById(id);
    }

    @Override
    public List<ProductSpuDO> getSpuListByStatus(Integer status) {
        return productSpuMapper.selectList(ProductSpuDO::getStatus, status);
    }

    @Override
    public List<ProductSpuDO> getSpuListByStatusAndMerchantId(Integer status, Long merchantId) {
        return productSpuMapper.selectListByStatusAndMerchantId(status, merchantId);
    }

    @Override
    public PageResult<ProductSpuDO> getSpuPage(ProductSpuPageReqVO pageReqVO) {
        PageResult<ProductSpuDO> page = productSpuMapper.selectPage(pageReqVO, getCategoryIdsWithChildren(pageReqVO));
        page.getList().forEach(spu -> spu.setGroupIds(getGroupIds(spu.getId())));
        return page;
    }

    @Override
    public PageResult<ProductSpuDO> getSpuPage(AppProductSpuPageReqVO pageReqVO) {
        PageResult<ProductSpuDO> page = selectAppSpuPage(pageReqVO);
        page.getList().forEach(spu -> spu.setGroupIds(getGroupIds(spu.getId())));
        return page;
    }

    @Override
    public PageResult<ProductSpuDO> getVisibleSpuPage(AppProductSpuPageReqVO pageReqVO, Long userId) {
        final int pageSize = pageReqVO.getPageSize();
        final long start = (long) (pageReqVO.getPageNo() - 1) * pageSize;
        final long end = start + pageSize;
        AppProductSpuPageReqVO scanReq = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(pageReqVO, AppProductSpuPageReqVO.class);
        scanReq.setPageNo(1);
        scanReq.setPageSize(200);
        AccessContext context = new AccessContext(userId);
        List<ProductSpuDO> selected = new ArrayList<>(pageSize);
        long visibleCount = 0;
        long candidateCount;
        int candidatePage = 1;
        do {
            scanReq.setPageNo(candidatePage++);
            PageResult<ProductSpuDO> candidates = selectAppSpuPage(scanReq);
            candidateCount = candidates.getTotal();
            if (candidates.getList().isEmpty()) break;
            for (ProductSpuDO spu : candidates.getList()) {
                if (!Boolean.TRUE.equals(checkAccess(spu, userId, 1, context).getVisible())) continue;
                if (visibleCount >= start && visibleCount < end) selected.add(spu);
                visibleCount++;
            }
        } while ((long) (candidatePage - 1) * scanReq.getPageSize() < candidateCount);
        selected.forEach(spu -> spu.setGroupIds(getGroupIds(spu.getId())));
        return new PageResult<>(selected, visibleCount);
    }

    private PageResult<ProductSpuDO> selectAppSpuPage(AppProductSpuPageReqVO pageReqVO) {
        // 查找时，如果查找某个分类编号，则包含它的子分类。因为顶级分类不包含商品
        Set<Long> categoryIds = new HashSet<>();
        if (pageReqVO.getCategoryId() != null && pageReqVO.getCategoryId() > 0) {
            categoryIds.add(pageReqVO.getCategoryId());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setStatus(CommonStatusEnum.ENABLE.getStatus()).setParentId(pageReqVO.getCategoryId()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        if (CollUtil.isNotEmpty(pageReqVO.getCategoryIds())) {
            categoryIds.addAll(pageReqVO.getCategoryIds());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setStatus(CommonStatusEnum.ENABLE.getStatus()).setParentIds(pageReqVO.getCategoryIds()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        // 分页查询
        return productSpuMapper.selectPage(pageReqVO, categoryIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpuStock(Map<Long, Integer> stockIncrCounts) {
        stockIncrCounts.forEach((id, incCount) -> productSpuMapper.updateStock(id, incCount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpuStatus(ProductSpuUpdateStatusReqVO updateReqVO) {
        // 校验存在
        ProductSpuDO spu = validateSpuExists(updateReqVO.getId());
        // 状态更新同样属于经营操作，避免管理员在商家冻结或资料复审期间重新上架商品。
        merchantService.validateMerchantForManagement(spu.getMerchantId());
        // TODO 芋艿：【可选】参与活动中的商品，不允许下架？？？

        // 更新状态
        ProductSpuDO productSpuDO = spu.setStatus(updateReqVO.getStatus());
        productSpuMapper.updateById(productSpuDO);
    }

    @Override
    public Map<Integer, Long> getTabsCount(ProductSpuPageReqVO reqVO) {
        Set<Long> categoryIds = getCategoryIdsWithChildren(reqVO);
        Map<Integer, Long> counts = Maps.newLinkedHashMapWithExpectedSize(5);
        // 每个 tab 的数量 = 筛选条件（name/categoryId/createTime）+ 该 tab 的状态/库存条件
        counts.put(ProductSpuPageReqVO.FOR_SALE,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.FOR_SALE, categoryIds));
        counts.put(ProductSpuPageReqVO.IN_WAREHOUSE,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.IN_WAREHOUSE, categoryIds));
        counts.put(ProductSpuPageReqVO.SOLD_OUT,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.SOLD_OUT, categoryIds));
        counts.put(ProductSpuPageReqVO.ALERT_STOCK,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.ALERT_STOCK, categoryIds));
        counts.put(ProductSpuPageReqVO.RECYCLE_BIN,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.RECYCLE_BIN, categoryIds));
        return counts;
    }

    /**
     * 获得分类编号集合，包含其子分类。因为顶级分类不包含商品
     *
     * 注意：管理端不过滤分类状态，保证管理员可以查询到所有商品
     */
    private Set<Long> getCategoryIdsWithChildren(ProductSpuPageReqVO pageReqVO) {
        Set<Long> categoryIds = new HashSet<>();
        if (pageReqVO.getCategoryId() != null && pageReqVO.getCategoryId() > 0) {
            categoryIds.add(pageReqVO.getCategoryId());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setParentId(pageReqVO.getCategoryId()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        if (CollUtil.isNotEmpty(pageReqVO.getCategoryIds())) {
            categoryIds.addAll(pageReqVO.getCategoryIds());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setParentIds(pageReqVO.getCategoryIds()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        return categoryIds;
    }

    @Override
    public Long getSpuCountByCategoryId(Long categoryId) {
        return productSpuMapper.selectCount(ProductSpuDO::getCategoryId, categoryId);
    }

}
