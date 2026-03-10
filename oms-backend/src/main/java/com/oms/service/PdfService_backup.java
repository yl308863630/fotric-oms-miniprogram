package com.oms.service;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PdfService_backup {
    @Value("${upload.dir:uploads}")
    private String uploadDir;

    private Path resolveFilePath(String url) {
        if (url.startsWith("/uploads/")) {
            return Paths.get(uploadDir, url.substring("/uploads/".length()));
        }
        return Paths.get(uploadDir, url);
    }

    public String convertDocxToProtectedPdf(String docxPath) {
        try {
            System.out.println("=== convertDocxToProtectedPdf started ===");
            System.out.println("docxPath: " + docxPath);
            
            Path docxFile = resolveFilePath(docxPath);
            System.out.println("Resolved docxFile: " + docxFile.toAbsolutePath());
            System.out.println("docxFile exists: " + Files.exists(docxFile));
            
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path targetDir = Paths.get(uploadDir, dateStr);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            System.out.println("targetDir: " + targetDir.toAbsolutePath());

            String fileName = docxFile.getFileName().toString().replace(".docx", "_protected.pdf");
            Path tempPdfPath = targetDir.resolve(docxFile.getFileName().toString().replace(".docx", "_temp.pdf"));
            Path protectedPdfPath = targetDir.resolve(fileName);
            System.out.println("tempPdfPath: " + tempPdfPath.toAbsolutePath());
            System.out.println("protectedPdfPath: " + protectedPdfPath.toAbsolutePath());

            System.out.println("Starting convertDocxToPdf...");
            convertDocxToPdf(docxFile, tempPdfPath);
            System.out.println("convertDocxToPdf completed, tempPdfPath exists: " + Files.exists(tempPdfPath));
            
            System.out.println("Starting protectPdf...");
            protectPdf(tempPdfPath, protectedPdfPath);
            System.out.println("protectPdf completed, protectedPdfPath exists: " + Files.exists(protectedPdfPath));
            
            Files.deleteIfExists(tempPdfPath);
            System.out.println("tempPdfPath deleted");

            String resultUrl = "/uploads/" + dateStr + "/" + fileName;
            System.out.println("=== convertDocxToProtectedPdf completed, result: " + resultUrl + " ===");
            return resultUrl;
        } catch (Exception e) {
            System.err.println("=== PDF转换失败 ===");
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("====================");
            return null;
        }
    }

    private void convertDocxToPdf(Path docxPath, Path pdfPath) throws Exception {
        try (InputStream in = Files.newInputStream(docxPath);
             XWPFDocument document = new XWPFDocument(in);
             OutputStream out = Files.newOutputStream(pdfPath)) {
            
            PdfOptions options = PdfOptions.create();
            PdfConverter.getInstance().convert(document, out, options);
        }
    }

    private void protectPdf(Path inputPdfPath, Path outputPdfPath) throws Exception {
        PdfReader reader = new PdfReader(inputPdfPath.toAbsolutePath().toString());
        try (OutputStream out = Files.newOutputStream(outputPdfPath)) {
            PdfStamper stamper = new PdfStamper(reader, out);
            
            stamper.setEncryption(
                null,
                "contract_secure_2026".getBytes(),
                PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_SCREENREADERS,
                PdfWriter.ENCRYPTION_AES_256 | PdfWriter.DO_NOT_ENCRYPT_METADATA
            );
            
            stamper.setFullCompression();
            stamper.close();
            reader.close();
        }
    }
}
