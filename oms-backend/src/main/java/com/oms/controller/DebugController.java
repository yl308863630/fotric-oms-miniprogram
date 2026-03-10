package com.oms.controller;

import com.oms.entity.Contract;
import com.oms.entity.ContractTemplate;
import com.oms.entity.PartnerInfo;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.ContractRepository;
import com.oms.repository.ContractTemplateRepository;
import com.oms.repository.PartnerInfoRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.service.SealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ContractTemplateRepository contractTemplateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SealService sealService;

    /**
     * 测试电子章生成：生成一张 PNG 图片，便于在浏览器中查看电子章是否正常。
     * 用法：浏览器打开 http://localhost:8080/api/debug/seal-test
     * 或带参数：http://localhost:8080/api/debug/seal-test?name=飞础科智慧科技（上海）有限公司
     * 若生成失败则返回文本错误信息，便于排查（如缺少字体）。
     */
    @GetMapping("/seal-test")
    public ResponseEntity<?> testSeal(@RequestParam(required = false, defaultValue = "测试电子章") String name) {
        try {
            byte[] png = sealService.generateSeal(name);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(png.length);
            return ResponseEntity.ok().headers(headers).body(png);
        } catch (Exception e) {
            String msg = "电子章生成失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body(msg);
        }
    }

    @GetMapping("/data")
    public Map<String, Object> getDebugData() {
        Map<String, Object> result = new HashMap<>();
        
        List<SalesOrder> salesOrders = salesOrderRepository.findAll();
        result.put("salesOrders", salesOrders);
        
        List<PartnerInfo> partners = partnerInfoRepository.findAll();
        result.put("partners", partners);
        
        List<Contract> contracts = contractRepository.findAll();
        result.put("contracts", contracts);
        
        List<ContractTemplate> templates = contractTemplateRepository.findAll();
        result.put("contractTemplates", templates);
        
        return result;
    }

    @GetMapping("/contract/{contractNo}")
    public Map<String, Object> getContractByNo(@PathVariable String contractNo) {
        Map<String, Object> result = new HashMap<>();
        
        Contract contract = contractRepository.findByContractNo(contractNo).orElse(null);
        result.put("contract", contract);
        
        if (contract != null && contract.getSalesOrderId() != null) {
            SalesOrder salesOrder = salesOrderRepository.findById(contract.getSalesOrderId()).orElse(null);
            result.put("salesOrder", salesOrder);
        }
        
        Map<String, String> availablePlaceholders = new HashMap<>();
        availablePlaceholders.put("${contractNo}", "合同编号");
        availablePlaceholders.put("${partyA}", "甲方");
        availablePlaceholders.put("${partyB}", "乙方");
        availablePlaceholders.put("${partyAName}", "甲方名称");
        availablePlaceholders.put("${partyBName}", "乙方名称");
        availablePlaceholders.put("${partyATaxNo}", "甲方税号");
        availablePlaceholders.put("${partyBTaxNo}", "乙方税号");
        availablePlaceholders.put("${partyAAddress}", "甲方地址");
        availablePlaceholders.put("${partyBAddress}", "乙方地址");
        availablePlaceholders.put("${partyABank}", "甲方银行");
        availablePlaceholders.put("${partyBBank}", "乙方银行");
        availablePlaceholders.put("${partyAAccount}", "甲方银行账号");
        availablePlaceholders.put("${partyBAccount}", "乙方银行账号");
        availablePlaceholders.put("${partyAPhone}", "甲方电话");
        availablePlaceholders.put("${partyBPhone}", "乙方电话");
        availablePlaceholders.put("${productModel}", "产品型号");
        availablePlaceholders.put("${productName}", "产品名称");
        availablePlaceholders.put("${materialNo}", "物料号");
        availablePlaceholders.put("${productConfig}", "产品配置");
        availablePlaceholders.put("${warrantyPeriod}", "保修期");
        availablePlaceholders.put("${quantity}", "数量");
        availablePlaceholders.put("${unitPrice}", "单价");
        availablePlaceholders.put("${totalAmount}", "总价");
        availablePlaceholders.put("${amount}", "金额(含税)");
        availablePlaceholders.put("${amountCn}", "金额(中文大写)");
        availablePlaceholders.put("${signDate}", "签约日期");
        availablePlaceholders.put("${salesName}", "销售姓名");
        availablePlaceholders.put("${platformName}", "平台名称(即甲方抬头)");
        availablePlaceholders.put("${settlementParty}", "结算方式及期限(仅工业电商合同有值=顶层客户名；其他为空，占位符不显示)");
        availablePlaceholders.put("${partyATitle}", "甲方抬头");
        availablePlaceholders.put("${partyAOrderNo}", "甲方订单号");
        availablePlaceholders.put("${paymentMethod}", "支付方式");
        availablePlaceholders.put("${甲方}", "甲方名称(同partyAName)");
        availablePlaceholders.put("${乙方}", "乙方名称(同partyBName)");
        availablePlaceholders.put("${deliveryAddress}", "交货地址");
        availablePlaceholders.put("${deliveryDate}", "交货日期");
        availablePlaceholders.put("${orderDate}", "订单日期");
        availablePlaceholders.put("${deliveryCycle}", "交货周期");
        availablePlaceholders.put("${partyBRepresentative}", "乙方代表");
        result.put("availablePlaceholders", availablePlaceholders);
        
        return result;
    }

    @GetMapping("/users")
    public Map<String, Object> getAllUsers() {
        Map<String, Object> result = new HashMap<>();
        List<User> users = userRepository.findAll();
        result.put("users", users);
        result.put("count", users.size());
        return result;
    }
}
