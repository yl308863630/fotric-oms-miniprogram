package com.oms.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 图形验证码：生成随机字符图片，内存缓存 key -> 答案，校验后移除。
 */
@Service
public class CaptchaService {

    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LEN = 4;
    private static final long EXPIRE_MS = 2 * 60 * 1000;

    private final Map<String, CaptchaEntry> cache = new ConcurrentHashMap<>();

    public static class CaptchaResult {
        public final String captchaKey;
        public final String image; // data:image/png;base64,...

        public CaptchaResult(String captchaKey, String image) {
            this.captchaKey = captchaKey;
            this.image = image;
        }
    }

    private static class CaptchaEntry {
        final String answer;
        final long expiresAt;

        CaptchaEntry(String answer, long expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }

    public CaptchaResult generate() {
        cleanupExpired();
        String code = randomCode();
        BufferedImage image = drawImage(code);
        String base64 = toBase64(image);
        String key = UUID.randomUUID().toString();
        cache.put(key, new CaptchaEntry(code, System.currentTimeMillis() + EXPIRE_MS));
        return new CaptchaResult(key, "data:image/png;base64," + base64);
    }

    /** 校验用户输入，忽略大小写与首尾空格；校验通过后移除 key */
    public boolean validate(String captchaKey, String userInput) {
        if (captchaKey == null || captchaKey.isBlank()) return false;
        CaptchaEntry entry = cache.remove(captchaKey);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt) return false;
        String normalized = userInput != null ? userInput.trim() : "";
        return entry.answer.equalsIgnoreCase(normalized);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        return sb.toString();
    }

    private BufferedImage drawImage(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        // 干扰线
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 2; i++) {
            g.drawLine((int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT),
                    (int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT));
        }
        // 噪点
        for (int i = 0; i < 20; i++) {
            g.fillRect((int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT), 1, 1);
        }
        // 文字
        g.setColor(new Color(50, 50, 50));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        int x = 12;
        for (int i = 0; i < code.length(); i++) {
            String ch = String.valueOf(code.charAt(i));
            int y = HEIGHT / 2 + fm.getAscent() / 2 - 4;
            g.drawString(ch, x, y);
            x += WIDTH / (CODE_LEN + 1);
        }
        g.dispose();
        return img;
    }

    private String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Captcha image encode failed", e);
        }
    }
}
