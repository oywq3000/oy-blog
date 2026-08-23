package com.oyproj.utils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 图形验证码工具：生成 4 位字符验证码 + PNG 图片（纯 JDK 实现，无第三方依赖）
 */
public final class CaptchaUtils {

    /** 验证码字符集：去掉 0/O/1/I/l 等易混淆字符 */
    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    /** 验证码长度 */
    private static final int CODE_LENGTH = 4;
    /** 图片尺寸 */
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;

    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaUtils() {
    }

    /**
     * 验证码结果：明文答案 + 图片 data URI
     */
    public record Captcha(String code, String base64Image) {
    }

    /**
     * 生成一张图形验证码
     */
    public static Captcha gen() {
        String code = genCode();
        return new Captcha(code, genBase64Image(code));
    }

    /**
     * 生成 4 位随机验证码
     */
    public static String genCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 按给定内容绘制验证码图片（浅色背景 + 干扰线 + 逐字符随机颜色/轻微旋转），
     * 返回 PNG 的 base64 data URI
     */
    public static String genBase64Image(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // 浅色背景
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            // 干扰线
            for (int i = 0; i < 5; i++) {
                g.setColor(new Color(170 + RANDOM.nextInt(70), 170 + RANDOM.nextInt(70), 170 + RANDOM.nextInt(70)));
                g.drawLine(RANDOM.nextInt(IMAGE_WIDTH), RANDOM.nextInt(IMAGE_HEIGHT),
                        RANDOM.nextInt(IMAGE_WIDTH), RANDOM.nextInt(IMAGE_HEIGHT));
            }
            // 逐字符绘制：随机深色 + 轻微旋转（Font.DIALOG 保证服务器无字体文件也能绘制）
            int step = IMAGE_WIDTH / (CODE_LENGTH + 1);
            for (int i = 0; i < code.length(); i++) {
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 26 + RANDOM.nextInt(6)));
                g.setColor(new Color(20 + RANDOM.nextInt(110), 20 + RANDOM.nextInt(110), 20 + RANDOM.nextInt(110)));
                int x = step * (i + 1);
                int y = 29;
                double angle = (RANDOM.nextDouble() - 0.5) * 0.5; // 约 ±0.25 弧度
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x, y);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("验证码图片生成失败", e);
        }
    }
}
