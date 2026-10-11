package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationRequirementSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantBusinessTypeDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationRequirementDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantBusinessTypeMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationRequirementMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductMerchantBusinessTypeServiceImpl implements ProductMerchantBusinessTypeService {

    @Resource
    private ProductMerchantBusinessTypeMapper businessTypeMapper;
    @Resource
    private ProductMerchantQualificationRequirementMapper requirementMapper;
    @Resource
    private ProductMerchantApplicationMapper applicationMapper;

    @Override
    public List<ProductMerchantBusinessTypeRespVO> getList(Boolean enabledOnly) {
        return businessTypeMapper.selectListByEnabled(Boolean.TRUE.equals(enabledOnly) ? Boolean.TRUE : null).stream()
                .map(item -> toResp(item, Boolean.TRUE.equals(enabledOnly))).toList();
    }

    @Override
    public PageResult<ProductMerchantBusinessTypeRespVO> getPage(ProductMerchantBusinessTypePageReqVO reqVO) {
        PageResult<ProductMerchantBusinessTypeDO> page = businessTypeMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(item -> toResp(item, false)).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ProductMerchantBusinessTypeSaveReqVO reqVO) {
        ProductMerchantBusinessTypeDO duplicate = businessTypeMapper.selectByCode(reqVO.getCode());
        ProductMerchantBusinessTypeDO old = reqVO.getId() == null ? null : businessTypeMapper.selectById(reqVO.getId());
        if (old == null && reqVO.getId() != null) {
            throw exception(MERCHANT_BUSINESS_TYPE_NOT_EXISTS);
        }
        if (duplicate != null && (old == null || !duplicate.getId().equals(old.getId()))) {
            throw exception(MERCHANT_BUSINESS_TYPE_CODE_EXISTS);
        }
        String oldCode = old == null ? null : old.getCode();
        if (old != null && !oldCode.equals(reqVO.getCode())
                && applicationMapper.selectCountByBusinessType(oldCode) > 0) {
            throw exception(MERCHANT_BUSINESS_TYPE_IN_USE);
        }

        ProductMerchantBusinessTypeDO businessType = old == null ? new ProductMerchantBusinessTypeDO() : old;
        businessType.setCode(reqVO.getCode()).setName(reqVO.getName())
                .setEnabled(reqVO.getEnabled() == null || reqVO.getEnabled())
                .setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        if (old == null) {
            businessTypeMapper.insert(businessType);
        } else {
            businessTypeMapper.updateById(businessType);
        }

        if (reqVO.getRequirements() != null) {
            if (oldCode != null) {
                requirementMapper.deleteByBusinessType(oldCode);
            }
            Set<String> codes = new HashSet<>();
            for (ProductMerchantQualificationRequirementSaveReqVO item : reqVO.getRequirements()) {
                if (!codes.add(item.getCode())) {
                    throw exception(MERCHANT_BUSINESS_TYPE_CODE_EXISTS);
                }
                validateCertificateNoPattern(item.getCertificateNoPattern());
                requirementMapper.insert(new ProductMerchantQualificationRequirementDO()
                        .setBusinessType(reqVO.getCode()).setCode(item.getCode()).setName(item.getName())
                        .setRequired(item.getRequired() == null || item.getRequired())
                        .setCertificateNoRequired(Boolean.TRUE.equals(item.getCertificateNoRequired()))
                        .setCertificateNoPattern(item.getCertificateNoPattern())
                        .setValidityRequired(Boolean.TRUE.equals(item.getValidityRequired()))
                        .setEnabled(item.getEnabled() == null || item.getEnabled())
                        .setSort(item.getSort() == null ? 0 : item.getSort()));
            }
        }
        return businessType.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProductMerchantBusinessTypeDO businessType = businessTypeMapper.selectById(id);
        if (businessType == null) {
            throw exception(MERCHANT_BUSINESS_TYPE_NOT_EXISTS);
        }
        if (applicationMapper.selectCountByBusinessType(businessType.getCode()) > 0) {
            throw exception(MERCHANT_BUSINESS_TYPE_IN_USE);
        }
        requirementMapper.deleteByBusinessType(businessType.getCode());
        businessTypeMapper.deleteById(id);
    }

    private ProductMerchantBusinessTypeRespVO toResp(ProductMerchantBusinessTypeDO businessType, boolean enabledRequirementsOnly) {
        ProductMerchantBusinessTypeRespVO resp = new ProductMerchantBusinessTypeRespVO();
        resp.setId(businessType.getId());
        resp.setCode(businessType.getCode());
        resp.setName(businessType.getName());
        resp.setEnabled(businessType.getEnabled());
        resp.setSort(businessType.getSort());
        List<ProductMerchantQualificationRequirementDO> configuredRequirements = enabledRequirementsOnly
                ? requirementMapper.selectEnabledListByBusinessType(businessType.getCode())
                : requirementMapper.selectListByBusinessType(businessType.getCode());
        List<ProductMerchantQualificationRequirementSaveReqVO> requirements = configuredRequirements.stream().map(item -> {
                    ProductMerchantQualificationRequirementSaveReqVO requirement = new ProductMerchantQualificationRequirementSaveReqVO();
                    requirement.setCode(item.getCode());
                    requirement.setName(item.getName());
                    requirement.setRequired(item.getRequired());
                    requirement.setCertificateNoRequired(item.getCertificateNoRequired());
                    requirement.setCertificateNoPattern(item.getCertificateNoPattern());
                    requirement.setValidityRequired(item.getValidityRequired());
                    requirement.setEnabled(item.getEnabled());
                    requirement.setSort(item.getSort());
                    return requirement;
        }).toList();
        resp.setRequirements(requirements);
        return resp;
    }

    private void validateCertificateNoPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw exception(MERCHANT_APPLICATION_FILE_INVALID);
        }
    }
}
