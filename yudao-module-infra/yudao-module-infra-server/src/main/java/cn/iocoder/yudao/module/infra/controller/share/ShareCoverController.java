package cn.iocoder.yudao.module.infra.controller.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.controller.share.vo.ShareCoverConfigRespVO;
import cn.iocoder.yudao.module.infra.controller.share.vo.ShareCoverSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import cn.iocoder.yudao.module.infra.service.config.ConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// Spring Boot 3 uses Jakarta EE annotations.
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.CONFIG_NOT_EXISTS;

@Tag(name = "商城基础 - 小程序分享封面")
@RestController
@RequestMapping("/infra/share-cover")
@Validated
public class ShareCoverController {

    public static final String CONFIG_KEY = "shopro.share.cover";

    @Resource
    private ConfigService configService;
    @Resource
    private ObjectMapper objectMapper;

    @GetMapping("/get")
    @Operation(summary = "获取小程序分享封面配置")
    public CommonResult<ShareCoverConfigRespVO> getConfig() {
        ConfigDO config = configService.getConfigByKey(CONFIG_KEY);
        if (config == null || config.getValue() == null || config.getValue().trim().isEmpty()) {
            return success(new ShareCoverConfigRespVO());
        }
        try {
            return success(objectMapper.readValue(config.getValue(), ShareCoverConfigRespVO.class));
        } catch (JsonProcessingException ex) {
            throw exception(CONFIG_NOT_EXISTS);
        }
    }

    @PutMapping("/save")
    @Operation(summary = "保存小程序分享封面配置")
    @PreAuthorize("@ss.hasPermission('infra:share-cover:save')")
    public CommonResult<Boolean> saveConfig(@Validated @RequestBody ShareCoverSaveReqVO reqVO) {
        final String value;
        try {
            value = objectMapper.writeValueAsString(reqVO);
        } catch (JsonProcessingException ex) {
            throw exception(CONFIG_NOT_EXISTS);
        }
        ConfigDO config = configService.getConfigByKey(CONFIG_KEY);
        cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigSaveReqVO configVO =
                new cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigSaveReqVO();
        configVO.setId(config == null ? null : config.getId());
        configVO.setCategory("商城基础");
        configVO.setName("小程序分享封面");
        configVO.setKey(CONFIG_KEY);
        configVO.setValue(value);
        configVO.setVisible(true);
        configVO.setRemark("首页、个人中心及自定义页面分享卡片封面配置");
        if (config == null) {
            configService.createConfig(configVO);
        } else {
            configService.updateConfig(configVO);
        }
        return success(true);
    }
}
