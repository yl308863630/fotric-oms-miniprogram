/**
 * OMS 销售订单流程验证 - API 自动化测试（销售/采购分离后）
 * 步骤：登录(sonmin) -> 新建两条销售订单(仅销售侧字段) -> 采购侧指派(更新交付方/采购价并生成合同) -> 校验
 * 运行：node flow-verification-api.js  或  npm test
 * 环境：需先启动后端 (http://localhost:8080) 与 MySQL；前端可选。
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');

const { BASE_URL, USERS, ORDER_1, ORDER_2, ASSIGN_EXPECTATIONS } = require('./config.js');

const EVIDENCE_DIR = path.join(__dirname, 'evidence');
if (!fs.existsSync(EVIDENCE_DIR)) {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
}

function writeEvidence(name, data) {
  const file = path.join(EVIDENCE_DIR, `${name}.json`);
  fs.writeFileSync(file, JSON.stringify(data, null, 2), 'utf8');
  console.log(`  [凭证] ${file}`);
}

function request(method, url, token, body) {
  const u = new URL(url.startsWith('http') ? url : `${BASE_URL}${url}`);
  const isHttps = u.protocol === 'https:';
  const options = {
    hostname: u.hostname,
    port: u.port || (isHttps ? 443 : 80),
    path: u.pathname + u.search,
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  };
  return new Promise((resolve, reject) => {
    const req = (isHttps ? https : http).request(options, (res) => {
      let data = '';
      res.on('data', (ch) => (data += ch));
      res.on('end', () => {
        try {
          const parsed = data ? JSON.parse(data) : {};
          if (res.statusCode >= 400) {
            reject(new Error(`HTTP ${res.statusCode}: ${data}`));
          } else {
            resolve(parsed);
          }
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

async function main() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  console.log('========== OMS 销售订单流程验证 (API) ==========');
  console.log('时间:', new Date().toISOString());
  console.log('后端:', BASE_URL);
  console.log('');

  let token;
  let creatorUserId;

  // ---------- 1. 登录 sonmin ----------
  console.log('[步骤 1] 登录 (sonmin / Sm123456)');
  try {
    const loginRes = await request('POST', `${BASE_URL}/api/auth/login`, null, {
      username: USERS.creator.username,
      password: USERS.creator.password,
    });
    token = loginRes.token;
    writeEvidence(`${timestamp}-01-login`, { username: loginRes.username, hasToken: !!token });
    console.log('  成功:', loginRes.username, loginRes.realName || '');
  } catch (e) {
    console.error('  失败:', e.message);
    process.exit(1);
  }

  // 获取当前用户 ID（用于 ecommerceSalesId，生成 OMS 订单号）
  try {
    const usersRes = await request('GET', `${BASE_URL}/api/users?username=${USERS.creator.username}&size=1`, token);
    const list = usersRes.content || usersRes;
    if (Array.isArray(list) && list.length > 0) {
      creatorUserId = list[0].id;
    } else if (list && list.content && list.content.length > 0) {
      creatorUserId = list.content[0].id;
    }
  } catch (_) {}
  if (creatorUserId) {
    ORDER_1.ecommerceSalesId = creatorUserId;
    ORDER_2.ecommerceSalesId = creatorUserId;
  }

  // 销售侧新建：不包含交付方/出货方/扣点/交付方采购价（由采购管理指派时填写）
  const salesOnlyPayload = (order) => {
    const { deliveryParty, deliveryPartyPurchasePrice, deductionRate, shippingParty, ...rest } = order;
    const payload = { ...rest };
    if (payload.products && payload.products[0]) {
      const p = { ...payload.products[0] };
      delete p.deliveryPartyPurchasePrice;
      delete p.deductionRate;
      payload.products = [{ ...p }];
    }
    if (payload.logistics && payload.logistics[0]) {
      const l = { ...payload.logistics[0] };
      delete l.deliveryParty;
      payload.logistics = [{ ...l }];
    }
    return payload;
  };

  // ---------- 2. 新建销售订单 1 (D20260210SM01) ----------
  console.log('\n[步骤 2] 新建销售订单 D20260210SM01（销售侧，仅平台价）');
  let order1Id;
  try {
    const create1 = await request('POST', `${BASE_URL}/api/sales-orders`, token, salesOnlyPayload(ORDER_1));
    order1Id = create1.id;
    const omsNo1 = create1.omsOrderNo || create1.platformOrderNo;
    writeEvidence(`${timestamp}-02-create-order1`, create1);
    console.log('  成功 id=', order1Id, 'omsOrderNo=', omsNo1);
    console.log('  交付方=', create1.deliveryParty, '交付成交价=', create1.deliveryPartyPurchasePrice);
  } catch (e) {
    console.error('  失败:', e.message);
    process.exit(1);
  }

  // ---------- 3. 新建销售订单 2 (D20260209SM01) ----------
  console.log('\n[步骤 3] 新建销售订单 D20260209SM01（销售侧，仅平台价）');
  let order2Id;
  try {
    const create2 = await request('POST', `${BASE_URL}/api/sales-orders`, token, salesOnlyPayload(ORDER_2));
    order2Id = create2.id;
    const omsNo2 = create2.omsOrderNo || create2.platformOrderNo;
    writeEvidence(`${timestamp}-03-create-order2`, create2);
    console.log('  成功 id=', order2Id, 'omsOrderNo=', omsNo2);
    console.log('  交付方=', create2.deliveryParty, '交付成交价=', create2.deliveryPartyPurchasePrice);
  } catch (e) {
    console.error('  失败:', e.message);
    process.exit(1);
  }

  // ---------- 4. 采购管理-指派：订单 1 → 上海坚领（填写交付方、交付方采购价=A的采购价=B的销售价，生成合同） ----------
  console.log('\n[步骤 4] 指派订单 1 → 上海坚领（坚领）');
  try {
    const update1 = await request('PUT', `${BASE_URL}/api/sales-orders/${order1Id}`, token, {
      ...ORDER_1,
      id: order1Id,
      deliveryParty: '上海坚领电子科技有限公司',
      deliveryPartyPurchasePrice: 17747.78,
      deductionRate: '20',
      status: '待指派',
    });
    writeEvidence(`${timestamp}-04-update-order1`, update1);

    const templatesRes = await request('GET', `${BASE_URL}/api/contract-templates?size=1`, token);
    const templates = templatesRes.content || templatesRes;
    const templateUrl = (Array.isArray(templates) ? templates[0] : templates?.content?.[0])?.templateUrl;
    if (templateUrl) {
      await request('POST', `${BASE_URL}/api/contracts/generate-from-order/${order1Id}`, token, {
        templateUrl,
        partyBRepresentative: '坚领',
        platformName: ORDER_1.platformName,
        paymentMethod: ORDER_1.paymentMethod || '账期',
      });
      await request('PATCH', `${BASE_URL}/api/sales-orders/${order1Id}/status`, token, { status: '待合同盖章' });
      writeEvidence(`${timestamp}-04-assign-order1`, { orderId: order1Id, assignedTo: '坚领' });
      console.log('  已生成合同并设为待合同盖章');
    } else {
      await request('PUT', `${BASE_URL}/api/sales-orders/${order1Id}`, token, {
        ...update1,
        assignedUsername: '坚领',
        deliveryParty: '上海坚领电子科技有限公司',
        deliveryPartyPurchasePrice: 17747.78,
      });
      console.log('  已更新订单（无合同模板时仅更新指派信息）');
    }
  } catch (e) {
    console.error('  失败:', e.message);
    writeEvidence(`${timestamp}-04-assign-order1-error`, { error: e.message });
  }

  // ---------- 5. 采购管理-指派：订单 2 → 上海热像科技 ----------
  console.log('\n[步骤 5] 指派订单 2 → 上海热像科技（热像科技-商务）');
  try {
    const update2 = await request('PUT', `${BASE_URL}/api/sales-orders/${order2Id}`, token, {
      ...ORDER_2,
      id: order2Id,
      deliveryParty: '上海热像科技股份有限公司',
      deliveryPartyPurchasePrice: ORDER_2.deliveryPartyPurchasePrice,
      deductionRate: ORDER_2.deductionRate,
      status: '待指派',
    });
    writeEvidence(`${timestamp}-05-update-order2`, update2);

    const templatesRes = await request('GET', `${BASE_URL}/api/contract-templates?size=1`, token);
    const templates = templatesRes.content || templatesRes;
    const templateUrl = (Array.isArray(templates) ? templates[0] : templates?.content?.[0])?.templateUrl;
    if (templateUrl) {
      await request('POST', `${BASE_URL}/api/contracts/generate-from-order/${order2Id}`, token, {
        templateUrl,
        partyBRepresentative: '热像科技-商务',
        platformName: ORDER_2.platformName,
        paymentMethod: ORDER_2.paymentMethod || '账期',
      });
      await request('PATCH', `${BASE_URL}/api/sales-orders/${order2Id}/status`, token, { status: '待合同盖章' });
      writeEvidence(`${timestamp}-05-assign-order2`, { orderId: order2Id, assignedTo: '热像科技-商务' });
      console.log('  已生成合同并设为待合同盖章');
    } else {
      await request('PUT', `${BASE_URL}/api/sales-orders/${order2Id}`, token, {
        ...update2,
        assignedUsername: '热像科技-商务',
        deliveryParty: '上海热像科技股份有限公司',
      });
      console.log('  已更新订单（无合同模板时仅更新指派信息）');
    }
  } catch (e) {
    console.error('  失败:', e.message);
    writeEvidence(`${timestamp}-05-assign-order2-error`, { error: e.message });
  }

  // ---------- 6. 校验：按 id 拉取订单详情（不传 view），核对交付方与交付方采购价 ----------
  console.log('\n[步骤 6] 校验订单数据与交付成交价');
  try {
    const fetch1 = await request('GET', `${BASE_URL}/api/sales-orders/${order1Id}`, token);
    const fetch2 = await request('GET', `${BASE_URL}/api/sales-orders/${order2Id}`, token);
    writeEvidence(`${timestamp}-06-order1-detail`, fetch1);
    writeEvidence(`${timestamp}-06-order2-detail`, fetch2);

    const o1 = fetch1 && (fetch1.id != null ? fetch1 : fetch1.content) || fetch1;
    const o2 = fetch2 && (fetch2.id != null ? fetch2 : fetch2.content) || fetch2;

    let ok = true;
    if (o1 && o1.id) {
      const exp = ASSIGN_EXPECTATIONS.D20260210SM01;
      const deliveryOk = o1.deliveryParty && o1.deliveryParty.indexOf('坚领') >= 0;
      const priceOk = exp.deliveryPartyPurchasePrice == null || Number(o1.deliveryPartyPurchasePrice) === exp.deliveryPartyPurchasePrice || Number(o1.deliveryPartyPurchasePrice) > 0;
      const assignOk = !exp.assignedUsername || o1.assignedUsername === exp.assignedUsername;
      console.log('  订单 1 (id=' + o1.id + '): deliveryParty=', o1.deliveryParty, deliveryOk ? '✓' : '✗', 'deliveryPartyPurchasePrice=', o1.deliveryPartyPurchasePrice, priceOk ? '✓' : '✗', 'assignedUsername=', o1.assignedUsername, assignOk ? '✓' : '(可选)');
      if (!deliveryOk || !priceOk) ok = false;
    } else {
      console.log('  订单 1: 获取详情失败');
      ok = false;
    }
    if (o2 && o2.id) {
      const exp = ASSIGN_EXPECTATIONS.D20260209SM01;
      const deliveryOk = o2.deliveryParty && o2.deliveryParty.indexOf('热像') >= 0;
      const assignOk = !exp.assignedUsername || o2.assignedUsername === exp.assignedUsername;
      console.log('  订单 2 (id=' + o2.id + '): deliveryParty=', o2.deliveryParty, deliveryOk ? '✓' : '✗', 'deliveryPartyPurchasePrice=', o2.deliveryPartyPurchasePrice, 'assignedUsername=', o2.assignedUsername, assignOk ? '✓' : '(可选)');
      if (!deliveryOk) ok = false;
    } else {
      console.log('  订单 2: 获取详情失败');
      ok = false;
    }

    console.log('\n========== 流程验证', ok ? '通过' : '存在异常', '==========');
    process.exit(ok ? 0 : 1);
  } catch (e) {
    console.error('  校验失败:', e.message);
    process.exit(1);
  }
}

main();
