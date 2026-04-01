package com.oms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 兼容前端传入带千分位/货币符号的金额字符串，如 "22,105.00"、"¥22,105.00"。
 */
public class LenientBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return p.getDecimalValue();
        }
        if (token == JsonToken.VALUE_STRING) {
            String raw = p.getText();
            if (raw == null) return null;
            String cleaned = raw.trim();
            if (cleaned.isEmpty()) return null;

            cleaned = cleaned
                    .replace(",", "")
                    .replace("，", "")
                    .replace("¥", "")
                    .replace("￥", "")
                    .replace(" ", "");

            if (cleaned.isEmpty()) return null;
            if (cleaned.endsWith("%")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
            }
            if (cleaned.isEmpty()) return null;

            try {
                return new BigDecimal(cleaned);
            } catch (NumberFormatException ex) {
                return (BigDecimal) ctxt.handleWeirdStringValue(
                        BigDecimal.class,
                        raw,
                        "无法解析为金额，请输入数字，例如 22105.00"
                );
            }
        }
        return (BigDecimal) ctxt.handleUnexpectedToken(BigDecimal.class, p);
    }
}

