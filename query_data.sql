-- 查询销售订单数据
SELECT 
  id,
  oms_order_no,
  platform_name,
  operation_entity_title,
  delivery_party,
  ecommerce_sales_name,
  model,
  quantity,
  tax_included_price,
  delivery_party_purchase_price,
  tax_included_total,
  payment_method,
  receiver_address,
  delivery_date,
  order_date
FROM sales_orders_new 
WHERE ecommerce_sales_name = '王潇龙'
LIMIT 3;

-- 查询合作伙伴信息
SELECT * FROM partner_info LIMIT 5;

-- 查询已生成的合同
SELECT * FROM contracts ORDER BY id DESC LIMIT 3;
