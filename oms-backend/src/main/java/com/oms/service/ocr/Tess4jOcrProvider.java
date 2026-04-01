package com.oms.service.ocr;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Tesseract OCR 实现，需 ECS 安装 tesseract-ocr 及 chi_sim 语言包。
 */
public class Tess4jOcrProvider implements OcrProvider {

    @Override
    public String recognize(BufferedImage image) {
        if (!isAvailable()) return null;
        try {
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();
            String dataPath = resolveTessDataPath();
            if (dataPath != null) {
                tesseract.setDatapath(dataPath);
            }
            tesseract.setLanguage("chi_sim+eng");
            return tesseract.doOCR(image);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            net.sourceforge.tess4j.Tesseract t = new net.sourceforge.tess4j.Tesseract();
            String path = resolveTessDataPath();
            if (path != null) t.setDatapath(path);
            t.setLanguage("chi_sim+eng");
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static String resolveTessDataPath() {
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank()) {
            File f = new File(env.trim());
            if (f.exists()) return f.getAbsolutePath();
        }
        String[] candidates = {
                "C:\\Program Files\\Tesseract-OCR\\tessdata",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tessdata"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isDirectory()) return f.getAbsolutePath();
        }
        return null;
    }
}
