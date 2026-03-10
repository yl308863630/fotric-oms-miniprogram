package com.oms.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

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
}
