package com.oms.controller;

import com.oms.service.BarcodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/barcode")
public class BarcodeController {

    @Autowired
    private BarcodeService barcodeService;

    @GetMapping("/code128")
    public ResponseEntity<byte[]> generateBarcode128(
            @RequestParam String text,
            @RequestParam(defaultValue = "400") int width,
            @RequestParam(defaultValue = "100") int height) {
        try {
            byte[] imageBytes = barcodeService.generateBarcode128(text, width, height);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imageBytes.length);
            headers.set("Content-Disposition", "inline; filename=barcode.png");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/code128/with-text")
    public ResponseEntity<byte[]> generateBarcode128WithText(
            @RequestParam String text,
            @RequestParam(defaultValue = "400") int width,
            @RequestParam(defaultValue = "130") int height,
            @RequestParam(defaultValue = "") String labelText) {
        try {
            byte[] imageBytes = barcodeService.generateBarcode128WithText(text, width, height, labelText);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imageBytes.length);
            headers.set("Content-Disposition", "inline; filename=barcode.png");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}