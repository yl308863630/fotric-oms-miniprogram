package com.oms.service;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfService {
    @Value("${upload.dir:uploads}")
    private String uploadDir;

    private Path resolveFilePath(String url) {
        if (url.startsWith("/uploads/")) {
            return Paths.get(uploadDir, url.substring("/uploads/".length()));
        }
        return Paths.get(uploadDir, url);
    }

    public String convertDocxToImage(String docxPath) {
        try {
            System.out.println("=== convertDocxToImage 开始 ===");
            
            Path docxFile = resolveFilePath(docxPath);
            System.out.println("docxPath: " + docxPath);
            System.out.println("docxFile: " + docxFile.toAbsolutePath());
            System.out.println("docxFile exists: " + Files.exists(docxFile));
            
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path targetDir = Paths.get(uploadDir, dateStr);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String baseFileName = docxFile.getFileName().toString().replace(".docx", "");
            Path tempPdfPath = targetDir.resolve(baseFileName + "_temp.pdf");

            System.out.println("1. 开始转换Word到PDF...");
            try (InputStream in = Files.newInputStream(docxFile);
                 XWPFDocument document = new XWPFDocument(in);
                 OutputStream out = Files.newOutputStream(tempPdfPath)) {
                PdfOptions options = PdfOptions.create();
                PdfConverter.getInstance().convert(document, out, options);
            }
            System.out.println("Word转PDF完成，文件大小: " + Files.size(tempPdfPath));

            System.out.println("2. 开始转换PDF到所有页图片...");
            String firstImageUrl = convertPdfToAllImages(tempPdfPath, targetDir, baseFileName);
            System.out.println("PDF转图片完成，第一张图片: " + firstImageUrl);

            System.out.println("3. 打包所有图片成ZIP...");
            String zipUrl = createZipFromImages(targetDir, baseFileName);
            System.out.println("ZIP打包完成: " + zipUrl);

            System.out.println("4. 删除临时PDF和原始Word文件...");
            Files.deleteIfExists(tempPdfPath);
            Files.deleteIfExists(docxFile);
            System.out.println("临时文件已删除");

            System.out.println("=== convertDocxToImage 完成: " + zipUrl + " ===");
            return zipUrl;
        } catch (Exception e) {
            System.err.println("=== 图片转换失败 ===");
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.err.println("====================");
            return null;
        }
    }

    private String convertPdfToAllImages(Path pdfPath, Path targetDir, String baseFileName) throws Exception {
        String firstImageUrl = null;
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
                
                String imageFileName = baseFileName + "_page" + (pageIndex + 1) + ".png";
                Path imagePath = targetDir.resolve(imageFileName);
                
                try (OutputStream out = Files.newOutputStream(imagePath)) {
                    ImageIO.write(image, "PNG", out);
                }
                
                System.out.println("  - 生成图片: " + imagePath);
                
                if (pageIndex == 0) {
                    firstImageUrl = "/uploads/" + targetDir.getFileName().toString() + "/" + imageFileName;
                }
            }
        }
        return firstImageUrl;
    }

    private String createZipFromImages(Path targetDir, String baseFileName) throws Exception {
        Path zipPath = targetDir.resolve(baseFileName + "_images.zip");
        String dateStr = targetDir.getFileName().toString();
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.list(targetDir)
                .filter(path -> path.getFileName().toString().startsWith(baseFileName + "_page") && path.getFileName().toString().endsWith(".png"))
                .forEach(path -> {
                    try {
                        ZipEntry entry = new ZipEntry(path.getFileName().toString());
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                        System.out.println("  - 添加到ZIP: " + path.getFileName());
                    } catch (Exception e) {
                        System.err.println("添加文件到ZIP失败: " + path.getFileName());
                        e.printStackTrace();
                    }
                });
        }
        
        return "/uploads/" + dateStr + "/" + baseFileName + "_images.zip";
    }

    public String convertDocxToProtectedPdf(String docxPath) {
        try {
            System.out.println("=== convertDocxToProtectedPdf 开始 ===");
            
            Path docxFile = resolveFilePath(docxPath);
            System.out.println("docxPath: " + docxPath);
            System.out.println("docxFile: " + docxFile.toAbsolutePath());
            System.out.println("docxFile exists: " + Files.exists(docxFile));
            
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path targetDir = Paths.get(uploadDir, dateStr);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String baseFileName = docxFile.getFileName().toString().replace(".docx", "");
            Path tempPdfPath = targetDir.resolve(baseFileName + "_temp.pdf");
            Path protectedPdfPath = targetDir.resolve(baseFileName + "_protected.pdf");

            System.out.println("1. 开始转换Word到PDF...");
            try (InputStream in = Files.newInputStream(docxFile);
                 XWPFDocument document = new XWPFDocument(in);
                 OutputStream out = Files.newOutputStream(tempPdfPath)) {
                PdfOptions options = PdfOptions.create();
                PdfConverter.getInstance().convert(document, out, options);
            }
            System.out.println("Word转PDF完成，文件大小: " + Files.size(tempPdfPath));

            System.out.println("2. 开始转换PDF到图片...");
            convertPdfToImages(tempPdfPath, targetDir, baseFileName);
            System.out.println("PDF转图片完成");

            System.out.println("3. 开始保护PDF...");
            PdfReader reader = null;
            try (OutputStream out = Files.newOutputStream(protectedPdfPath)) {
                reader = new PdfReader(tempPdfPath.toAbsolutePath().toString());
                PdfStamper stamper = new PdfStamper(reader, out);
                stamper.setEncryption(
                    null,
                    "contract123".getBytes(),
                    PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_SCREENREADERS,
                    PdfWriter.ENCRYPTION_AES_256
                );
                stamper.close();
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
            System.out.println("PDF保护完成，文件大小: " + Files.size(protectedPdfPath));

            System.out.println("4. 删除临时PDF文件...");
            Files.deleteIfExists(tempPdfPath);

            String resultUrl = "/uploads/" + dateStr + "/" + baseFileName + "_protected.pdf";
            System.out.println("=== convertDocxToProtectedPdf 完成: " + resultUrl + " ===");
            return resultUrl;
        } catch (Exception e) {
            System.err.println("=== PDF转换失败 ===");
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.err.println("====================");
            return null;
        }
    }

    private void convertPdfToImages(Path pdfPath, Path targetDir, String baseFileName) throws Exception {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
                
                String imageFileName = baseFileName + "_page" + (pageIndex + 1) + ".png";
                Path imagePath = targetDir.resolve(imageFileName);
                
                try (OutputStream out = Files.newOutputStream(imagePath)) {
                    ImageIO.write(image, "PNG", out);
                }
                
                System.out.println("  - 生成图片: " + imagePath);
            }
        }
    }
}
