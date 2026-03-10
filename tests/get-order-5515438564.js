/**
 * 查询平台订单号 5515438564 的当前数据，便于完善
 * 运行：node get-order-5515438564.js
 * 需后端已启动；默认用 admin/Yw110120 登录（可改下方 ACCOUNT）
 */

const http = require('http');
const BASE = process.env.OMS_API_BASE || 'http://localhost:8080';
const ACCOUNT = { username: 'admin', password: 'Yw110120' };
const PLATFORM_ORDER_NO = '5515438564';

function request(method, url, token, body) {
  const u = new URL(url.startsWith('http') ? url : BASE + url);
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: u.hostname,
      port: u.port || 80,
      path: u.pathname + u.search,
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    }, res => {
      let data = '';
      res.on('data', c => (data += c));
      res.on('end', () => {
        try {
          const j = data ? JSON.parse(data) : {};
          if (res.statusCode >= 400) reject(new Error(res.statusCode + ': ' + data));
          else resolve(j);
        } catch (e) {
          if (res.statusCode >= 400) reject(new Error(res.statusCode + ': ' + data));
          else resolve(data);
        }
      });
    });
    req.on('error', reject);
    if (body) req.write(typeof body === 'string' ? body : JSON.stringify(body));
    req.end();
  });
}

async function main() {
  console.log('查询平台订单号:', PLATFORM_ORDER_NO);
  console.log('后端:', BASE, '\n');

  let token;
  try {
    const login = await request('POST', BASE + '/api/auth/login', null, ACCOUNT);
    token = login.token;
  } catch (e) {
    console.error('登录失败:', e.message);
    process.exit(1);
  }

  const list = await request('GET', BASE + '/api/sales-orders?platformOrderNo=' + encodeURIComponent(PLATFORM_ORDER_NO) + '&size=1', token);
  const content = list.content || list;
  const arr = Array.isArray(content) ? content : (content.content || []);
  const order = arr[0];

  if (!order) {
    console.log('未找到该平台订单号，请确认是否已录入或是否有权限查看。');
    return;
  }

  const empty = (v) => v == null || String(v).trim() === '';
  const fields = [
    ['platformName', '平台名字'],
    ['platformOrderNo', '平台订单号'],
    ['omsOrderNo', '工业电商销售订单号'],
    ['orderDate', '订单时间'],
    ['materialNo', '物料号'],
    ['platformSku', '平台SKU'],
    ['model', '型号'],
    ['quantity', '数量'],
    ['taxIncludedPrice', '含税单价'],
    ['taxIncludedTotal', '含税总价'],
    ['ecommerceSalesName', '业务员'],
    ['receiverName', '收货人'],
    ['receiverPhone', '收货人电话'],
    ['receiverAddress', '收货人地址'],
    ['operationEntityTitle', '代运营主体抬头'],
    ['deliveryParty', '交付方'],
    ['deliveryPartyPurchasePrice', '交付方采购价'],
    ['deductionRate', '扣点'],
    ['paymentMethod', '支付方式'],
    ['orderType', '订单类型'],
    ['status', '状态'],
    ['invoiceTitle', '平台发票抬头'],
    ['finalCustomerTitle', '最终客户抬头'],
    ['logisticsCompany', '物流公司'],
    ['trackingNumber', '物流单号'],
    ['contractUrl', '合同文件'],
    ['deliveryNoteUrl', '送货单'],
    ['receiptUrl', '签收单'],
    ['invoiceNumber', '发票号码'],
    ['invoiceUrl', '发票文件'],
  ];

  console.log('========== 当前数据 ==========');
  console.log('id:', order.id);
  for (const [key, label] of fields) {
    const v = order[key];
    const str = v != null ? String(v) : '';
    const status = empty(str) ? '[空]' : '[有]';
    console.log('  ' + label + ' (' + key + '): ' + status + ' ' + (str.length > 60 ? str.slice(0, 60) + '...' : str));
  }
  console.log('\n========== 建议完善项（在 销售管理 -> 编辑该订单 中填写）==========');
  const missing = fields.filter(([key]) => empty(order[key]));
  if (missing.length === 0) {
    console.log('  无，主要字段已填写。可再核对：物流、发票、对账、合同等。');
  } else {
    missing.forEach(([key, label]) => console.log('  - ' + label + ' (' + key + ')'));
  }
  console.log('\n订单 id 用于接口调试:', order.id);
}

main().catch(e => { console.error(e); process.exit(1); });
