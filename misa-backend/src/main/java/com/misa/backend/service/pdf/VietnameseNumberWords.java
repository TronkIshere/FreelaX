package com.misa.backend.service.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VietnameseNumberWords {

    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] UNITS = {"", " nghìn", " triệu", " tỷ", " nghìn tỷ", " triệu tỷ"};

    private VietnameseNumberWords() {
    }

    public static String toVnd(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        long value = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        String words = toWords(Math.abs(value));
        String sentence = (value < 0 ? "âm " : "") + words + " đồng";
        return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1) + ".";
    }

    static String toWords(long value) {
        if (value == 0) {
            return DIGITS[0];
        }

        long[] groups = new long[UNITS.length];
        int count = 0;
        while (value > 0 && count < UNITS.length) {
            groups[count++] = value % 1000;
            value /= 1000;
        }

        StringBuilder result = new StringBuilder();
        for (int i = count - 1; i >= 0; i--) {
            if (groups[i] == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(readGroup((int) groups[i], i < count - 1)).append(UNITS[i]);
        }

        return result.toString().trim();
    }

    private static String readGroup(int group, boolean full) {
        int hundreds = group / 100;
        int tens = (group % 100) / 10;
        int ones = group % 10;
        StringBuilder sb = new StringBuilder();

        if (hundreds > 0 || full) {
            sb.append(DIGITS[hundreds]).append(" trăm");
        }

        if (tens == 0) {
            if (ones > 0) {
                if (sb.length() > 0) {
                    sb.append(" lẻ");
                }
                sb.append(sb.length() > 0 ? " " : "").append(DIGITS[ones]);
            }
        } else if (tens == 1) {
            sb.append(sb.length() > 0 ? " " : "").append("mười");
            if (ones == 5) {
                sb.append(" lăm");
            } else if (ones > 0) {
                sb.append(' ').append(DIGITS[ones]);
            }
        } else {
            sb.append(sb.length() > 0 ? " " : "").append(DIGITS[tens]).append(" mươi");
            if (ones == 1) {
                sb.append(" mốt");
            } else if (ones == 5) {
                sb.append(" lăm");
            } else if (ones > 0) {
                sb.append(' ').append(DIGITS[ones]);
            }
        }

        return sb.toString();
    }
}