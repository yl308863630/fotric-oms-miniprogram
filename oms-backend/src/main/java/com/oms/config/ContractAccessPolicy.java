package com.oms.config;

import com.oms.entity.User;

import java.util.Set;

/**
 * 合同可见与预览权限：rxkj-sw / rxkj-cw 对热像+飞础科合同全量；飞础科主体销售列表合同操作等。
 */
public final class ContractAccessPolicy {

    private ContractAccessPolicy() {}

    public static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    public static final String REXIANG_TITLE = "上海热像科技股份有限公司";

    /** 商务/财务等：热像+飞础科维度全量合同（列表/详情/下载等） */
    public static final Set<String> FULL_CONTRACT_PORTAL_USERNAMES = Set.of("rxkj-sw", "rxkj-cw");

    /** 用户管理可勾选：与上述账号同等「热像+飞础科全量合同」 */
    public static final String PERM_CONTRACT_FULL_RX_FEICHUKE = "contract_full_rx_feichuke";

    /** 用户管理可勾选：飞础科销售列表合同按钮/预览（公司抬头未维护时的兜底） */
    public static final String PERM_SALES_FEICHUKE_CONTRACT = "sales_feichuke_contract";

    /** 用户管理可勾选：平台合同全量查看（列表/详情/下载/预览） */
    public static final String PERM_CONTRACT_PLATFORM_VIEW_ALL = "contract_platform_view_all";

    public static boolean hasUserPermission(User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) {
            return false;
        }
        String perms = user.getPermissions();
        if (perms == null || perms.isBlank()) {
            return false;
        }
        for (String p : perms.split(",")) {
            if (permission.equalsIgnoreCase(p.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 热像+飞础科「全量合同门户」账号（含管理员）。
     * 用于预览、销售列表合同按钮等与管理员同权扩展。
     */
    public static boolean hasFullRexiangFeichukeContractPortal(User user) {
        if (user == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        return hasRexiangFeichukeContractFullPortalWithoutAdmin(user);
    }

    /** 不含管理员：合同列表仅看热像+飞础科相关时使用，避免与管理员「全表」逻辑混淆 */
    public static boolean hasRexiangFeichukeContractFullPortalWithoutAdmin(User user) {
        if (user == null || "ROLE_ADMIN".equals(user.getRole())) {
            return false;
        }
        String u = user.getUsername();
        if (u != null && FULL_CONTRACT_PORTAL_USERNAMES.contains(u.trim())) {
            return true;
        }
        return hasUserPermission(user, PERM_CONTRACT_FULL_RX_FEICHUKE);
    }

    /** 平台合同全量查看权限（不含管理员判断，由调用方决定是否先放行管理员） */
    public static boolean hasPlatformContractViewAll(User user) {
        return hasUserPermission(user, PERM_CONTRACT_PLATFORM_VIEW_ALL);
    }

    /**
     * 与历史「甲方合同仅飞础科、热像可预览」一致；并兼容公司抬头简称。
     */
    public static boolean matchesContractViewCompanyTitle(String companyTitle) {
        if (companyTitle == null || companyTitle.isBlank()) {
            return false;
        }
        String ct = companyTitle.trim();
        if (ct.contains(FEICHUKE_TITLE) || ct.contains(REXIANG_TITLE)) {
            return true;
        }
        if (FEICHUKE_TITLE.contains(ct) || REXIANG_TITLE.contains(ct)) {
            return true;
        }
        return ct.contains("飞础科智慧科技（上海）") || ct.contains("飞础科智慧科技");
    }

    /**
     * 销售列表合同操作：飞础科/热像抬头、全量门户账号、或显式权限 sales_feichuke_contract。
     */
    public static boolean canFeichukeSalesListContractActions(User user) {
        if (user == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (hasFullRexiangFeichukeContractPortal(user)) {
            return true;
        }
        if (matchesContractViewCompanyTitle(user.getCompanyTitle())) {
            return true;
        }
        return hasUserPermission(user, PERM_SALES_FEICHUKE_CONTRACT);
    }

    /** 合同上传文件预览：管理员 / 全量门户 / 飞础科销售合同权限 / 原有两家公司抬头 */
    public static boolean mayPreviewContractUploadByUser(User user) {
        if (user == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (hasFullRexiangFeichukeContractPortal(user)) {
            return true;
        }
        if (hasPlatformContractViewAll(user)) {
            return true;
        }
        if (canFeichukeSalesListContractActions(user)) {
            return true;
        }
        return matchesContractViewCompanyTitle(user.getCompanyTitle());
    }
}
