package com.oms.util;

import java.math.BigDecimal;

public class MoneyUtil {

    private static final String[] CN_NUMBERS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UNITS = {"", "拾", "佰", "仟"};
    private static final String[] CN_BIG_UNITS = {"", "万", "亿", "兆"};

    public static String convertToChinese(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "零元整";
        }

        StringBuilder result = new StringBuilder();

        long yuan = amount.longValue();
        int jiao = amount.multiply(new BigDecimal("10")).intValue() % 10;
        int fen = amount.multiply(new BigDecimal("100")).intValue() % 10;

        if (yuan > 0) {
            result.append(convertYuan(yuan)).append("元");
        }

        if (jiao > 0) {
            result.append(CN_NUMBERS[jiao]).append("角");
        } else if (yuan > 0 && fen > 0) {
            result.append("零");
        }

        if (fen > 0) {
            result.append(CN_NUMBERS[fen]).append("分");
        } else if (jiao == 0 && yuan > 0) {
            result.append("整");
        }

        return result.toString();
    }

    private static String convertYuan(long yuan) {
        if (yuan == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int unitIndex = 0;
        boolean needZero = false;

        while (yuan > 0) {
            int section = (int) (yuan % 10000);
            if (section > 0) {
                String sectionStr = convertSection(section);
                if (needZero) {
                    result.insert(0, "零");
                }
                result.insert(0, sectionStr + CN_BIG_UNITS[unitIndex]);
                needZero = section < 1000 && section > 0;
            } else {
                needZero = result.length() > 0;
            }
            yuan = yuan / 10000;
            unitIndex++;
        }

        return result.toString();
    }

    private static String convertSection(int section) {
        if (section == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean needZero = false;

        for (int i = 0; i < 4; i++) {
            int digit = section % 10;
            if (digit > 0) {
                if (needZero) {
                    result.insert(0, "零");
                }
                result.insert(0, CN_NUMBERS[digit] + CN_UNITS[i]);
                needZero = false;
            } else {
                needZero = result.length() > 0;
            }
            section = section / 10;
        }

        return result.toString();
    }
}
