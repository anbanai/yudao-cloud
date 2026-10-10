package cn.iocoder.yudao.module.product.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Product 错误码枚举类
 *
 * product 系统，使用 1-008-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商品分类相关 1-008-001-000 ============
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_008_001_000, "商品分类不存在");
    ErrorCode CATEGORY_PARENT_NOT_EXISTS = new ErrorCode(1_008_001_001, "父分类不存在");
    ErrorCode CATEGORY_PARENT_NOT_FIRST_LEVEL = new ErrorCode(1_008_001_002, "父分类不能是二级分类");
    ErrorCode CATEGORY_EXISTS_CHILDREN = new ErrorCode(1_008_001_003, "存在子分类，无法删除");
    ErrorCode CATEGORY_DISABLED = new ErrorCode(1_008_001_004, "商品分类({})已禁用，无法使用");
    ErrorCode CATEGORY_HAVE_BIND_SPU = new ErrorCode(1_008_001_005, "类别下存在商品，无法删除");

    // ========== 商品品牌相关编号 1-008-002-000 ==========
    ErrorCode BRAND_NOT_EXISTS = new ErrorCode(1_008_002_000, "品牌不存在");
    ErrorCode BRAND_DISABLED = new ErrorCode(1_008_002_001, "品牌已禁用");
    ErrorCode BRAND_NAME_EXISTS = new ErrorCode(1_008_002_002, "品牌名称已存在");

    // ========== 商品属性项 1-008-003-000 ==========
    ErrorCode PROPERTY_NOT_EXISTS = new ErrorCode(1_008_003_000, "属性项不存在");
    ErrorCode PROPERTY_EXISTS = new ErrorCode(1_008_003_001, "属性项的名称已存在");
    ErrorCode PROPERTY_DELETE_FAIL_VALUE_EXISTS = new ErrorCode(1_008_003_002, "属性项下存在属性值，无法删除");

    // ========== 商品属性值 1-008-004-000 ==========
    ErrorCode PROPERTY_VALUE_NOT_EXISTS = new ErrorCode(1_008_004_000, "属性值不存在");
    ErrorCode PROPERTY_VALUE_EXISTS = new ErrorCode(1_008_004_001, "属性值的名称已存在");

    // ========== 商品 SPU 1-008-005-000 ==========
    ErrorCode SPU_NOT_EXISTS = new ErrorCode(1_008_005_000, "商品 SPU 不存在");
    ErrorCode SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR = new ErrorCode(1_008_005_001, "商品分类不正确，原因：必须使用第二级的商品分类及以下");
    ErrorCode SPU_SAVE_FAIL_COUPON_TEMPLATE_NOT_EXISTS = new ErrorCode(1_008_005_002, "商品 SPU 保存失败，原因：优惠劵不存在");
    ErrorCode SPU_NOT_ENABLE = new ErrorCode(1_008_005_003, "商品 SPU【{}】不处于上架状态");
    ErrorCode SPU_NOT_RECYCLE = new ErrorCode(1_008_005_004, "商品 SPU 不处于回收站状态");
    ErrorCode SPU_IMAGE_INVALID = new ErrorCode(1_008_005_005,
            "商品图片无效，仅支持 3MB 以内的 JPG、PNG、BMP 图片");

    // ========== 商品 SKU 1-008-006-000 ==========
    ErrorCode SKU_NOT_EXISTS = new ErrorCode(1_008_006_000, "商品 SKU 不存在");
    ErrorCode SKU_PROPERTIES_DUPLICATED = new ErrorCode(1_008_006_001, "商品 SKU 的属性组合存在重复");
    ErrorCode SPU_ATTR_NUMBERS_MUST_BE_EQUALS = new ErrorCode(1_008_006_002, "一个 SPU 下的每个 SKU，其属性项必须一致");
    ErrorCode SPU_SKU_NOT_DUPLICATE = new ErrorCode(1_008_006_003, "一个 SPU 下的每个 SKU，必须不重复");
    ErrorCode SKU_STOCK_NOT_ENOUGH = new ErrorCode(1_008_006_004, "商品 SKU 库存不足");

    // ========== 商品 评价 1-008-007-000 ==========
    ErrorCode COMMENT_NOT_EXISTS = new ErrorCode(1_008_007_000, "商品评价不存在");
    ErrorCode COMMENT_ORDER_EXISTS = new ErrorCode(1_008_007_001, "订单的商品评价已存在");

    // ========== 商品 收藏 1-008-008-000 ==========
    ErrorCode FAVORITE_EXISTS = new ErrorCode(1_008_008_000, "该商品已经被收藏");
    ErrorCode FAVORITE_NOT_EXISTS = new ErrorCode(1_008_008_001, "商品收藏不存在");

    ErrorCode GROUP_NOT_EXISTS = new ErrorCode(1_008_009_000, "商品分组不存在");
    ErrorCode GROUP_NAME_EXISTS = new ErrorCode(1_008_009_001, "商品分组名称已存在");
    ErrorCode GROUP_DISABLED = new ErrorCode(1_008_009_002, "商品分组已禁用");
    ErrorCode GROUP_HAVE_BIND_SPU = new ErrorCode(1_008_009_003, "分组下存在商品，无法删除");

    // ========== 商家相关 1-008-010-000 ==========
    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_008_010_000, "商家不存在");
    ErrorCode MERCHANT_NOT_AVAILABLE = new ErrorCode(1_008_010_001, "商家未审核通过或已被冻结");
    ErrorCode MERCHANT_NAME_EXISTS = new ErrorCode(1_008_010_002, "商家名称已存在");
    ErrorCode MERCHANT_QUALIFICATION_NOT_EXISTS = new ErrorCode(1_008_010_003, "商家资质不存在");
    ErrorCode MERCHANT_REVIEW_STATUS_ERROR = new ErrorCode(1_008_010_004, "商家当前状态不允许执行审核操作");
    ErrorCode BRAND_MERCHANT_MISMATCH = new ErrorCode(1_008_010_005, "品牌不属于当前商家");
    ErrorCode MERCHANT_APPLICATION_NOT_EXISTS = new ErrorCode(1_008_010_006, "商家申请版本不存在");
    ErrorCode MERCHANT_APPLICATION_STATUS_ERROR = new ErrorCode(1_008_010_007, "商家申请当前状态不允许执行此操作");
    ErrorCode MERCHANT_APPLICATION_QUALIFICATION_REQUIRED = new ErrorCode(1_008_010_008, "请补充必需的商家资质");
    ErrorCode MERCHANT_APPLICATION_NOT_OWNER = new ErrorCode(1_008_010_009, "无权访问该商家申请");
    ErrorCode MERCHANT_BUSINESS_TYPE_NOT_EXISTS = new ErrorCode(1_008_010_010, "经营类型不存在或已停用");
    ErrorCode MERCHANT_BUSINESS_TYPE_CODE_EXISTS = new ErrorCode(1_008_010_011, "经营类型编码已存在");
    ErrorCode MERCHANT_BUSINESS_TYPE_IN_USE = new ErrorCode(1_008_010_012, "经营类型已被申请使用，无法删除");
    ErrorCode MERCHANT_IDENTITY_BIND_FAILED = new ErrorCode(1_008_010_013, "商家账号绑定失败");
    ErrorCode MERCHANT_APPLICATION_INFO_INCOMPLETE = new ErrorCode(1_008_010_014, "商家申请资料不完整");
    ErrorCode MERCHANT_APPLICATION_FILE_INVALID = new ErrorCode(1_008_010_015, "商家资质文件无效或不属于当前账号");

}
