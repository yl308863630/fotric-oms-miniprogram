package com.oms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.dto.AuthorizationImportResult;
import com.oms.dto.AuthorizationVerifyResult;
import com.oms.entity.AuthorizationRecord;
import com.oms.entity.AuthorizationScanLog;
import com.oms.service.AuthorizationScanLogService;
import com.oms.service.AuthorizationService;
import com.oms.service.AuthorizationImportMappingService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authorizations")
public class AuthorizationController {
    private final AuthorizationService authorizationService;
    private final AuthorizationScanLogService scanLogService;
    private final AuthorizationImportMappingService importMappingService;
    private final ObjectMapper objectMapper;

    public AuthorizationController(AuthorizationService authorizationService,
                                   AuthorizationScanLogService scanLogService,
                                   AuthorizationImportMappingService importMappingService,
                                   ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.scanLogService = scanLogService;
        this.importMappingService = importMappingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import-xlsx")
    public ResponseEntity<AuthorizationImportResult> importXlsx(@RequestParam("file") MultipartFile file,
                                                                @RequestParam(required = false) String mappingJson) {
        Map<String, String> mapping = null;
        if (mappingJson != null && !mappingJson.isBlank()) {
            try {
                mapping = objectMapper.readValue(mappingJson, new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("mappingJson 不是有效 JSON");
            }
        }
        return ResponseEntity.ok(authorizationService.importFromXlsx(file, mapping));
    }

    @GetMapping("/import-mappings")
    public List<Map<String, Object>> listImportMappings() {
        return importMappingService.listCurrentUserMappings();
    }

    @PostMapping("/import-mappings")
    public Map<String, Object> createImportMapping(@RequestBody Map<String, Object> body) {
        String mappingName = body.get("mappingName") == null ? null : String.valueOf(body.get("mappingName"));
        Boolean setDefault = body.get("setDefault") instanceof Boolean b ? b : false;
        Map<String, String> mapping = body.get("mapping") instanceof Map<?, ?> m
                ? objectMapper.convertValue(m, new TypeReference<>() {}) : Map.of();
        return importMappingService.saveForCurrentUser(null, mappingName, mapping, setDefault);
    }

    @PutMapping("/import-mappings/{id}")
    public Map<String, Object> updateImportMapping(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String mappingName = body.get("mappingName") == null ? null : String.valueOf(body.get("mappingName"));
        Boolean setDefault = body.get("setDefault") instanceof Boolean b ? b : false;
        Map<String, String> mapping = body.get("mapping") instanceof Map<?, ?> m
                ? objectMapper.convertValue(m, new TypeReference<>() {}) : Map.of();
        return importMappingService.saveForCurrentUser(id, mappingName, mapping, setDefault);
    }

    @PostMapping("/import-mappings/{id}/set-default")
    public Map<String, Object> setDefaultImportMapping(@PathVariable Long id) {
        return importMappingService.setDefaultForCurrentUser(id);
    }

    @DeleteMapping("/import-mappings/{id}")
    public ResponseEntity<Void> deleteImportMapping(@PathVariable Long id) {
        importMappingService.deleteForCurrentUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public Page<AuthorizationRecord> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return authorizationService.search(keyword, status, pageable);
    }

    @GetMapping("/{id}")
    public AuthorizationRecord get(@PathVariable Long id) {
        return authorizationService.get(id);
    }

    @PostMapping
    public AuthorizationRecord create(@RequestBody AuthorizationRecord body) {
        body.setId(null);
        return authorizationService.save(body);
    }

    @PutMapping("/{id}")
    public AuthorizationRecord update(@PathVariable Long id, @RequestBody AuthorizationRecord body) {
        body.setId(id);
        return authorizationService.save(body);
    }

    @PostMapping("/{id}/generate")
    public AuthorizationRecord generate(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String watermarkCode = body != null ? body.get("watermarkCode") : null;
        String templateUrl = body != null ? body.get("templateUrl") : null;
        return authorizationService.generateDocument(id, watermarkCode, templateUrl);
    }

    /** 推荐：token 放查询参数，避免微信/网关对 path 编码、截断导致验真失败 */
    @GetMapping(value = "/verify", params = "token")
    public AuthorizationVerifyResult verifyByQuery(@RequestParam("token") String token,
                                                     @RequestParam(required = false, defaultValue = "scan") String channel,
                                                     HttpServletRequest request) {
        return authorizationService.verify(token, channel, request);
    }

    @GetMapping("/verify/{token:.+}")
    public AuthorizationVerifyResult verify(@PathVariable String token,
                                            @RequestParam(required = false, defaultValue = "scan") String channel,
                                            HttpServletRequest request) {
        return authorizationService.verify(token, channel, request);
    }

    @GetMapping("/scan-logs")
    public Page<AuthorizationScanLog> scanLogs(@RequestParam(required = false) Long authorizationRecordId,
                                               @RequestParam(required = false) String result,
                                               @RequestParam(required = false) String channel,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return scanLogService.search(authorizationRecordId, result, channel, pageable);
    }

    @GetMapping("/scan-logs/export")
    public ResponseEntity<byte[]> exportScanLogs(@RequestParam(required = false) Long authorizationRecordId,
                                                 @RequestParam(required = false) String result,
                                                 @RequestParam(required = false) String channel) {
        List<AuthorizationScanLog> logs = scanLogService.searchAll(authorizationRecordId, result, channel);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("scan_logs");
            var h = sheet.createRow(0);
            String[] heads = {"时间", "授权ID", "授权编码", "验真结果", "渠道", "IP", "UA", "国家", "地区", "城市", "操作用户", "操作人"};
            for (int i = 0; i < heads.length; i++) h.createCell(i).setCellValue(heads[i]);
            int rowNum = 1;
            for (AuthorizationScanLog log : logs) {
                var r = sheet.createRow(rowNum++);
                r.createCell(0).setCellValue(log.getCreateTime() == null ? "" : String.valueOf(log.getCreateTime()));
                r.createCell(1).setCellValue(log.getAuthorizationRecordId() == null ? 0 : log.getAuthorizationRecordId());
                r.createCell(2).setCellValue(log.getAuthorizationCode() == null ? "" : log.getAuthorizationCode());
                r.createCell(3).setCellValue(log.getVerifyResult() == null ? "" : log.getVerifyResult());
                r.createCell(4).setCellValue(log.getChannel() == null ? "" : log.getChannel());
                r.createCell(5).setCellValue(log.getClientIp() == null ? "" : log.getClientIp());
                r.createCell(6).setCellValue(log.getUserAgent() == null ? "" : log.getUserAgent());
                r.createCell(7).setCellValue(log.getGeoCountry() == null ? "" : log.getGeoCountry());
                r.createCell(8).setCellValue(log.getGeoRegion() == null ? "" : log.getGeoRegion());
                r.createCell(9).setCellValue(log.getGeoCity() == null ? "" : log.getGeoCity());
                r.createCell(10).setCellValue(log.getOperatorUsername() == null ? "" : log.getOperatorUsername());
                r.createCell(11).setCellValue(log.getOperatorRealName() == null ? "" : log.getOperatorRealName());
            }
            for (int i = 0; i < heads.length; i++) sheet.autoSizeColumn(i);
            wb.write(baos);
            String fileName = "authorization_scan_logs_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("导出扫码日志失败: " + e.getMessage(), e);
        }
    }
}
