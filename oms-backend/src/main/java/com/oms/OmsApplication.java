package com.oms;

import com.oms.entity.PartyAPaymentRule;
import com.oms.entity.User;
import com.oms.repository.PartyAPaymentRuleRepository;
import com.oms.repository.UserRepository;
import com.oms.service.SealService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class OmsApplication {
    public static void main(String[] args) {
        // iText PDF AES-256 加密依赖 BouncyCastle；未注册时部分环境会抛 GeneralSecurityException 导致「生成文档」500
        if (java.security.Security.getProvider(org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME) == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
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

    /**
     * 历史库中的 users.permissions 常停留在 VARCHAR(255)，勾选较多权限后保存用户会 400。
     * Hibernate update 不一定会把已有列自动扩成 TEXT，这里在启动时做一次幂等兜底。
     */
    @Bean
    public CommandLineRunner ensureUsersPermissionsTextColumn(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                String dataType = jdbcTemplate.query(
                        "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'permissions'",
                        rs -> rs.next() ? rs.getString(1) : null
                );
                if (dataType == null || dataType.isBlank()) {
                    System.out.println("[启动迁移] 未找到 users.permissions 列，跳过检查");
                    return;
                }
                if (!"text".equalsIgnoreCase(dataType.trim())) {
                    jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN permissions TEXT NULL COMMENT '权限列表逗号分隔'");
                    System.out.println("[启动迁移] 已自动将 users.permissions 升级为 TEXT");
                }
            } catch (Exception e) {
                System.err.println("[启动迁移] 检查或升级 users.permissions 失败: " + e.getMessage());
            }
        };
    }

    @Bean
    public CommandLineRunner seedDefaultPartyAPaymentRules(PartyAPaymentRuleRepository partyAPaymentRuleRepository) {
        return args -> {
            try {
                ensureDefaultPartyAPaymentRule(
                        partyAPaymentRuleRepository,
                        "震坤行工业超市（上海）有限公司",
                        "DELIVERY_DATE",
                        25,
                        25,
                        true,
                        60,
                        "FIXED_DAY_OF_NEXT_MONTH",
                        5,
                        null,
                        null,
                        "25日前交货，当月25日作为对账基准，向后推60天后取次月5号付款",
                        "震坤行：当月25日前交货 -> 当月25日开始对账 -> +60天 -> 次月5号付款"
                );

                ensureDefaultPartyAPaymentRule(
                        partyAPaymentRuleRepository,
                        "西域智慧供应链（上海）股份公司",
                        "INVOICE_DATE",
                        null,
                        null,
                        true,
                        60,
                        "INTERVAL_DAY_BUCKET",
                        15,
                        25,
                        15,
                        "开票后推60天；若60天结果落在1-15日则当月15号付款，16-25日则当月25号付款，26日后则次月15号付款",
                        "西域：开票后+60天，1-15取当月15号，16-25取当月25号，26日后取次月15号"
                );
            } catch (Exception e) {
                System.err.println("[启动种子] 初始化甲方回款规则失败: " + e.getMessage());
            }
        };
    }

    private void ensureDefaultPartyAPaymentRule(
            PartyAPaymentRuleRepository repository,
            String title,
            String baseEventType,
            Integer baseDayOfMonth,
            Integer cycleCutoffDay,
            boolean carryOverToNextCycle,
            int offsetDays,
            String paymentAnchorType,
            Integer anchorDay1,
            Integer anchorDay2,
            Integer anchorDay3,
            String description,
            String exampleRuleText
    ) {
        List<PartyAPaymentRule> existingList = repository.findAllByPartyATitleTrimmed(title);
        if (existingList != null && !existingList.isEmpty()) {
            System.out.println("[启动种子] 甲方回款规则已存在，跳过: " + title);
            return;
        }
        PartyAPaymentRule rule = new PartyAPaymentRule();
        rule.setPartyATitle(title);
        rule.setEnabled(true);
        rule.setBaseEventType(baseEventType);
        rule.setBaseDayOfMonth(baseDayOfMonth);
        rule.setCycleCutoffDay(cycleCutoffDay);
        rule.setCarryOverToNextCycle(carryOverToNextCycle);
        rule.setOffsetDays(offsetDays);
        rule.setPaymentAnchorType(paymentAnchorType);
        rule.setAnchorDay1(anchorDay1);
        rule.setAnchorDay2(anchorDay2);
        rule.setAnchorDay3(anchorDay3);
        rule.setDescription(description);
        rule.setExampleRuleText(exampleRuleText);
        repository.save(rule);
        System.out.println("[启动种子] 已预置甲方回款规则: " + title);
    }
}
