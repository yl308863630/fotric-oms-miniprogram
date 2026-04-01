package com.oms.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class BarcodeService {

    public byte[] generateBarcode128(String text, int width, int height) throws IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix bitMatrix = new com.google.zxing.oned.Code128Writer().encode(
                text, BarcodeFormat.CODE_128, width, height, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IOException("生成条形码失败: " + e.getMessage(), e);
        }
    }

    public byte[] generateBarcode128WithText(String text, int width, int height, String labelText) throws IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix bitMatrix = new com.google.zxing.oned.Code128Writer().encode(
                text, BarcodeFormat.CODE_128, width, height - 30, hints);

            BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = finalImage.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);

            graphics.drawImage(barcodeImage, 0, 5, null);

            if (labelText != null && !labelText.isEmpty()) {
                graphics.setColor(Color.BLACK);
                graphics.setFont(new Font("Arial", Font.PLAIN, 14));
                FontMetrics fontMetrics = graphics.getFontMetrics();
                int textWidth = fontMetrics.stringWidth(labelText);
                int x = (width - textWidth) / 2;
                graphics.drawString(labelText, x, height - 5);
            }

            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(finalImage, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IOException("生成条形码失败: " + e.getMessage(), e);
        }
    }
}