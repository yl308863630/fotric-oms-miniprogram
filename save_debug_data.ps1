# PowerShell script to fetch debug data
$baseUrl = "http://localhost:8080"
$debugUrl = "$baseUrl/api/debug/data"

Write-Host "正在获取调试数据..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri $debugUrl -Method Get -TimeoutSec 10 -ErrorAction Stop
    
    # Save to file
    $outputFile = "e:\订单系统\debug_data.json"
    $response | ConvertTo-Json -Depth 10 | Out-File -FilePath $outputFile -Encoding UTF8
    
    Write-Host "调试数据已保存到: $outputFile" -ForegroundColor Green
    Write-Host ""
    
    # Display contract templates
    if ($response.contractTemplates) {
        Write-Host "==================================================" -ForegroundColor Yellow
        Write-Host "=== 合同模板 ===" -ForegroundColor Yellow
        Write-Host "==================================================" -ForegroundColor Yellow
        foreach ($template in $response.contractTemplates) {
            Write-Host "模板ID: $($template.id)" -ForegroundColor White
            Write-Host "模板名称: $($template.templateName)" -ForegroundColor White
            Write-Host "模板URL: $($template.templateUrl)" -ForegroundColor White
            Write-Host ""
        }
    }
    
    # Display contracts
    if ($response.contracts) {
        Write-Host "==================================================" -ForegroundColor Yellow
        Write-Host "=== 合同列表 ===" -ForegroundColor Yellow
        Write-Host "==================================================" -ForegroundColor Yellow
        foreach ($contract in $response.contracts) {
            if ($contract.contractNo) { $contractNo = $contract.contractNo } else { $contractNo = "N/A" }
            if ($contract.salesOrderId) { $salesOrderId = $contract.salesOrderId } else { $salesOrderId = "N/A" }
            if ($contract.partyAName) { $partyAName = $contract.partyAName } else { $partyAName = "N/A" }
            if ($contract.partyBName) { $partyBName = $contract.partyBName } else { $partyBName = "N/A" }
            if ($contract.productName) { $productName = $contract.productName } else { $productName = "N/A" }
            if ($contract.productModel) { $productModel = $contract.productModel } else { $productModel = "N/A" }
            if ($contract.materialNo) { $materialNo = $contract.materialNo } else { $materialNo = "N/A" }
            if ($contract.productConfig) { $productConfig = $contract.productConfig } else { $productConfig = "N/A" }
            if ($contract.warrantyPeriod) { $warrantyPeriod = $contract.warrantyPeriod } else { $warrantyPeriod = "N/A" }
            if ($contract.quantity) { $quantity = $contract.quantity } else { $quantity = "N/A" }
            if ($contract.unitPrice) { $unitPrice = $contract.unitPrice } else { $unitPrice = "N/A" }
            if ($contract.totalAmount) { $totalAmount = $contract.totalAmount } else { $totalAmount = "N/A" }
            if ($contract.amount) { $amount = $contract.amount } else { $amount = "N/A" }
            if ($contract.amountCn) { $amountCn = $contract.amountCn } else { $amountCn = "N/A" }
            if ($contract.platformName) { $platformName = $contract.platformName } else { $platformName = "N/A" }
            if ($contract.paymentMethod) { $paymentMethod = $contract.paymentMethod } else { $paymentMethod = "N/A" }
            if ($contract.deliveryAddress) { $deliveryAddress = $contract.deliveryAddress } else { $deliveryAddress = "N/A" }
            if ($contract.deliveryDate) { $deliveryDate = $contract.deliveryDate } else { $deliveryDate = "N/A" }
            if ($contract.orderDate) { $orderDate = $contract.orderDate } else { $orderDate = "N/A" }
            if ($contract.deliveryCycle) { $deliveryCycle = $contract.deliveryCycle } else { $deliveryCycle = "N/A" }
            
            Write-Host "合同编号: $contractNo" -ForegroundColor White
            Write-Host "订单ID: $salesOrderId" -ForegroundColor White
            Write-Host "甲方名称: $partyAName" -ForegroundColor White
            Write-Host "乙方名称: $partyBName" -ForegroundColor White
            Write-Host "产品名称: $productName" -ForegroundColor White
            Write-Host "产品型号: $productModel" -ForegroundColor White
            Write-Host "物料号: $materialNo" -ForegroundColor White
            Write-Host "产品配置: $productConfig" -ForegroundColor White
            Write-Host "保修期: $warrantyPeriod" -ForegroundColor White
            Write-Host "数量: $quantity" -ForegroundColor White
            Write-Host "单价: $unitPrice" -ForegroundColor White
            Write-Host "总价: $totalAmount" -ForegroundColor White
            Write-Host "金额(含税): $amount" -ForegroundColor White
            Write-Host "金额(中文大写): $amountCn" -ForegroundColor White
            Write-Host "平台名称: $platformName" -ForegroundColor White
            Write-Host "支付方式: $paymentMethod" -ForegroundColor White
            Write-Host "交货地址: $deliveryAddress" -ForegroundColor White
            Write-Host "交货日期: $deliveryDate" -ForegroundColor White
            Write-Host "订单日期: $orderDate" -ForegroundColor White
            Write-Host "交货周期: $deliveryCycle" -ForegroundColor White
            Write-Host ""
            Write-Host "--------------------------------------------------" -ForegroundColor Gray
            Write-Host ""
        }
    }
    
} catch {
    Write-Host "发生错误: $_" -ForegroundColor Red
    Write-Host $_.ScriptStackTrace -ForegroundColor Red
}

Write-Host ""
Write-Host "脚本执行完成！" -ForegroundColor Cyan
