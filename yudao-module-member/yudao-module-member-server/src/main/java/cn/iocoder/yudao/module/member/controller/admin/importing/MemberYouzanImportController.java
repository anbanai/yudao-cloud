package cn.iocoder.yudao.module.member.controller.admin.importing;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.member.service.importing.MemberYouzanImportService;
import cn.iocoder.yudao.module.member.service.importing.YouzanCustomerImportRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 有赞客户积分导入")
@RestController
@RequestMapping("/member/youzan-import")
@Validated
public class MemberYouzanImportController {

    @Resource
    private MemberYouzanImportService importService;

    @GetMapping("/merchants")
    @Operation(summary = "获得当前操作人授权的有赞会员导入商家编号")
    @PreAuthorize("@ss.hasPermission('member:youzan-import:import')")
    public CommonResult<List<String>> getAuthorizedMerchantIds() {
        return success(importService.listAuthorizedMerchantIds());
    }

    @GetMapping("/template")
    @Operation(summary = "下载有赞客户积分导入空模板")
    @PreAuthorize("@ss.hasPermission('member:youzan-import:query')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "有赞客户积分导入模板.xlsx", YouzanCustomerImportRules.SHEET_NAME,
                YouzanCustomerImportRules.HEADERS.stream().map(java.util.List::of).toList(), Collections.emptyList());
    }

    @PostMapping("/preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @Operation(summary = "上传并校验有赞客户积分快照")
    @PreAuthorize("@ss.hasPermission('member:youzan-import:import')")
    public CommonResult<MemberYouzanImportService.PreviewResult> preview(@RequestParam("file") MultipartFile file, @RequestParam(value = "merchantId", required = false) Long merchantId)
            throws IOException {
        return success(importService.preview(file, merchantId));
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认客户导入；未选择商家的批次保存为待分配")
    @PreAuthorize("@ss.hasPermission('member:youzan-import:import')")
    public CommonResult<MemberYouzanImportService.ImportResult> confirm(@RequestParam("batchId") Long batchId) {
        return success(importService.confirm(batchId));
    }

    @GetMapping("/pending")
    @Operation(summary = "按批次号倒序查询当前操作人的待分配批次，每页 20 条")
    @PreAuthorize("@ss.hasAnyPermissions('member:youzan-import:query', 'member:youzan-import:import')")
    public CommonResult<List<String>> getPending(@RequestParam(value = "before", required = false) Long beforeBatchId) {
        return success(importService.listPending(beforeBatchId));
    }

    @PostMapping("/assign")
    @Operation(summary = "将待分配批次导入授权商家")
    @PreAuthorize("@ss.hasPermission('member:youzan-import:import')")
    public CommonResult<MemberYouzanImportService.ImportResult> assign(@RequestParam("batchId") Long batchId,
                                                                        @RequestParam("merchantId") Long merchantId) {
        return success(importService.assign(batchId, merchantId));
    }

    @GetMapping("/result")
    @Operation(summary = "查询有赞客户积分导入批次结果")
    @PreAuthorize("@ss.hasAnyPermissions('member:youzan-import:query', 'member:youzan-import:import')")
    public CommonResult<MemberYouzanImportService.ImportResult> getResult(@RequestParam("batchId") Long batchId) {
        return success(importService.getResult(batchId));
    }
}
