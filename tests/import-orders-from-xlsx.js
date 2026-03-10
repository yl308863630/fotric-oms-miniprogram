/**
 * 按 订单实测.xlsx 数据集录入销售订单
 * - 按业务员分组：杨旺->yangwang, 史峰->shifeng, 宋敏->sonmin, 王潇龙->wangxiaolong；无账号的 ecommerceSalesId 留空
 * - 收件信息（最终用户等）为空则留空
 * - 账号：shifeng/Sf123456, yangwang/Yw110120, sonmin/Sm123456, wangxiaolong/Wxl123456
 */

const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');

const BASE_URL = process.env.OMS_API_BASE || 'http://localhost:8080';
const XLSX_PATH = process.argv[2] || path.join('e:\\OMS原型图', '订单实测.xlsx');

const ACCOUNTS = {
  yangwang:    { username: 'yangwang',    password: 'Yw110120', realName: '杨旺' },
  shifeng:    { username: 'shifeng',     password: 'Sf123456',  realName: '史峰' },
  sonmin:     { username: 'sonmin',      password: 'Sm123456',  realName: '宋敏' },
  wangxiaolong: { username: 'wangxiaolong', password: 'Wxl123456', realName: '王潇龙' },
};

// 业务员姓名 -> 账号 key
const SALES_TO_ACCOUNT = {
  '杨旺': 'yangwang',
  '史峰': 'shifeng',
  '石峰': 'shifeng',
  '宋敏': 'sonmin',
  '王潇龙': 'wangxiaolong',
};

function excelSerialToDate(serial) {
  if (serial == null || serial === '') return null;
  const n = Number(serial);
  if (isNaN(n)) return null;
  const utc = (n - 25569) * 86400 * 1000;
  const d = new Date(utc);
  const y = d.getUTCFullYear(), m = String(d.getUTCMonth() + 1).padStart(2, '0'), day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function request(method, url, token, body) {
  const u = new URL(url.startsWith('http') ? url : `${BASE_URL}${url}`);
  const isHttps = u.protocol === 'https:';
  const options = {
    hostname: u.hostname,
    port: u.port || (isHttps ? 443 : 80),
    path: u.pathname + u.search,
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  };
  return new Promise((resolve, reject) => {
    const req = (isHttps ? https : http).request(options, (res) => {
      let data = '';
      res.on('data', (ch) => (data += ch));
      res.on('end', () => {
        try {
          const parsed = data ? JSON.parse(data) : {};
          if (res.statusCode >= 400) reject(new Error(`HTTP ${res.statusCode}: ${data}`));
          else resolve(parsed);
        } catch (e) {
          if (res.statusCode >= 400) reject(new Error(`HTTP ${res.statusCode}: ${data}`));
          else resolve(data);
        }
      });
    });
    req.on('error', reject);
    if (body) req.write(typeof body === 'string' ? body : JSON.stringify(body));
    req.end();
  });
}

function parseSheet(rows) {
  const header = rows[0];
  const col = (name) => {
    const i = header.indexOf(name);
    return i >= 0 ? i : -1;
  };
  const idx = {
    平台: col('平台'),
    订单号: col('订单号'),
    OMS订单号: col('OMS订单号'),
    订单时间: col('订单时间'),
    物料号: col('物料号'),
    平台SKU: col('平台SKU'),
    型号: col('型号'),
    数量: col('数量'),
    含税订单金额: col('含税订单金额'),
    含税总价: col('含税总价'),
    交付方: col('交付方'),
    交付含税总价: col('交付含税总价'),
    物流单号: col('物流单号'),
    最终用户: col('最终用户'),
    业务员: col('业务员'),
  };
  const orders = [];
  for (let i = 1; i < rows.length; i++) {
    const r = rows[i];
    if (!r || r.length < 5) continue;
    const orderDate = excelSerialToDate(r[idx.订单时间]);
    const quantity = parseInt(r[idx.数量], 10) || 1;
    const taxTotal = parseFloat(r[idx.含税总价]) || parseFloat(r[idx.含税订单金额]) || 0;
    const taxPrice = quantity > 0 ? taxTotal / quantity : taxTotal;
    const deliveryParty = (r[idx.交付方] && String(r[idx.交付方]).trim() !== '无') ? String(r[idx.交付方]).trim() : '';
    const deliveryTotal = (r[idx.交付含税总价] != null && r[idx.交付含税总价] !== '' && String(r[idx.交付含税总价]).trim() !== '无')
      ? parseFloat(r[idx.交付含税总价]) : null;
    const salesName = r[idx.业务员] ? String(r[idx.业务员]).trim() : '';
    const accountKey = SALES_TO_ACCOUNT[salesName] || null;

    const order = {
      platformName: r[idx.平台] ? String(r[idx.平台]).trim() : '',
      platformOrderNo: r[idx.订单号] ? String(r[idx.订单号]).trim() : `IMP${Date.now()}_${i}`,
      omsOrderNo: '',
      orderDate: orderDate || new Date().toISOString().slice(0, 10),
      materialNo: r[idx.物料号] ? String(r[idx.物料号]).trim() : '',
      platformSku: r[idx.平台SKU] ? String(r[idx.平台SKU]).trim() : '',
      model: r[idx.型号] ? String(r[idx.型号]).trim() : '',
      quantity,
      taxIncludedPrice: taxPrice,
      taxIncludedTotal: taxTotal,
      amount: taxTotal,
      receiverName: (r[idx.最终用户] && String(r[idx.最终用户]).trim()) ? String(r[idx.最终用户]).trim() : '',
      receiverPhone: '',
      receiverAddress: '',
      deliveryParty,
      deliveryPartyPurchasePrice: deliveryTotal,
      status: '待指派',
      orderType: '第三方订单',
      products: [{
        model: r[idx.型号] ? String(r[idx.型号]).trim() : '',
        materialNo: r[idx.物料号] ? String(r[idx.物料号]).trim() : '',
        quantity,
        taxIncludedPrice: taxPrice,
        taxIncludedTotal: taxTotal,
        orderType: '第三方订单',
        deliveryParty: deliveryParty || undefined,
        deliveryPartyPurchasePrice: deliveryTotal,
      }],
      logistics: [{
        receiverName: (r[idx.最终用户] && String(r[idx.最终用户]).trim()) ? String(r[idx.最终用户]).trim() : '',
        receiverPhone: '',
        deliveryParty: deliveryParty || '',
        trackingNumber: (r[idx.物流单号] && String(r[idx.物流单号]).trim()) ? String(r[idx.物流单号]).trim() : '',
      }],
      invoices: [],
      reconciliations: [],
      _salesName: salesName,
      _accountKey: accountKey,
    };
    orders.push(order);
  }
  return orders;
}

async function main() {
  let XLSX;
  try {
    XLSX = require('xlsx');
  } catch (e) {
    console.error('请先安装: npm install xlsx');
    process.exit(1);
  }

  console.log('========== 按 订单实测.xlsx 录入销售订单 ==========');
  console.log('Excel:', XLSX_PATH);
  console.log('后端:', BASE_URL);

  if (!fs.existsSync(XLSX_PATH)) {
    console.error('文件不存在:', XLSX_PATH);
    process.exit(1);
  }

  const wb = XLSX.readFile(XLSX_PATH);
  const ws = wb.Sheets[wb.SheetNames[0]];
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
  const orders = parseSheet(rows);

  const byAccount = { yangwang: [], shifeng: [], sonmin: [], wangxiaolong: [], other: [] };
  orders.forEach((o) => {
    if (o._accountKey && byAccount[o._accountKey]) byAccount[o._accountKey].push(o);
    else byAccount.other.push(o);
  });

  console.log('\n解析订单数:', orders.length);
  console.log('  杨旺(yangwang):', byAccount.yangwang.length);
  console.log('  史峰(shifeng):', byAccount.shifeng.length);
  console.log('  宋敏(sonmin):', byAccount.sonmin.length);
  console.log('  王潇龙(wangxiaolong):', byAccount.wangxiaolong.length);
  console.log('  其他/空业务员:', byAccount.other.length);

  const created = { yangwang: 0, shifeng: 0, sonmin: 0, wangxiaolong: 0, other: 0 };
  const errors = [];

  let defaultToken = null;
  let defaultUserId = null;
  try {
    const loginRes = await request('POST', `${BASE_URL}/api/auth/login`, null, { username: ACCOUNTS.yangwang.username, password: ACCOUNTS.yangwang.password });
    defaultToken = loginRes.token;
    const usersRes = await request('GET', `${BASE_URL}/api/users?username=${ACCOUNTS.yangwang.username}&size=1`, defaultToken);
    const list2 = usersRes.content || usersRes;
    const userList = Array.isArray(list2) ? list2 : (list2.content || []);
    if (userList.length > 0) defaultUserId = userList[0].id;
  } catch (e) {
    console.error('默认登录(yangwang)失败:', e.message);
  }

  for (const [accountKey, list] of Object.entries(byAccount)) {
    if (list.length === 0) continue;
    let token = defaultToken;
    let userId = null;
    const acc = ACCOUNTS[accountKey];
    if (acc) {
      try {
        const loginRes = await request('POST', `${BASE_URL}/api/auth/login`, null, { username: acc.username, password: acc.password });
        token = loginRes.token;
        const usersRes = await request('GET', `${BASE_URL}/api/users?username=${acc.username}&size=1`, token);
        const list2 = usersRes.content || usersRes;
        const userList = Array.isArray(list2) ? list2 : (list2.content || []);
        if (userList.length > 0) userId = userList[0].id;
      } catch (e) {
        console.error(`  [${accountKey}] 登录失败:`, e.message);
        errors.push({ account: accountKey, phase: 'login', error: e.message });
        continue;
      }
    }
    for (const o of list) {
      const payload = { ...o };
      delete payload._salesName;
      delete payload._accountKey;
      if (userId) payload.ecommerceSalesId = userId;
      if (!token) { errors.push({ account: accountKey, platformOrderNo: payload.platformOrderNo, error: '无可用 token' }); continue; }
      try {
        const existRes = await request('GET', `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(payload.platformOrderNo)}&size=1`, token);
        const existList = existRes.content || existRes;
        const arr = Array.isArray(existList) ? existList : (existList.content || []);
        if (arr.length > 0) continue;
        await request('POST', `${BASE_URL}/api/sales-orders`, token, payload);
        created[accountKey]++;
        if (created[accountKey] <= 2) console.log(`  [${accountKey}] 已创建:`, payload.platformOrderNo || payload.platformName);
      } catch (e) {
        errors.push({ account: accountKey, platformOrderNo: payload.platformOrderNo, error: e.message });
        if (errors.length <= 5) console.error('  创建失败:', payload.platformOrderNo, e.message);
      }
    }
    if (acc) console.log(`  [${accountKey}] 合计创建:`, created[accountKey]);
  }

  console.log('\n========== 录入结果 ==========');
  console.log('yangwang     创建:', created.yangwang);
  console.log('shifeng      创建:', created.shifeng);
  console.log('sonmin       创建:', created.sonmin);
  console.log('wangxiaolong 创建:', created.wangxiaolong);
  console.log('其他         创建:', created.other);
  if (errors.length > 0) {
    console.log('失败/错误数:', errors.length);
    const outPath = path.join(__dirname, 'evidence', 'import-errors.json');
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, JSON.stringify(errors, null, 2), 'utf8');
    console.log('错误明细已写入:', outPath);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
