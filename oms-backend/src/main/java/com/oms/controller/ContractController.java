package com.oms.controller;

import com.oms.entity.Contract;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.ContractService;
import com.oms.service.DocumentService;
import com.oms.service.PdfService;
import com.oms.service.PrivacyAccessLogService;
import com.oms.service.SealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private SealService sealService;

    @Autowired
    private PrivacyAccessLogService privacyAccessLogService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserRepository userRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private User currentUserOrNull() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        return userRepository.findByUsername(name.trim()).orElse(null);
    }

    /** 测试电子章：浏览器打开 /api/contracts/seal-test 或 ?name=公司名 查看生成的印章图，便于排查合同章问题 */
    @GetMapping(value = "/seal-test")
    public ResponseEntity<?> testSeal(@RequestParam(required = false, defaultValue = "测试电子章") String name) {
        try {
            byte[] png = sealService.generateSeal(name);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(png.length);
            return ResponseEntity.ok().headers(headers).body(png);
        } catch (Exception e) {
            String msg = "电子章生成失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body(msg);
        }
    }

    @GetMapping
    public Page<Contract> list(
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return contractService.getAllContracts(contractNo, status, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contract> getById(@PathVariable Long id) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> opt = contractService.getContractById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserViewContract(u, opt.get())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(opt.get());
    }

    /** 合同占位符跟踪表：返回该合同生成时各占位符对应的值、代码字段、说明，便于核查占位符替换是否正确。{id} 为合同主键（数字） */
    @GetMapping("/{id}/placeholder-report")
    public ResponseEntity<?> getPlaceholderReport(@PathVariable Long id) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> opt = contractService.getContractById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserViewContract(u, opt.get())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(documentService.buildPlaceholderReport(opt.get()));
    }

    /** 按合同编号获取占位符跟踪表，便于用合同编号（如 HT202603150005）直接查，无需先查主键 id */
    @GetMapping("/by-no/{contractNo}/placeholder-report")
    public ResponseEntity<?> getPlaceholderReportByContractNo(@PathVariable String contractNo) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> opt = contractService.getContractByContractNo(contractNo);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserViewContract(u, opt.get())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(documentService.buildPlaceholderReport(opt.get()));
    }

    @GetMapping("/sales-order/{salesOrderId}")
    public ResponseEntity<Contract> getBySalesOrderId(@PathVariable Long salesOrderId) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> opt = contractService.getContractBySalesOrderId(salesOrderId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserViewContract(u, opt.get())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Contract contract) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        if (contract.getId() != null) {
            Optional<Contract> ex = contractService.getContractById(contract.getId());
            if (ex.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            if (!contractService.canCurrentUserAccessContract(u, ex.get())) {
                return ResponseEntity.status(403).build();
            }
        } else {
            Long sid = contract.getSalesOrderId();
            if (sid == null) {
                return ResponseEntity.badRequest().build();
            }
            if (!contractService.canCurrentUserMutateContractForSalesOrder(u, sid)) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(contractService.saveContract(contract));
    }

    @PostMapping("/{id}/sign")
    public Contract signContract(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String partyType = request.get("partyType");
        return contractService.signContract(id, partyType);
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<?> regenerateContract(@PathVariable Long id) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> ex = contractService.getContractById(id);
        if (ex.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserAccessContract(u, ex.get())) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(contractService.regenerateContract(id));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "合同重生成失败";
            return ResponseEntity.status(422).body(java.util.Map.of("message", msg));
        }
    }

    @PostMapping("/generate-from-order/{salesOrderId}")
    public ResponseEntity<?> generateFromOrder(@PathVariable Long salesOrderId, @RequestBody(required = false) java.util.Map<String, Object> request) {
        String templateUrl = request != null && request.get("templateUrl") != null ? request.get("templateUrl").toString() : null;
        String partyBRepresentative = request != null && request.get("partyBRepresentative") != null ? request.get("partyBRepresentative").toString() : null;
        String platformName = request != null && request.get("platformName") != null ? request.get("platformName").toString() : null;
        String paymentMethod = request != null && request.get("paymentMethod") != null ? request.get("paymentMethod").toString() : null;
        String deliveryParty = request != null && request.get("deliveryParty") != null ? request.get("deliveryParty").toString() : null;
        String contractNo = request != null && request.get("contractNo") != null ? request.get("contractNo").toString() : null;
        String purchaseOrderNo = request != null && request.get("purchaseOrderNo") != null ? request.get("purchaseOrderNo").toString() : null;
        java.math.BigDecimal deliveryPartyPurchasePrice = null;
        if (request != null && request.get("deliveryPartyPurchasePrice") != null && !request.get("deliveryPartyPurchasePrice").toString().trim().isEmpty()) {
            try {
                deliveryPartyPurchasePrice = new java.math.BigDecimal(request.get("deliveryPartyPurchasePrice").toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        try {
            Contract contract = contractService.createContractFromSalesOrder(
                    salesOrderId,
                    templateUrl,
                    partyBRepresentative,
                    platformName,
                    paymentMethod,
                    deliveryParty,
                    deliveryPartyPurchasePrice,
                    contractNo,
                    purchaseOrderNo
            );
            return ResponseEntity.ok(contract);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "合同生成失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) msg = msg + " " + e.getCause().getMessage();
            return ResponseEntity.status(422).body(java.util.Map.of("message", msg));
        }
    }

    @PostMapping("/generate-from-master-selection")
    public ResponseEntity<?> generateFromMasterSelection(@RequestBody(required = false) Map<String, Object> request) {
        List<Long> salesOrderIds = toLongList(request != null ? request.get("salesOrderIds") : null);
        String templateUrl = request != null && request.get("templateUrl") != null ? String.valueOf(request.get("templateUrl")) : null;
        String partyBRepresentative = request != null && request.get("partyBRepresentative") != null ? String.valueOf(request.get("partyBRepresentative")) : null;
        String platformName = request != null && request.get("platformName") != null ? String.valueOf(request.get("platformName")) : null;
        String paymentMethod = request != null && request.get("paymentMethod") != null ? String.valueOf(request.get("paymentMethod")) : null;
        String deliveryParty = request != null && request.get("deliveryParty") != null ? String.valueOf(request.get("deliveryParty")) : null;
        BigDecimal deliveryPartyPurchasePrice = toBigDecimal(request != null ? request.get("deliveryPartyPurchasePrice") : null);
        Map<Long, BigDecimal> deliveryPartyPurchasePriceByOrderId = toBigDecimalMapByLongKey(request != null ? request.get("deliveryPartyPurchasePriceByOrderId") : null);
        BigDecimal deductionRate = toBigDecimal(request != null ? request.get("deductionRate") : null);
        String offlineSales = request != null && request.get("offlineSales") != null ? String.valueOf(request.get("offlineSales")) : null;
        String contractNo = request != null && request.get("contractNo") != null ? String.valueOf(request.get("contractNo")) : null;
        String purchaseOrderNo = request != null && request.get("purchaseOrderNo") != null ? String.valueOf(request.get("purchaseOrderNo")) : null;
        try {
            Contract contract = contractService.createContractFromMasterSelection(
                    salesOrderIds,
                    templateUrl,
                    partyBRepresentative,
                    platformName,
                    paymentMethod,
                    deliveryParty,
                    deliveryPartyPurchasePrice,
                    deliveryPartyPurchasePriceByOrderId,
                    deductionRate,
                    offlineSales,
                    contractNo,
                    purchaseOrderNo
            );
            return ResponseEntity.ok(contract);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "合并指派生成合同失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) msg = msg + " " + e.getCause().getMessage();
            System.err.println("generate-from-master-selection failed: " + msg);
            return ResponseEntity.status(422).body(java.util.Map.of("message", msg));
        }
    }

    @PostMapping("/number-preview")
    public ResponseEntity<?> previewNumbers(@RequestBody(required = false) Map<String, Object> request) {
        List<Long> salesOrderIds = toLongList(request != null ? request.get("salesOrderIds") : null);
        Long salesOrderId = salesOrderIds.isEmpty() ? toLong(request != null ? request.get("salesOrderId") : null) : null;
        String deliveryParty = request != null && request.get("deliveryParty") != null ? String.valueOf(request.get("deliveryParty")) : null;
        String partyBRepresentative = request != null && request.get("partyBRepresentative") != null ? String.valueOf(request.get("partyBRepresentative")) : null;
        try {
            if (!salesOrderIds.isEmpty()) {
                return ResponseEntity.ok(contractService.previewNumbersForMasterSelection(salesOrderIds, deliveryParty, partyBRepresentative));
            }
            if (salesOrderId == null) {
                return ResponseEntity.status(422).body(Map.of("message", "请传入 salesOrderId 或 salesOrderIds"));
            }
            return ResponseEntity.ok(contractService.previewNumbersForSalesOrder(salesOrderId, deliveryParty, partyBRepresentative));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "获取默认编号失败";
            return ResponseEntity.status(422).body(Map.of("message", msg));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Contract contract) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> ex = contractService.getContractById(id);
        if (ex.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserAccessContract(u, ex.get())) {
            return ResponseEntity.status(403).build();
        }
        contract.setId(id);
        return ResponseEntity.ok(contractService.saveContract(contract));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User u = currentUserOrNull();
        if (u == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Contract> ex = contractService.getContractById(id);
        if (ex.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.canCurrentUserAccessContract(u, ex.get())) {
            return ResponseEntity.status(403).build();
        }
        contractService.deleteContract(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadContract(@PathVariable Long id, HttpServletRequest request) {
        try {
            User u = currentUserOrNull();
            if (u == null) {
                return ResponseEntity.status(401).build();
            }
            Contract contract = contractService.getContractById(id).orElse(null);
            if (contract == null) {
                return ResponseEntity.notFound().build();
            }
            if (!contractService.canCurrentUserViewContract(u, contract)) {
                return ResponseEntity.status(403).build();
            }

            System.out.println("=== Contract " + id + " download ===");
            System.out.println("protectedPdfUrl: " + contract.getProtectedPdfUrl());
            System.out.println("generatedUrl: " + contract.getGeneratedUrl());
            System.out.println("partyASigned: " + contract.getPartyASigned());
            System.out.println("partyBSigned: " + contract.getPartyBSigned());
            System.out.println("status: " + contract.getStatus());
            
            String fileUrl = null;
            String fileName = null;
            String protectedImageUrl = null;
            
            if (contract.getProtectedImageUrl() == null || contract.getProtectedImageUrl().isEmpty()) {
                if ((Boolean.TRUE.equals(contract.getPartyASigned()) || Boolean.TRUE.equals(contract.getPartyBSigned())) && 
                    contract.getGeneratedUrl() != null && !contract.getGeneratedUrl().isEmpty()) {
                    System.out.println("Generating Image on download...");
                    protectedImageUrl = pdfService.convertDocxToImage(contract.getGeneratedUrl());
                    System.out.println("Generated protectedImageUrl: " + protectedImageUrl);
                    if (protectedImageUrl != null) {
                        contract.setProtectedImageUrl(protectedImageUrl);
                        contract.setProtectedPdfUrl(null);
                        contract.setGeneratedUrl(null);
                        contractService.saveContract(contract);
                        System.out.println("Contract saved with protectedImageUrl: " + contract.getProtectedImageUrl());
                    }
                }
            } else {
                protectedImageUrl = contract.getProtectedImageUrl();
            }
            
            if (protectedImageUrl != null && !protectedImageUrl.isEmpty()) {
                System.out.println("Using protected Image URL: " + protectedImageUrl);
                fileUrl = protectedImageUrl;
                if (protectedImageUrl.endsWith(".zip")) {
                    fileName = contract.getContractNo() + "_images.zip";
                } else {
                    fileName = contract.getContractNo() + ".png";
                }
            } else if (contract.getProtectedPdfUrl() != null && !contract.getProtectedPdfUrl().isEmpty()) {
                System.out.println("Using protected PDF URL: " + contract.getProtectedPdfUrl());
                fileUrl = contract.getProtectedPdfUrl();
                fileName = contract.getContractNo() + "_protected.pdf";
            } else {
                System.out.println("Using generated Word URL: " + contract.getGeneratedUrl());
                fileUrl = contract.getGeneratedUrl();
                fileName = contract.getContractNo() + ".docx";
            }
            
            if (fileUrl == null || fileUrl.isEmpty()) {
                System.out.println("Contract " + id + " has no file URL");
                return ResponseEntity.notFound().build();
            }

            System.out.println("Upload dir: " + uploadDir);
            System.out.println("File URL: " + fileUrl);

            Path filePath;
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                try {
                    java.net.URI uri = java.net.URI.create(fileUrl);
                    fileUrl = uri.getPath();
                } catch (Exception ignored) {
                }
            }

            if (fileUrl.startsWith("/uploads/")) {
                String relativePath = fileUrl.substring("/uploads/".length());
                System.out.println("Relative path: " + relativePath);
                filePath = Paths.get(uploadDir, relativePath);
            } else {
                filePath = Paths.get(uploadDir, fileUrl);
            }

            System.out.println("File path: " + filePath.toAbsolutePath());
            System.out.println("File exists: " + Files.exists(filePath));

            if (!Files.exists(filePath)) {
                System.out.println("File not found: " + filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            
            HttpHeaders headers = new HttpHeaders();
            String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            if (fileName.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else if (fileName.endsWith(".pdf")) {
                contentType = MediaType.APPLICATION_PDF_VALUE;
            } else if (fileName.endsWith(".zip")) {
                contentType = "application/zip";
            }
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", fileName);

            String username = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
            if (username != null && !"anonymousUser".equals(username)) {
                User user = userRepository.findByUsername(username).orElse(null);
                String operatorName = user != null && user.getRealName() != null && !user.getRealName().isBlank()
                        ? user.getRealName() : username;
                String clientIp = request.getHeader("X-Forwarded-For");
                if (clientIp == null || clientIp.isBlank()) clientIp = request.getRemoteAddr();
                if (clientIp != null && clientIp.contains(",")) clientIp = clientIp.split(",")[0].trim();
                privacyAccessLogService.log(user != null ? user.getId() : null, operatorName, clientIp,
                        "DOWNLOAD", "CONTRACT", String.valueOf(id), "合同下载");
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<Long> toLongList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long parsed = toLong(item);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result;
        }
        Long single = toLong(value);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<Long, BigDecimal> toBigDecimalMapByLongKey(Object value) {
        Map<Long, BigDecimal> result = new java.util.LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> rawMap)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Long key = toLong(entry.getKey());
            BigDecimal amount = toBigDecimal(entry.getValue());
            if (key != null && amount != null) {
                result.put(key, amount);
            }
        }
        return result;
    }
}
