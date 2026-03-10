package com.oms.util;

import java.util.HashMap;
import java.util.Map;

public class LogisticsCompanyCode {
    private static final Map<String, String> COMPANY_CODE_MAP = new HashMap<>();

    static {
        COMPANY_CODE_MAP.put("顺丰速运", "shunfeng");
        COMPANY_CODE_MAP.put("顺丰", "shunfeng");
        COMPANY_CODE_MAP.put("京东物流", "jd");
        COMPANY_CODE_MAP.put("京东", "jd");
        COMPANY_CODE_MAP.put("中通快递", "zhongtong");
        COMPANY_CODE_MAP.put("中通", "zhongtong");
        COMPANY_CODE_MAP.put("圆通速递", "yuantong");
        COMPANY_CODE_MAP.put("圆通", "yuantong");
        COMPANY_CODE_MAP.put("韵达快递", "yunda");
        COMPANY_CODE_MAP.put("韵达", "yunda");
        COMPANY_CODE_MAP.put("申通快递", "shentong");
        COMPANY_CODE_MAP.put("申通", "shentong");
        COMPANY_CODE_MAP.put("邮政EMS", "ems");
        COMPANY_CODE_MAP.put("EMS", "ems");
        COMPANY_CODE_MAP.put("德邦快递", "debang");
        COMPANY_CODE_MAP.put("德邦", "debang");
        COMPANY_CODE_MAP.put("极兔速递", "jtexpress");
        COMPANY_CODE_MAP.put("极兔", "jtexpress");
        COMPANY_CODE_MAP.put("中国邮政", "youzhengguonei");
        COMPANY_CODE_MAP.put("邮政", "youzhengguonei");
        COMPANY_CODE_MAP.put("天天快递", "tiantian");
        COMPANY_CODE_MAP.put("天天", "tiantian");
        COMPANY_CODE_MAP.put("宅急送", "zhaijisong");
        COMPANY_CODE_MAP.put("中铁快运", "zhongtiekuaiyun");
        COMPANY_CODE_MAP.put("中铁", "zhongtiekuaiyun");
        COMPANY_CODE_MAP.put("安能物流", "annengwuliu");
        COMPANY_CODE_MAP.put("安能", "annengwuliu");
        COMPANY_CODE_MAP.put("百世快递", "huitongkuaidi");
        COMPANY_CODE_MAP.put("百世", "huitongkuaidi");
        COMPANY_CODE_MAP.put("速尔快递", "suer");
        COMPANY_CODE_MAP.put("速尔", "suer");
        COMPANY_CODE_MAP.put("全峰快递", "quanfengkuaidi");
        COMPANY_CODE_MAP.put("全峰", "quanfengkuaidi");
        COMPANY_CODE_MAP.put("国通快递", "guotongkuaidi");
        COMPANY_CODE_MAP.put("国通", "guotongkuaidi");
        COMPANY_CODE_MAP.put("优速快递", "youshuwuliu");
        COMPANY_CODE_MAP.put("优速", "youshuwuliu");
    }

    public static String getCode(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return "";
        }
        return COMPANY_CODE_MAP.getOrDefault(companyName.trim(), "");
    }

    public static boolean contains(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return false;
        }
        return COMPANY_CODE_MAP.containsKey(companyName.trim());
    }

    public static boolean isShunfeng(String companyName) {
        return "顺丰速运".equals(companyName) || "顺丰".equals(companyName);
    }

    public static boolean isJD(String companyName) {
        return "京东物流".equals(companyName) || "京东".equals(companyName);
    }
}
