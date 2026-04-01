package com.oms;

import com.oms.service.SealService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

@SpringBootTest
public class SealTest {

    @Autowired
    private SealService sealService;

    @Test
    public void testGenerateSeal() throws IOException {
        String companyName = "上海热像科技股份有限公司";
        String filePath = "e:\\订单系统\\test_seal.png";
        File file = sealService.generateSealToFile(companyName, filePath);
        System.out.println("电子章已生成: " + file.getAbsolutePath());
    }
}
