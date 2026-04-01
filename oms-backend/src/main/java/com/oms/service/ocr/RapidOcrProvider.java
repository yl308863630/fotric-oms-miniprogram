package com.oms.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * RapidOCR (PaddleOCR) 实现，模型内置 JAR，无需外部安装。
 */
public class RapidOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(RapidOcrProvider.class);

    private volatile Boolean available;
    private static final Object LOCK = new Object();

    @Override
    public String recognize(BufferedImage image) {
        if (!isAvailable()) return null;
        try {
            io.github.mymonstercat.ocr.InferenceEngine engine =
                    io.github.mymonstercat.ocr.InferenceEngine.getInstance(io.github.mymonstercat.Model.ONNX_PPOCR_V4);
            String path = writeToTempFile(image);
            if (path == null) return null;
            try {
                com.benjaminwan.ocrlibrary.OcrResult result = engine.runOcr(path);
                return result != null ? result.getStrRes().trim() : null;
            } finally {
                new java.io.File(path).delete();
            }
        } catch (Exception e) {
            log.warn("RapidOCR 识别失败: {}", e.getMessage());
            return null;
        }
    }

    private static String writeToTempFile(BufferedImage image) {
        try {
            java.io.File tmp = java.io.File.createTempFile("ocr_", ".png");
            javax.imageio.ImageIO.write(image, "png", tmp);
            return tmp.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        if (available != null) return available;
        synchronized (LOCK) {
            if (available != null) return available;
            try {
                io.github.mymonstercat.ocr.InferenceEngine.getInstance(io.github.mymonstercat.Model.ONNX_PPOCR_V4);
                available = true;
                log.info("RapidOCR (PP-OCRv4) 已加载");
            } catch (Throwable t) {
                available = false;
                log.info("RapidOCR 不可用，将回退 Tesseract: {}", t.getMessage());
            }
        }
        return available;
    }
}
