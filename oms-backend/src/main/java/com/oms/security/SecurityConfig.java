package com.oms.security;

import com.oms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfiguration = new org.springframework.web.cors.CorsConfiguration();
                    corsConfiguration.setAllowedOrigins(List.of("*"));
                    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    corsConfiguration.setAllowedHeaders(List.of("*"));
                    return corsConfiguration;
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/static/**", "/assets/**", "/favicon.ico").permitAll()
                        // 合同等敏感文件预览 /api/files/preview 需登录并按公司/订单鉴权，不放行匿名访问
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/uploads/**", "/api/files/download/**", "/api/upload", "/api/debug/**", "/api/barcode/**", "/api/contracts/seal-test", "/api/logistics/query", "/api/orders/import-from-file", "/api/jd/oauth/**", "/api/jd/tracking/push", "/api/authorizations/verify", "/api/authorizations/verify/**").permitAll()
                        .requestMatchers(new RequestMatcher() {
                            @Override
                            public boolean matches(HttpServletRequest request) {
                                String path = request.getServletPath();
                                String uri = request.getRequestURI();
                                return (path != null && path.contains("/api/auth/captcha")) || (uri != null && uri.contains("/api/auth/captcha"));
                            }
                        }).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/auth/captcha", "GET")).permitAll()
                        .requestMatchers(new RequestMatcher() {
                            @Override
                            public boolean matches(HttpServletRequest request) {
                                String path = request.getServletPath();
                                return path != null && path.startsWith("/api/contracts/") && path.endsWith("/placeholder-report");
                            }
                        }).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 非 API 请求（如 /sales、/product、/purchase、/opportunity）用于前端 SPA 刷新，必须放行
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}