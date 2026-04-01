import requests
import json
import sys

# 后端服务地址
BASE_URL = "http://localhost:8080"

try:
    print("正在获取调试数据...")
    debug_url = f"{BASE_URL}/api/debug/data"
    response = requests.get(debug_url, timeout=10)
    
    if response.status_code == 200:
        data = response.json()
        print(f"成功获取调试数据！")
        print()
        
        # 保存到文件
        output_file = 'e:\\订单系统\\debug_data.json'
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        print(f"调试数据已保存到: {output_file}")
        print()
        
        # 显示合同模板
        if "contractTemplates" in data:
            print("="*50)
            print("=== 合同模板 ===")
            print("="*50)
            templates = data["contractTemplates"]
            for template in templates:
                print(f"模板ID: {template['id']}")
                print(f"模板名称: {template['templateName']}")
                print(f"模板URL: {template['templateUrl']}")
                print()
        
        # 显示合同
        if "contracts" in data:
            print("="*50)
            print("=== 合同列表 ===")
            print("="*50)
            contracts = data["contracts"]
            for contract in contracts:
                print(f"合同编号: {contract.get('contractNo', 'N/A')}")
                print(f"订单ID: {contract.get('salesOrderId', 'N/A')}")
                print(f"甲方名称: {contract.get('partyAName', 'N/A')}")
                print(f"乙方名称: {contract.get('partyBName', 'N/A')}")
                print(f"产品名称: {contract.get('productName', 'N/A')}")
                print(f"产品型号: {contract.get('productModel', 'N/A')}")
                print(f"物料号: {contract.get('materialNo', 'N/A')}")
                print(f"产品配置: {contract.get('productConfig', 'N/A')}")
                print(f"保修期: {contract.get('warrantyPeriod', 'N/A')}")
                print(f"数量: {contract.get('quantity', 'N/A')}")
                print(f"单价: {contract.get('unitPrice', 'N/A')}")
                print(f"总价: {contract.get('totalAmount', 'N/A')}")
                print(f"金额(含税): {contract.get('amount', 'N/A')}")
                print(f"金额(中文大写): {contract.get('amountCn', 'N/A')}")
                print(f"平台名称: {contract.get('platformName', 'N/A')}")
                print(f"支付方式: {contract.get('paymentMethod', 'N/A')}")
                print(f"交货地址: {contract.get('deliveryAddress', 'N/A')}")
                print(f"交货日期: {contract.get('deliveryDate', 'N/A')}")
                print(f"订单日期: {contract.get('orderDate', 'N/A')}")
                print(f"交货周期: {contract.get('deliveryCycle', 'N/A')}")
                print()
                print("-"*50)
                print()
        
    else:
        print(f"获取调试数据失败，状态码: {response.status_code}")
        print(response.text)
        
except Exception as e:
    print(f"发生错误: {e}")
    import traceback
    traceback.print_exc()

print()
print("脚本执行完成！")
