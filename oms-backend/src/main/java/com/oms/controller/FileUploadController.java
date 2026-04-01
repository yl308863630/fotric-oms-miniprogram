package com.oms.controller;

import com.oms.config.ContractAccessPolicy;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.service.PurchaseOrderService;
import com.oms.repository.UserRepository;
import com.oms.service.SalesOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {
    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            String datePath = LocalDateTime.now().format(DATE_FORMATTER);
            
            Path basePath = Paths.get("").toAbsolutePath();
            String uploadPath = basePath + File.separator + uploadDir + File.separator + datePath;
            
            Path path = Paths.get(uploadPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            String newFilename = UUID.randomUUID().toString() + extension;
            String filePath = uploadPath + File.separator + newFilename;
            
            file.transferTo(new File(filePath));
            
            String fileUrl = "/uploads/" + datePath + "/" + newFilename;
            
            result.put("success", true);
            result.put("url", fileUrl);
            result.put("filename", originalFilename);
            result.put("message", "文件上传成功");
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /** 合同文件预览/下载：仅飞础科、上海热像科技可访问，链接走后端鉴权 */
    @GetMapping("/files/preview")
    public ResponseEntity<Resource> previewFile(@RequestParam String path) {
        if (path == null || !path.startsWith("/uploads/") || path.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        final User currentUser = (username != null && !"anonymousUser".equalsIgnoreCase(username))
                ? userRepository.findByUsername(username).orElse(null)
                : null;
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean allowed = ContractAccessPolicy.mayPreviewContractUploadByUser(currentUser)
                || salesOrderService.currentUserMayPreviewContractUploadPath(currentUser, path)
                || currentUserMayPreviewAuthorizationGeneratedFile(currentUser, path);
        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String relativePath = path.substring("/uploads/".length());
        Path basePath = Paths.get("").toAbsolutePath();
        Path filePath = basePath.resolve(uploadDir).resolve(relativePath).normalize();
        Path uploadsRoot = basePath.resolve(uploadDir).normalize();
        if (!filePath.startsWith(uploadsRoot) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        String filename = filePath.getFileName().toString();
        MediaType mediaType = resolvePreviewMediaType(filename);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping("/files/erp-entry-preview")
    public ResponseEntity<Resource> previewErpEntryFile(@RequestParam String targetType,
                                                        @RequestParam Long targetId,
                                                        @RequestParam(required = false) String screenshotUrl,
                                                        @RequestParam(defaultValue = "inline") String disposition) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        User currentUser = (username != null && !"anonymousUser".equalsIgnoreCase(username))
                ? userRepository.findByUsername(username).orElse(null)
                : null;
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String path = normalizeUploadPath(screenshotUrl);
        boolean allowed = false;
        String denyReason = "unknown";
        if ("sales".equalsIgnoreCase(targetType)) {
            SalesOrder order = salesOrderService.getAllOrders().stream()
                    .filter(item -> item.getId() != null && item.getId().equals(targetId))
                    .findFirst()
                    .orElse(null);
            if (order != null) {
                if (path == null) {
                    path = order.getErpEntryScreenshotUrl();
                }
                allowed = salesOrderService.canCurrentUserViewErpEntryScreenshot(order);
                denyReason = allowed ? "sales-order-allowed" : "sales-order-denied";
            } else {
                denyReason = "sales-order-not-found";
            }
            if (!allowed && path != null) {
                allowed = salesOrderService.currentUserMayPreviewErpEntryScreenshotPath(currentUser, path);
                denyReason = allowed ? "sales-path-allowed" : denyReason + "+sales-path-denied";
            }
        } else if ("purchase".equalsIgnoreCase(targetType)) {
            try {
                PurchaseOrder order = purchaseOrderService.getOrderById(targetId);
                if (order != null) {
                    if (path == null) {
                        path = order.getErpEntryScreenshotUrl();
                    }
                    allowed = purchaseOrderService.canCurrentUserViewErpEntryScreenshot(order);
                    denyReason = allowed ? "purchase-order-allowed" : "purchase-order-denied";
                } else {
                    denyReason = "purchase-order-not-found";
                }
                if (!allowed && path != null) {
                    allowed = purchaseOrderService.currentUserMayPreviewErpEntryScreenshotPath(currentUser, path);
                    denyReason = allowed ? "purchase-path-allowed" : denyReason + "+purchase-path-denied";
                }
            } catch (RuntimeException ex) {
                log.warn("ERP预览被拦截: user={}, company={}, targetType={}, targetId={}, rawPath={}, reason=purchase-order-forbidden-exception",
                        currentUser.getUsername(), currentUser.getCompanyTitle(), targetType, targetId, screenshotUrl);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            return ResponseEntity.badRequest().build();
        }

        path = normalizeUploadPath(path);
        if (!allowed || path == null || !path.startsWith("/uploads/") || path.contains("..")) {
            log.warn("ERP预览被拦截: user={}, company={}, targetType={}, targetId={}, rawPath={}, normalizedPath={}, allowed={}, reason={}",
                    currentUser.getUsername(),
                    currentUser.getCompanyTitle(),
                    targetType,
                    targetId,
                    screenshotUrl,
                    path,
                    allowed,
                    denyReason);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("ERP预览放行: user={}, company={}, targetType={}, targetId={}, normalizedPath={}, disposition={}, reason={}",
                currentUser.getUsername(),
                currentUser.getCompanyTitle(),
                targetType,
                targetId,
                path,
                disposition,
                denyReason);

        String relativePath = path.substring("/uploads/".length());
        Path basePath = Paths.get("").toAbsolutePath();
        Path filePath = basePath.resolve(uploadDir).resolve(relativePath).normalize();
        Path uploadsRoot = basePath.resolve(uploadDir).normalize();
        if (!filePath.startsWith(uploadsRoot) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        String filename = filePath.getFileName().toString();
        MediaType mediaType = resolvePreviewMediaType(filename);
        String contentDisposition = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition + "; filename=\"" + filename + "\"")
                .body(resource);
    }

    private String normalizeUploadPath(String path) {
        if (path == null) {
            return null;
        }
        String value = path.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(value);
                value = uri.getPath();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (value.startsWith("uploads/")) {
            value = "/" + value;
        }
        return value;
    }

    private boolean currentUserMayPreviewAuthorizationGeneratedFile(User user, String path) {
        if (user == null || path == null || path.isBlank()) return false;
        if ("ROLE_ADMIN".equalsIgnoreCase(user.getRole())) return true;
        if (!hasPermission(user, "authorization")) return false;
        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        // Authorization module generated artifacts are named with AUTH_ prefix.
        return fileName.startsWith("AUTH_");
    }

    private boolean hasPermission(User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) return false;
        String permissions = user.getPermissions();
        if (permissions == null || permissions.isBlank()) return false;
        for (String p : permissions.split(",")) {
            if (permission.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    private static MediaType resolvePreviewMediaType(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (lower.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        if (lower.endsWith(".doc")) {
            return MediaType.parseMediaType("application/msword");
        }
        if (lower.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
