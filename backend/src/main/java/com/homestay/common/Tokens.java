package com.homestay.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Sinh chuỗi ngẫu nhiên an toàn và băm SHA-256 để lưu token (không lưu token gốc). */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final String UPPER_ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Tokens() {}

    public static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mật khẩu tạm: đủ chữ và số, không dùng ký tự dễ nhầm (0/O, 1/l). */
    public static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            sb.setLength(0);
            for (int i = 0; i < 10; i++) {
                sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
            }
            String s = sb.toString();
            if (s.chars().anyMatch(Character::isDigit) && s.chars().anyMatch(Character::isLetter)) {
                return s;
            }
        }
    }

    public static String bookingCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(UPPER_ALNUM.charAt(RANDOM.nextInt(UPPER_ALNUM.length())));
        }
        return sb.toString();
    }
}
