package com.oms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);
    private final UserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;

    public JwtRequestFilter(@org.springframework.context.annotation.Lazy UserDetailsService userDetailsService, JwtTokenUtil jwtTokenUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        // 检查是否是允许的路径，如果是则跳过JWT验证（同时检查 servletPath 与 requestURI，兼容代理/context-path）
        String path = request.getServletPath();
        String uri = request.getRequestURI();
        String safePath = path != null ? path : "";
        boolean isCaptcha = (path != null && path.contains("/api/auth/captcha")) || (uri != null && uri.contains("/api/auth/captcha"));
        boolean isApiRequest = safePath.startsWith("/api/");

        // 前端路由刷新请求（非 /api/**）直接放行，交给 SPA fallback 返回 index.html
        if (!isApiRequest) {
            chain.doFilter(request, response);
            return;
        }

        if ("/".equals(safePath) || "/index.html".equals(safePath) || safePath.startsWith("/static/") || safePath.startsWith("/assets/") || "/favicon.ico".equals(safePath)
                || "/api/auth/login".equals(safePath) || "/api/auth/register".equals(safePath) || isCaptcha
                || safePath.startsWith("/uploads/")
                // /api/files/preview 需携带 JWT 以便按用户公司抬头/订单可见性鉴权，不可在此跳过认证
                || (safePath.startsWith("/api/files/") && !safePath.startsWith("/api/files/preview"))
                || safePath.startsWith("/api/upload")
                || safePath.startsWith("/api/debug/") || safePath.startsWith("/api/barcode/")
                || "/api/contracts/seal-test".equals(safePath) || "/api/logistics/query".equals(safePath)
                || safePath.startsWith("/api/jd/oauth/")
                || "/api/jd/tracking/push".equals(safePath)
                || (safePath.startsWith("/api/contracts/") && safePath.endsWith("/placeholder-report"))) {
            chain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtTokenUtil.extractUsername(jwt);
            } catch (Exception e) {
                logger.error("Unable to get JWT Token or JWT Token has expired");
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if (jwtTokenUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            }
        }
        chain.doFilter(request, response);
    }
}
