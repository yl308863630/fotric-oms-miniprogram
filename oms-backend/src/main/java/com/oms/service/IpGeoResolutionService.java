package com.oms.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;

/**
 * 使用 ip2region 离线库将公网 IPv4 解析为国家/省/市（地市粒度，非门牌；内网 IP 标记为「内网」）。
 * 数据文件：classpath {@code /ip2region/ip2region_v4.xdb}（见 lionsoul2014/ip2region 仓库 data 目录）。
 */
@Service
public class IpGeoResolutionService {

    private static final Logger log = LoggerFactory.getLogger(IpGeoResolutionService.class);
    private static final String DB_RESOURCE = "/ip2region/ip2region_v4.xdb";

    private Searcher searcher;
    private volatile boolean available;

    public record GeoParts(String country, String region, String city) {}

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getResourceAsStream(DB_RESOURCE)) {
            if (is == null) {
                log.warn("未找到 {} ，扫码日志地理位置将保持 UNKNOWN。请将 ip2region 的 data/ip2region_v4.xdb 放到 src/main/resources/ip2region/ 下。", DB_RESOURCE);
                return;
            }
            byte[] buf = is.readAllBytes();
            if (buf.length < 1024) {
                log.warn("{} 文件异常过小，跳过加载", DB_RESOURCE);
                return;
            }
            this.searcher = Searcher.newWithBuffer(buf);
            this.available = true;
            log.info("ip2region IPv4 库已加载，可按 IP 解析国家/省/市");
        } catch (Exception e) {
            log.warn("加载 ip2region 失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 解析失败或库未加载时返回 null。
     */
    public GeoParts lookup(String ip) {
        if (!available || searcher == null || ip == null || ip.isBlank()) {
            return null;
        }
        String clean = ip.trim();
        if ("UNKNOWN".equalsIgnoreCase(clean)) {
            return null;
        }
        if (isPrivateOrLocal(clean)) {
            return new GeoParts("内网", "内网", "内网");
        }
        try {
            String line = searcher.search(clean);
            if (line == null || line.isBlank()) {
                return null;
            }
            // 典型：中国|0|浙江省|杭州市|电信
            String[] p = line.split("\\|", -1);
            String country = normPart(p, 0);
            String region = normPart(p, 2);
            String city = normPart(p, 3);
            return new GeoParts(country, region, city);
        } catch (Exception e) {
            log.debug("ip2region 解析失败 ip={}: {}", clean, e.getMessage());
            return null;
        }
    }

    private static String normPart(String[] p, int idx) {
        if (p == null || idx >= p.length) {
            return "-";
        }
        String s = p[idx] == null ? "" : p[idx].trim();
        if (s.isEmpty() || "0".equals(s)) {
            return "-";
        }
        return s;
    }

    private static boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            // 非标准 IP 字符串时退回简单规则
            String lower = ip.toLowerCase();
            if (lower.startsWith("127.") || "::1".equals(lower) || "0:0:0:0:0:0:0:1".equals(lower)) {
                return true;
            }
            if (lower.startsWith("10.")) {
                return true;
            }
            if (lower.startsWith("192.168.")) {
                return true;
            }
            if (lower.startsWith("172.")) {
                String[] parts = lower.split("\\.");
                if (parts.length >= 2) {
                    try {
                        int second = Integer.parseInt(parts[1]);
                        return second >= 16 && second <= 31;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                }
            }
            return false;
        }
    }
}
