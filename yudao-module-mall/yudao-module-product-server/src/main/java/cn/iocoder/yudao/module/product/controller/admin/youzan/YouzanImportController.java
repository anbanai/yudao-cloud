package cn.iocoder.yudao.module.product.controller.admin.youzan;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.service.youzan.YouzanImportService;
import cn.iocoder.yudao.module.product.service.youzan.YouzanWorkbook;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/product/youzan-import")
@PreAuthorize("@ss.hasPermission('product:youzan:import')")
public class YouzanImportController {
    private final YouzanImportService service;
    public YouzanImportController(YouzanImportService service) { this.service = service; }

    @GetMapping("/merchants")
    public CommonResult<List<YouzanImportService.MerchantOption>> getAuthorizedMerchants() {
        return success(service.listAuthorizedMerchants());
    }

    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=youzan-product.xlsx");
        YouzanWorkbook.template(response.getOutputStream());
    }

    @PostMapping("/preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<YouzanImportService.Batch> preview(@RequestParam(required = false) Long merchantId, @RequestParam MultipartFile file) throws Exception {
        return success(service.preview(merchantId, file));
    }

    @PostMapping("/confirm")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<YouzanImportService.Batch> confirm(@RequestParam(required = false) Long merchantId, @RequestParam UUID batchId) throws Exception {
        return success(service.confirm(merchantId, batchId));
    }

    @GetMapping("/result")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<YouzanImportService.Batch> result(@RequestParam(required = false) Long merchantId, @RequestParam UUID batchId) throws Exception {
        return success(service.result(merchantId, batchId));
    }

    @GetMapping("/pending")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<List<String>> pending(@RequestParam(required = false) UUID before) {
        return success(service.pending(before));
    }

    @PostMapping("/assign")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<YouzanImportService.Batch> assign(@RequestParam UUID batchId, @RequestParam long merchantId) throws Exception {
        return success(service.assign(batchId, merchantId));
    }

    public record Supplement(@NotNull UUID batchId, @NotNull String product, @NotNull Map<String,String> sourceByProperties, @Valid @NotNull ProductSpuSaveReqVO form) {}

    @PostMapping("/supplement")
    @PreAuthorize("@ss.hasPermission('product:youzan:import') and @ss.hasPermission('product:spu:create')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Long> supplement(@RequestParam long merchantId, @Valid @RequestBody Supplement body) {
        return success(service.supplement(merchantId, body.batchId(), body.product(), body.sourceByProperties(), body.form()));
    }
}
