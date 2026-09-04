package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public CommonResult<String> createFile(FileCreateReqDTO createReqDTO) {
        return success(fileService.createFile(createReqDTO.getContent(), createReqDTO.getName(),
                createReqDTO.getDirectory(), createReqDTO.getType()));
    }

    @Override
    public CommonResult<FileCreateRespDTO> createPrivateFile(FileCreateReqDTO createReqDTO) {
        FileDO file = fileService.createPrivateFile(createReqDTO.getContent(), createReqDTO.getName(),
                createReqDTO.getDirectory(), createReqDTO.getType());
        return success(new FileCreateRespDTO().setId(file.getId()).setUrl(file.getUrl()));
    }

    @Override
    public CommonResult<String> presignGetUrl(String url, Integer expirationSeconds) {
        return success(fileService.presignGetUrl(url, expirationSeconds));
    }

    @Override
    public CommonResult<String> presignGetUrl(Long fileId, Integer expirationSeconds) {
        return success(fileService.presignGetUrl(fileId, expirationSeconds));
    }

    @Override
    public CommonResult<Boolean> isPrivatePresignedGetSupported() {
        return success(fileService.isPrivatePresignedGetSupported());
    }

    @Override
    public CommonResult<Boolean> isPrivateMasterSupported() {
        return success(fileService.isPrivateMasterSupported());
    }

}
