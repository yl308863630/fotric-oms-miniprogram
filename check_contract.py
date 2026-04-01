import requests
import json

# 后端服务地址
BASE_URL = "http://localhost:8080"

# 1. 先获取一个JWT token（需要先登录）
print("请先在前端登录，然后复制Authorization头中的JWT token...")
print("或者我们可以尝试直接查看合同数据...")
print()

# 尝试调用调试接口
try:
    print("正在获取调试数据...")
    debug_url = f"{BASE_URL}/api/debug/data"
    response = requests.get(debug_url, timeout=10)
    
    if response.status_code == 200:
        data = response.json()
        print(f"成功获取调试数据！")
        print()
        
        # 保存到文件
        with open('e:\\订单系统\\debug_data.json', 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        print("调试数据已保存到: e:\\订单系统\\debug_data.json")
        print()
        
        # 显示合同模板
        if "contractTemplates" in data:
            print("=== 合同模板 ===")
            templates = data["contractTemplates"]
            for template in templates:
                print(f"模板ID: {template['id']}")
                print(f"模板名称: {template['templateName']}")
                print(f"模板URL: {template['templateUrl']}")
                print()
        
        # 显示合同
        if "contracts" in data:
            print("=== 合同列表 ===")
            contracts = data["contracts"]
            for contract in contracts:
                print(f"合同编号: {contract['contractNo']}")
                print(f"订单ID: {contract['salesOrderId']}")
                print(f"甲方名称: {contract['partyAName']}")
                print(f"乙方名称: {contract['partyBName']}")
                print(f"产品型号: {contract['productModel']}")
                print(f"物料号: {contract['materialNo']}")
                print(f"产品配置: {contract['productConfig']}")
                print(f"保修期: {contract['warrantyPeriod']}")
                print(f"数量: {contract['quantity']}")
                print(f"单价: {contract['unitPrice']}")
                print(f"总价: {contract['totalAmount']}")
                print(f"金额(含税): {contract['amount']}")
                print(f"金额(中文大写): {contract['amountCn']}")
                print(f"平台名称: {contract['platformName']}")
                print(f"支付方式: {contract['paymentMethod']}")
                print(f"交货地址: {contract['deliveryAddress']}")
                print(f"交货日期: {contract['deliveryDate']}")
                print(f"订单日期: {contract['orderDate']}")
                print(f"交货周期: {contract['deliveryCycle']}")
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
