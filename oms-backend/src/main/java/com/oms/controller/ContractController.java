package com.oms.controller;

import com.oms.entity.Contract;
import com.oms.service.ContractService;
import com.oms.service.PdfService;
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
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private SealService sealService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return contractService.getAllContracts(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contract> getById(@PathVariable Long id) {
        return contractService.getContractById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sales-order/{salesOrderId}")
    public ResponseEntity<Contract> getBySalesOrderId(@PathVariable Long salesOrderId) {
        return contractService.getContractBySalesOrderId(salesOrderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Contract save(@RequestBody Contract contract) {
        return contractService.saveContract(contract);
    }

    @PostMapping("/{id}/sign")
    public Contract signContract(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String partyType = request.get("partyType");
        return contractService.signContract(id, partyType);
    }

    @PostMapping("/generate-from-order/{salesOrderId}")
    public ResponseEntity<?> generateFromOrder(@PathVariable Long salesOrderId, @RequestBody(required = false) java.util.Map<String, String> request) {
        String templateUrl = request != null ? request.get("templateUrl") : null;
        String partyBRepresentative = request != null ? request.get("partyBRepresentative") : null;
        String platformName = request != null ? request.get("platformName") : null;
        String paymentMethod = request != null ? request.get("paymentMethod") : null;
        String deliveryParty = request != null ? request.get("deliveryParty") : null;
        try {
            Contract contract = contractService.createContractFromSalesOrder(salesOrderId, templateUrl, partyBRepresentative, platformName, paymentMethod, deliveryParty);
            return ResponseEntity.ok(contract);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "合同生成失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) msg = msg + " " + e.getCause().getMessage();
            return ResponseEntity.status(422).body(java.util.Map.of("message", msg));
        }
    }

    @PutMapping("/{id}")
    public Contract update(@PathVariable Long id, @RequestBody Contract contract) {
        contract.setId(id);
        return contractService.saveContract(contract);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadContract(@PathVariable Long id) {
        try {
            Contract contract = contractService.getContractById(id)
                    .orElseThrow(() -> new RuntimeException("合同不存在"));
            
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
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
