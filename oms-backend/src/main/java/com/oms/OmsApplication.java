package com.oms;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.SealService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.nio.file.Files;

@SpringBootApplication
public class OmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(OmsApplication.class, args);
    }

    /** 启动时生成飞础科电子章图片到项目根目录与 oms-backend，便于直接打开查看 */
    @Bean
    public CommandLineRunner generateTestSealFile(SealService sealService) {
        return args -> {
            String name = "飞础科智慧科技（上海）有限公司";
            try {
                byte[] png = sealService.generateSeal(name);
                String cwd = System.getProperty("user.dir");
                File curDir = new File(cwd);
                // 项目根目录（E:\订单系统）
                File rootDir = "oms-backend".equals(curDir.getName()) ? curDir.getParentFile() : curDir;
                String fileName = "test-seal-飞础科.png";
                java.util.List<File> written = new java.util.ArrayList<>();
                // 1. 项目根目录
                File out1 = new File(rootDir, fileName);
                Files.write(out1.toPath(), png);
                written.add(out1);
                // 2. 当前目录（若在 oms-backend 则多一份在这里）
                File out2 = new File(curDir, fileName);
                if (!out2.getAbsolutePath().equals(out1.getAbsolutePath())) {
                    Files.write(out2.toPath(), png);
                    written.add(out2);
                }
                // 3. uploads 目录
                File uploads = new File(curDir, "uploads");
                if (!uploads.exists()) uploads.mkdirs();
                File out3 = new File(uploads, fileName);
                Files.write(out3.toPath(), png);
                written.add(out3);
                for (File f : written) {
                    System.out.println("[电子章] 已生成: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println("[电子章] 生成失败: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    boolean updated = false;
                    if (admin.getRole() == null || !admin.getRole().equals("ROLE_ADMIN")) {
                        admin.setRole("ROLE_ADMIN");
                        updated = true;
                    }
                    if (admin.getEnabled() == null) {
                        admin.setEnabled(true);
                        updated = true;
                    }
                    if (admin.getPermissions() == null) {
                        admin.setPermissions("dashboard,product,opportunity,sales,purchase,settlement,cooperation,user");
                        updated = true;
                    }
                    if (updated) {
                        userRepository.save(admin);
                        System.out.println("管理员账号权限已更新");
                    }
                },
                () -> {
                    User admin = new User();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode("Yw110120"));
                    admin.setRole("ROLE_ADMIN");
                    admin.setRealName("管理员");
                    admin.setEnabled(true);
                    admin.setPermissions("dashboard,product,opportunity,sales,purchase,settlement,cooperation,user");
                    userRepository.save(admin);
                    System.out.println("预设管理员账号创建成功: admin / Yw110120");
                }
            );
        };
    }
}
