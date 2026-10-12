package cn.iocoder.yudao.module.infra.controller.merchant.file;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.controller.merchant.file.vo.MerchantQualificationUploadRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.enums.FileUploadSceneEnum;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileUploadPolicy;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_IS_EMPTY;

@Tag(name = "商家工作台 - 入驻资质文件")
@RestController
@RequestMapping("/infra/file")
@Validated
public class MerchantFileController {

    private static final int PREVIEW_EXPIRATION_SECONDS = 15 * 60;

    @Resource
    private FileService fileService;

    @PostMapping("/qualification-upload")
    @Operation(summary = "上传商家入驻资质文件")
    public CommonResult<MerchantQualificationUploadRespVO> uploadQualification(
            @RequestParam("file") @NotNull MultipartFile file) throws Exception {
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || !UserTypeEnum.MERCHANT.getValue().equals(loginUser.getUserType())) {
            throw new org.springframework.security.access.AccessDeniedException("仅商家账号可以上传入驻资质");
        }
        Long userId = loginUser.getId();
        if (userId == null || file.isEmpty()) {
            throw exception(FILE_IS_EMPTY);
        }
        FileUploadPolicy.validateMetadata(FileUploadSceneEnum.MERCHANT_QUALIFICATION, file.getOriginalFilename(),
                file.getContentType(), file.getSize());
        byte[] content = IoUtil.readBytes(file.getInputStream());
        FileUploadPolicy.validateQualificationDocument(file.getOriginalFilename(), content, file.getContentType());
        FileDO savedFile = fileService.createPrivateFile(content, file.getOriginalFilename(),
                "merchant-qualifications/" + userId, file.getContentType());
        String previewUrl = fileService.presignGetUrl(savedFile.getId(), PREVIEW_EXPIRATION_SECONDS);
        return success(new MerchantQualificationUploadRespVO().setFileId(savedFile.getId())
                .setPreviewUrl(previewUrl).setScene(FileUploadSceneEnum.MERCHANT_QUALIFICATION.getValue()));
    }

}
