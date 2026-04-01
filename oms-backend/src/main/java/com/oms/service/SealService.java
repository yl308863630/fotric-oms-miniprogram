package com.oms.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

@Service
public class SealService {

    private static final int CANVAS_SIZE = 1000;
    private static final int STAMP_DIAMETER = 800;
    private static final int STAR_SIZE = 120;
    private static final int BORDER_WIDTH = 12;
    private static final Color RED_COLOR = new Color(200, 30, 30);
    private static final Random RANDOM = new Random();

    /** 优先使用宋体；若系统无则依次尝试 SimSun、微软雅黑等，避免电子章因字体缺失报错或乱码 */
    private static Font getSealFont() {
        String[] preferred = { "宋体", "SimSun", "Microsoft YaHei", "微软雅黑", "SimHei", "黑体" };
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String name : preferred) {
            if (Arrays.asList(available).contains(name)) {
                return new Font(name, Font.PLAIN, 120);
            }
        }
        return new Font(Font.SERIF, Font.PLAIN, 120);
    }

    public byte[] generateSeal(String companyName) throws IOException {
        BufferedImage stampImage = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D stampG2d = stampImage.createGraphics();
        stampG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        stampG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        stampG2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        stampG2d.setColor(RED_COLOR);
        drawPerfectCircle(stampG2d);
        drawOuterText(stampG2d, companyName);
        drawStar(stampG2d);
        stampG2d.dispose();

        stampImage = applyStampEffect(stampImage);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(stampImage, "PNG", baos);
        return baos.toByteArray();
    }

    public File generateSealToFile(String companyName, String filePath) throws IOException {
        byte[] sealBytes = generateSeal(companyName);
        File file = new File(filePath);
        java.nio.file.Files.write(file.toPath(), sealBytes);
        return file;
    }

    private void drawPaperBackground(Graphics2D g2d, int width, int height) {
        Color paperColor = new Color(255, 253, 245);
        g2d.setColor(paperColor);
        g2d.fillRect(0, 0, width, height);
    }

    private BufferedImage applyPaperTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;

                double grain = 0.95 + RANDOM.nextDouble() * 0.1;
                double yellowTint = 0.97 + RANDOM.nextDouble() * 0.03;

                int newR = (int) Math.min(255, r * grain);
                int newG = (int) Math.min(255, g * grain * yellowTint);
                int newB = (int) Math.min(255, b * grain * (yellowTint - 0.02));

                int newArgb = (a << 24) | (newR << 16) | (newG << 8) | newB;
                result.setRGB(x, y, newArgb);
            }
        }

        return result;
    }

    private void drawStampShadow(Graphics2D g2d, BufferedImage stampImage) {
        int shadowOffset = 8;
        BufferedImage shadow = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D shadowG2d = shadow.createGraphics();

        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                int argb = stampImage.getRGB(x, y);
                int alpha = (argb >> 24) & 0xff;
                if (alpha > 0) {
                    int shadowAlpha = (int) (alpha * 0.15);
                    int shadowArgb = (shadowAlpha << 24) | (50 << 16) | (50 << 8) | 50;
                    shadow.setRGB(x + shadowOffset, y + shadowOffset, shadowArgb);
                }
            }
        }

        shadowG2d.dispose();
        g2d.drawImage(shadow, 0, 0, null);
    }

    private void drawPerfectCircle(Graphics2D g2d) {
        int centerX = CANVAS_SIZE / 2;
        int centerY = CANVAS_SIZE / 2;
        int radius = STAMP_DIAMETER / 2;

        g2d.setStroke(new BasicStroke(BORDER_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }

    private void drawImperfectCircle(Graphics2D g2d) {
        int centerX = CANVAS_SIZE / 2;
        int centerY = CANVAS_SIZE / 2;
        int baseRadius = STAMP_DIAMETER / 2;

        int points = 200;
        int[] xPoints = new int[points];
        int[] yPoints = new int[points];

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double variation = 0.97 + RANDOM.nextDouble() * 0.06;
            double radius = baseRadius * variation;
            xPoints[i] = centerX + (int) (radius * Math.cos(angle));
            yPoints[i] = centerY + (int) (radius * Math.sin(angle));
        }

        g2d.setStroke(new BasicStroke(BORDER_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawPolygon(xPoints, yPoints, points);
    }

    private void drawOuterText(Graphics2D g2d, String companyName) {
        int centerX = CANVAS_SIZE / 2;
        int centerY = CANVAS_SIZE / 2;
        int radius = 280;

        Font font = getSealFont();
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics(font);

        int textLength = companyName.length();
        double angleStep = Math.toRadians(320.0 / (textLength + 1));
        double startAngle = Math.toRadians(250);

        for (int i = 0; i < textLength; i++) {
            String charStr = companyName.substring(i, i + 1);
            double angle = startAngle + angleStep * i + angleStep / 2;

            AffineTransform transform = new AffineTransform();
            transform.translate(centerX, centerY);
            transform.rotate(angle);

            int charWidth = fm.charWidth(charStr.charAt(0));
            transform.translate(-charWidth / 2, -radius);

            g2d.setTransform(transform);
            g2d.drawString(charStr, 0, 0);
        }

        g2d.setTransform(new AffineTransform());
    }

    private void drawStar(Graphics2D g2d) {
        int centerX = CANVAS_SIZE / 2;
        int centerY = CANVAS_SIZE / 2;

        int[] xPoints = new int[10];
        int[] yPoints = new int[10];

        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(i * 36 - 90);
            int radius = i % 2 == 0 ? STAR_SIZE / 2 : STAR_SIZE / 4;
            xPoints[i] = centerX + (int) (radius * Math.cos(angle));
            yPoints[i] = centerY + (int) (radius * Math.sin(angle));
        }

        Polygon star = new Polygon(xPoints, yPoints, 10);
        g2d.fill(star);
    }

    private BufferedImage applyStampEffect(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xff;

                if (alpha > 0) {
                    int r = (argb >> 16) & 0xff;
                    int g = (argb >> 8) & 0xff;
                    int b = argb & 0xff;

                    double dist = distanceFromCenter(x, y, width, height);
                    double edgeFactor = getEdgeFactor(dist, width, height);
                    double gapFactor = getGapFactor(x, y, width, height);
                    double grain = RANDOM.nextDouble() * 0.4 + 0.7;
                    double bleedFactor = getBleedFactor(x, y, image);

                    int newAlpha = (int) (alpha * edgeFactor * gapFactor * grain);

                    if (newAlpha > 0 && RANDOM.nextDouble() > 0.08) {
                        int newR = (int) (r * (0.5 + RANDOM.nextDouble() * 0.7) * bleedFactor);
                        int newG = (int) (g * (0.2 + RANDOM.nextDouble() * 0.5) * bleedFactor);
                        int newB = (int) (b * (0.15 + RANDOM.nextDouble() * 0.45) * bleedFactor);

                        int newArgb = (Math.min(255, newAlpha) << 24) |
                                     (Math.min(255, newR) << 16) |
                                     (Math.min(255, newG) << 8) |
                                     Math.min(255, newB);
                        result.setRGB(x, y, newArgb);
                    }
                }
            }
        }

        return result;
    }

    private double getBleedFactor(int x, int y, BufferedImage image) {
        int count = 0;
        int total = 0;
        int radius = 2;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < image.getWidth() && ny >= 0 && ny < image.getHeight()) {
                    int argb = image.getRGB(nx, ny);
                    if ((argb >> 24 & 0xff) > 0) {
                        count++;
                    }
                    total++;
                }
            }
        }

        return 0.8 + (count * 1.0 / total) * 0.4;
    }

    private double getGapFactor(int x, int y, int width, int height) {
        double centerX = width / 2.0;
        double centerY = height / 2.0;

        double angle = Math.atan2(y - centerY, x - centerX);
        double dist = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
        double normalizedDist = dist / (width / 2.0);

        double gapStrength = 0.0;
        for (int i = 0; i < 12; i++) {
            double gapAngle = Math.toRadians(i * 30);
            double angleDiff = Math.abs(angle - gapAngle);
            if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;

            double gapSize = 0.12 + RANDOM.nextDouble() * 0.15;
            if (angleDiff < gapSize) {
                double t = angleDiff / gapSize;
                double factor = 0.05 + 0.95 * t * t;
                if (factor > gapStrength) {
                    gapStrength = factor;
                }
            }
        }

        double randomGap = RANDOM.nextDouble();
        if (randomGap < 0.12 && normalizedDist > 0.55) {
            return 0.0;
        }

        return Math.max(gapStrength, 0.25 + RANDOM.nextDouble() * 0.75);
    }

    private double distanceFromCenter(int x, int y, int width, int height) {
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double maxDist = Math.sqrt(centerX * centerX + centerY * centerY);
        double dist = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
        return dist / maxDist;
    }

    private double getEdgeFactor(double dist, int width, int height) {
        double radius = 0.88;
        if (dist < radius - 0.08) {
            return 1.0;
        } else if (dist > radius + 0.08) {
            return 0.0;
        } else {
            double t = (dist - (radius - 0.08)) / 0.16;
            return 1.0 - t * t * (3 - 2 * t);
        }
    }
}
