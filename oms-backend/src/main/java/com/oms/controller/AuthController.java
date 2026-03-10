package com.oms.controller;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.security.JwtTokenUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        System.out.println("Login attempt for user: " + request.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            System.out.println("Authentication successful for user: " + request.getUsername());
        } catch (Exception e) {
            System.out.println("Authentication failed for user: " + request.getUsername());
            System.out.println("Error: " + e.getMessage());
            throw new RuntimeException("用户名或密码错误");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        System.out.println("UserDetails loaded: " + userDetails.getUsername());
        final String jwt = jwtTokenUtil.generateToken(userDetails);
        System.out.println("JWT token generated successfully");

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("username", userDetails.getUsername());
        if (user != null) {
            response.put("realName", user.getRealName());
            response.put("companyTitle", user.getCompanyTitle());
            response.put("role", user.getRole());
            response.put("permissions", user.getPermissions());
        }
        return response;
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null) user.setRole("ROLE_USER");
        return userRepository.save(user);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
