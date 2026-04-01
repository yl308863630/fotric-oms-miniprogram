# 检查合同数据脚本
Write-Host "正在获取合同 HT202603010001 的数据..." -ForegroundColor Green

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/debug/contract/HT202603010001" -Method Get
    $data = $response.Content | ConvertFrom-Json
    
    Write-Host "`n=== 合同数据 ===" -ForegroundColor Yellow
    if ($data.contract) {
        $contract = $data.contract
        Write-Host "合同编号: $($contract.contractNo)"
        Write-Host "甲方: $($contract.partyA)"
        Write-Host "乙方: $($contract.partyB)"
        Write-Host "甲方名称: $($contract.partyAName)"
        Write-Host "乙方名称: $($contract.partyBName)"
        Write-Host "甲方税号: $($contract.partyATaxNo)"
        Write-Host "乙方税号: $($contract.partyBTaxNo)"
        Write-Host "甲方地址: $($contract.partyAAddress)"
        Write-Host "乙方地址: $($contract.partyBAddress)"
        Write-Host "甲方银行: $($contract.partyABank)"
        Write-Host "乙方银行: $($contract.partyBBank)"
        Write-Host "甲方账号: $($contract.partyAAccount)"
        Write-Host "乙方账号: $($contract.partyBAccount)"
        Write-Host "甲方电话: $($contract.partyAPhone)"
        Write-Host "乙方电话: $($contract.partyBPhone)"
        Write-Host "产品名称: $($contract.productName)"
        Write-Host "产品型号: $($contract.productModel)"
        Write-Host "物料号: $($contract.materialNo)"
        Write-Host "产品配置: $($contract.productConfig)"
        Write-Host "保修期: $($contract.warrantyPeriod)"
        Write-Host "数量: $($contract.quantity)"
        Write-Host "单价: $($contract.unitPrice)"
        Write-Host "总价: $($contract.totalAmount)"
        Write-Host "金额(含税): $($contract.amount)"
        Write-Host "金额(中文大写): $($contract.amountCn)"
        Write-Host "签约日期: $($contract.signDate)"
        Write-Host "销售姓名: $($contract.salesName)"
        Write-Host "乙方代表: $($contract.partyBRepresentative)"
        Write-Host "平台名称: $($contract.platformName)"
        Write-Host "支付方式: $($contract.paymentMethod)"
        Write-Host "交货地址: $($contract.deliveryAddress)"
        Write-Host "交货日期: $($contract.deliveryDate)"
        Write-Host "订单日期: $($contract.orderDate)"
        Write-Host "交货周期: $($contract.deliveryCycle)"
    }
    
    Write-Host "`n=== 销售订单数据 ===" -ForegroundColor Yellow
    if ($data.salesOrder) {
        $salesOrder = $data.salesOrder
        Write-Host "订单号: $($salesOrder.orderNo)"
        Write-Host "代运营主体: $($salesOrder.operationEntityTitle)"
        Write-Host "交付方: $($salesOrder.deliveryParty)"
        Write-Host "型号: $($salesOrder.model)"
        Write-Host "物料号: $($salesOrder.materialNo)"
        Write-Host "产品配置: $($salesOrder.productConfig)"
        Write-Host "保修期: $($salesOrder.warrantyPeriod)"
        Write-Host "数量: $($salesOrder.quantity)"
        Write-Host "平台名称: $($salesOrder.platformName)"
        Write-Host "支付方式: $($salesOrder.paymentMethod)"
        Write-Host "收货人地址: $($salesOrder.receiverAddress)"
        Write-Host "交货日期: $($salesOrder.deliveryDate)"
        Write-Host "订单日期: $($salesOrder.orderDate)"
    }
    
    Write-Host "`n=== 可用占位符 ===" -ForegroundColor Yellow
    if ($data.availablePlaceholders) {
        foreach ($placeholder in $data.availablePlaceholders.PSObject.Properties) {
            Write-Host "$($placeholder.Name): $($placeholder.Value)"
        }
    }
    
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}
