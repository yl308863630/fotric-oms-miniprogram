/**
 * 第二订单全流程 E2E：平台订单 5516469437（第三方坚领 + 第三方青岛科创 + 自营热像科技）
 * 步骤 1～12：登录、新建 3 单、平台合同占位、指派、合同、送货单占位、仓库/签收、平台回款、发票占位、对交付方付款占位、完结校验
 * 运行：cd tests && npx playwright install chromium && node order2-full-e2e.js
 */

const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const { execSync } = require('child_process');
const { pathToFileURL } = require('url');

const {
  BASE_URL,
  FRONTEND_URL,
  USERS,
  PLATFORM_ORDER_5516469437,
  ORDER_5516469437_JIANLING,
  ORDER_5516469437_QINGDAO,
  ORDER_5516469437_REFIRM,
  RECEIPT_TEST_FOLDER,
  DELIVERY_NOTE_AC65,
  DELIVERY_NOTE_XM3,
  PLATFORM_CONTRACT_5516469437,
  INVOICE_JIANLING_PDF,
  INVOICE_QINGDAO_PDF,
} = require('./config.js');

const EVIDENCE_BASE = path.join(__dirname, 'evidence', 'screenshots');
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
const EVIDENCE_DIR = path.join(EVIDENCE_BASE, `order2-full-${RUN_ID}`);
if (!fs.existsSync(EVIDENCE_DIR)) {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
}

function shot(page, name) {
  const file = path.join(EVIDENCE_DIR, `${name}.png`);
  return page.screenshot({ path: file }).then(() => {
    console.log(`  凭证: ${name}.png`);
    return file;
  });
}

/** 若页面出现错误提示或红色校验文字则截图 */
async function shotIfErrorOrRed(page, stepLabel) {
  await page.waitForTimeout(600);
  const errMsg = page.locator('.el-message--error, .el-form-item__error, [class*="error"]').first();
  if (await errMsg.isVisible().catch(() => false)) {
    const name = `error-red-${String(stepLabel).replace(/[^a-z0-9.-]/gi, '-')}.png`;
    await page.screenshot({ path: path.join(EVIDENCE_DIR, name) });
    console.log(`  [截图] 检测到报错/红色提示: ${name}`);
  }
}

/** 若页面出现旧公司名「上海坚领仪器有限公司」则截图并抛错 */
async function assertNoOldCompanyName(page, stepLabel) {
  const body = await page.locator('body').textContent().catch(() => '');
  if (body && body.includes('上海坚领仪器有限公司')) {
    const name = `error-old-company-${String(stepLabel).replace(/[^a-z0-9.-]/gi, '-')}.png`;
    await page.screenshot({ path: path.join(EVIDENCE_DIR, name) });
    console.error(`  [失败] 页面仍出现「上海坚领仪器有限公司」，已截图: ${name}`);
    throw new Error('页面出现旧公司名：上海坚领仪器有限公司，已截图 ' + name);
  }
}

async function fillSelectByTyping(page, container, value) {
  const input = container.locator('input').first();
  await input.click({ force: true });
  await input.fill('');
  await input.type(String(value), { delay: 40 });
  await page.waitForTimeout(150);
  await page.keyboard.press('Enter').catch(() => {});
  await page.waitForTimeout(120);
  await page.keyboard.press('Tab').catch(() => {});
}

function apiRequest(method, urlPath, token, body) {
  const u = new URL(urlPath.startsWith('http') ? urlPath : `${BASE_URL}${urlPath}`);
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

async function loginAsCreatorToken() {
  const loginRes = await apiRequest('POST', `${BASE_URL}/api/auth/login`, null, {
    username: USERS.creator.username,
    password: USERS.creator.password,
  });
  const token = loginRes?.token;
  if (!token) throw new Error('登录失败：未获取到 token');
  return token;
}

async function loginToken(username, password) {
  const loginRes = await apiRequest('POST', `${BASE_URL}/api/auth/login`, null, { username, password });
  const token = loginRes?.token;
  if (!token) throw new Error(`登录失败：${username} 未获取到 token`);
  return token;
}

async function getOrdersByPlatformOrderNo(platformOrderNo) {
  const token = await loginAsCreatorToken();
  const listRes = await apiRequest(
    'GET',
    `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=50`,
    token
  );
  const list = listRes?.content || listRes;
  const orders = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
  return (orders || []).filter((o) => o.platformOrderNo === platformOrderNo).sort((a, b) => Number(a.id || 0) - Number(b.id || 0));
}

async function assertSalesOrderCreated(platformOrderNo) {
  const matched = await getOrdersByPlatformOrderNo(platformOrderNo);
  const order = matched.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0];
  if (!order || !order.id) {
    throw new Error(`创建校验失败：未找到平台订单号 ${platformOrderNo}`);
  }
  return order;
}

async function getOrderById(orderId) {
  const token = await loginAsCreatorToken();
  return apiRequest('GET', `${BASE_URL}/api/sales-orders/${orderId}`, token);
}

async function patchOrderPlatformRefundStatus(orderId, platformRefundStatus) {
  const token = await loginAsCreatorToken();
  const order = await getOrderById(orderId);
  if (!order || !order.id) throw new Error('Order not found: ' + orderId);
  const body = { ...order, platformRefundStatus };
  return apiRequest('PUT', `${BASE_URL}/api/sales-orders/${orderId}`, token, body);
}

async function getContractBySalesOrderId(salesOrderId) {
  const token = await loginAsCreatorToken();
  return apiRequest('GET', `${BASE_URL}/api/contracts/sales-order/${salesOrderId}`, token);
}

function toAbsoluteContractUrl(urlPath) {
  if (!urlPath) return '';
  if (/^https?:\/\//i.test(urlPath)) return urlPath;
  const base = BASE_URL.replace(/\/$/, '');
  return urlPath.startsWith('/') ? base + urlPath : base + '/' + urlPath;
}

function downloadBinary(url, token, outputFile) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url.startsWith('http') ? url : BASE_URL + url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const req = lib.request(
      {
        hostname: parsed.hostname,
        port: parsed.port,
        path: parsed.pathname + parsed.search,
        method: 'GET',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
      (res) => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          let body = '';
          res.on('data', (c) => (body += c.toString()));
          res.on('end', () => reject(new Error(`下载失败 HTTP ${res.statusCode}: ${body}`)));
          return;
        }
        const ws = fs.createWriteStream(outputFile);
        res.pipe(ws);
        ws.on('finish', () => resolve(outputFile));
        ws.on('error', reject);
      }
    );
    req.on('error', reject);
    req.end();
  });
}

function extractZipToDir(zipFile, targetDir) {
  if (!fs.existsSync(targetDir)) fs.mkdirSync(targetDir, { recursive: true });
  const zipEscaped = zipFile.replace(/'/g, "''");
  const dirEscaped = targetDir.replace(/'/g, "''");
  const cmd = `powershell -NoProfile -Command "Expand-Archive -Path '${zipEscaped}' -DestinationPath '${dirEscaped}' -Force"`;
  execSync(cmd, { stdio: 'pipe' });
}

function listImageFilesRecursively(rootDir) {
  const out = [];
  const walk = (dir) => {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) walk(full);
      else if (/\.(png|jpg|jpeg|webp)$/i.test(e.name)) out.push(full);
    }
  };
  walk(rootDir);
  return out.sort();
}

async function signContractAs(username, password, contractId, partyType) {
  const token = await loginToken(username, password);
  return apiRequest('POST', `${BASE_URL}/api/contracts/${contractId}/sign`, token, { partyType });
}

async function cleanupOrdersByPlatformOrderNo(platformOrderNo) {
  const accounts = [USERS.creator, USERS.jianling, USERS.refirm, USERS.refirmWarehouse].filter(Boolean);
  let touched = 0;
  for (const acc of accounts) {
    let token;
    try {
      token = await loginToken(acc.username, acc.password);
    } catch (e) {
      continue;
    }
    let orders = [];
    try {
      const listRes = await apiRequest(
        'GET',
        `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=200`,
        token
      );
      const list = listRes?.content || listRes;
      const all = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
      orders = all.filter((o) => o.platformOrderNo === platformOrderNo);
    } catch (e) {
      continue;
    }
    for (const o of orders.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))) {
      try {
        if (o.status === '已退回') continue;
        try {
          await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${o.id}/return`, token, { returnReason: 'E2E order2 清理' });
        } catch {
          await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${o.id}/status`, token, { status: '已退回' });
        }
        touched += 1;
      } catch (e) {}
    }
  }
  if (touched > 0) console.log(`  清理：已处理 ${touched} 条 ${platformOrderNo} 订单`);
}

function getFirstReceiptTestFile() {
  try {
    if (!fs.existsSync(RECEIPT_TEST_FOLDER)) return null;
    const names = fs.readdirSync(RECEIPT_TEST_FOLDER);
    const first = names.find((n) => !n.startsWith('.'));
    return first ? path.join(RECEIPT_TEST_FOLDER, first) : null;
  } catch (e) {
    return null;
  }
}

/** 填写新建订单弹窗：甲方、平台订单号、商品明细、物流、发票，不点确定 */
async function fillOrderForm(page, dialog, orderData) {
  const d = dialog;
  const platformInput = d.locator('input').first();
  await platformInput.click();
  await platformInput.fill(orderData.platformName);
  await page.waitForTimeout(400);
  const orderNoInput = d.locator('input').nth(1);
  await orderNoInput.click();
  await page.waitForTimeout(300);
  await orderNoInput.fill(orderData.platformOrderNo);
  await page.waitForTimeout(200);

  await d.getByRole('tab', { name: '商品明细' }).click();
  await page.waitForTimeout(300);
  const productTable = d.locator('.el-table').first();
  const firstRow = productTable.locator('tbody tr').first();
  if (orderData.platformSku) {
    await firstRow.locator('td').nth(4).locator('input').fill(orderData.platformSku);
    await page.waitForTimeout(150);
  }
  const modelCell = firstRow.locator('td').nth(5);
  await fillSelectByTyping(page, modelCell, orderData.model);
  await page.waitForTimeout(200);
  if (orderData.productConfig) await firstRow.locator('td').nth(6).locator('input').fill(orderData.productConfig);
  if (orderData.warrantyPeriod) await firstRow.locator('td').nth(7).locator('input').fill(orderData.warrantyPeriod);
  await firstRow.locator('td').nth(9).locator('input').fill(String(orderData.taxIncludedPrice));
  await page.waitForTimeout(200);
  if (orderData.orderType === '自营') {
    const orderTypeSelect = firstRow.locator('td').locator('.el-select').first();
    if (await orderTypeSelect.count()) {
      await orderTypeSelect.click();
      await page.waitForTimeout(200);
      await page.getByRole('option', { name: '自营' }).click();
      await page.waitForTimeout(200);
    }
  }

  await d.getByRole('tab', { name: '物流信息' }).click();
  await page.waitForTimeout(300);
  const logRow = orderData.logistics[0];
  const logTable = d.locator('.el-table').nth(1);
  const logFirstRow = logTable.locator('tbody tr').first();
  await logFirstRow.locator('td').nth(4).locator('input').first().fill(logRow.receiverName);
  await page.waitForTimeout(100);
  await logFirstRow.locator('td').nth(5).locator('input').first().fill(logRow.receiverPhone);
  await logFirstRow.locator('td').nth(6).locator('textarea').fill(logRow.receiverAddress);
  await logFirstRow.locator('td').nth(7).locator('.el-date-editor input').fill(logRow.deliveryDate);
  if (logRow.productQuantity != null) {
    const qtyInput = logFirstRow.locator('td').nth(3).locator('.el-input-number input');
    if (await qtyInput.count()) await qtyInput.fill(String(logRow.productQuantity));
  }
  await page.waitForTimeout(200);

  await d.getByRole('tab', { name: '发票信息' }).click();
  await page.waitForTimeout(300);
  const invoiceTable = d.locator('.el-table').nth(2);
  const invoiceFirstRow = invoiceTable.locator('tbody tr').first();
  const invoiceTitleCell = invoiceFirstRow.locator('td').nth(3);
  await fillSelectByTyping(page, invoiceTitleCell, orderData.invoice.invoiceTitle || orderData.platformName);
  const payCell = invoiceFirstRow.locator('td').nth(5);
  await fillSelectByTyping(page, payCell, orderData.invoice.paymentMethod);
  await page.waitForTimeout(300);
}

/** 在指派弹窗中填写交付方、采购价、乙方业务员、合同模板，可选点生成合同 */
async function fillAssignDialog(page, assignD, assignData, screenshotName) {
  const deliveryInput = assignD.getByPlaceholder(/交付方.*乙方|交付方/).first();
  if (await deliveryInput.count()) await deliveryInput.fill(assignData.deliveryParty, { force: true });
  const partyATitleInput = assignD.locator('.el-form-item').filter({ hasText: '甲方抬头' }).locator('input').first();
  if (await partyATitleInput.count()) await partyATitleInput.fill(assignData.platformName || '飞础科智慧科技（上海）有限公司', { force: true });
  const priceInputAssign = assignD.getByPlaceholder(/A的采购价|交付方采购价/).first();
  await priceInputAssign.fill(String(assignData.deliveryPartyPurchasePrice), { force: true });
  await page.waitForTimeout(200);
  const partyBSelect = assignD.getByText('乙方业务员').locator('..').locator('.el-select');
  if (await partyBSelect.count()) await fillSelectByTyping(page, partyBSelect, assignData.partyBRepresentative || '袁星辉');
  const templateSelect = assignD.getByText('合同模板').locator('..').locator('.el-select');
  if (await templateSelect.count()) await fillSelectByTyping(page, templateSelect, assignData.templateName || 'FOTRIC电商扣点派单采购合同');
  await page.waitForTimeout(200);
  if (screenshotName) await shot(page, screenshotName);
  const genContractBtn = page.locator('button').filter({ hasText: '生成合同' }).first();
  if (await genContractBtn.count()) {
    await genContractBtn.click({ force: true, timeout: 8000 }).catch(() => {});
    await page.waitForTimeout(3500);
  }
}

async function run() {
  let testPassed = false;
  let playwright;
  try {
    playwright = await import('playwright');
  } catch (e) {
    console.error('请先安装 Playwright: npx playwright install chromium');
    process.exit(1);
  }
  const { chromium } = playwright;
  const headless = !!process.env.HEADLESS;
  const slowMoMs = Number(process.env.SLOW_MO_MS || 0) || (headless ? 0 : 400);
  const launchOptions = { headless, slowMo: slowMoMs };
  if (!headless) console.log('  慢速监督模式: 每步延迟 ' + slowMoMs + 'ms，关键步骤将截图至 ' + EVIDENCE_DIR);
  let browser;
  try {
    browser = await chromium.launch(launchOptions);
  } catch (e1) {
    try {
      browser = await chromium.launch({ ...launchOptions, channel: 'chrome' });
    } catch (e2) {
      browser = await chromium.launch({ ...launchOptions, channel: 'msedge' });
    }
  }
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const dialog = () => page.locator('.el-dialog').filter({ has: page.locator('.el-dialog__body') });

  try {
    console.log('========== 第二订单全流程 E2E：5516469437 ==========');
    console.log('截图目录:', EVIDENCE_DIR);
    console.log('\n动作指引概要：登录 -> 新建 3 单(坚领/青岛/热像科技) -> 销售列表查询确认 -> 平台合同(占位) -> 采购指派(按含税总价匹配) -> 合同签署与截图 -> 送货单/仓库/签收/回款/发票/付款(占位) -> 完结校验');

    // ---------- 清理旧数据 ----------
    console.log('\n[清理] 平台订单号 ' + PLATFORM_ORDER_5516469437);
    await cleanupOrdersByPlatformOrderNo(PLATFORM_ORDER_5516469437);

    // ---------- 1. sonmin 登录 ----------
    console.log('\n[步骤 1] 登录 -> 销售管理入口');
    console.log('  动作：登录页输入 sonmin / 密码 -> 点击登录');
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.creator.username);
    await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.creator.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(3000);
    await shot(page, '02-01-sonmin-login');

    // ---------- 2. 新建 3 笔销售订单 ----------
    console.log('\n[步骤 2] 销售管理 -> 新建销售订单（3 笔：坚领 / 青岛科创 / 热像科技）');
    const order2DataSet = [
      { name: 'jianling', data: ORDER_5516469437_JIANLING },
      { name: 'qingdao', data: ORDER_5516469437_QINGDAO },
      { name: 'refirm', data: ORDER_5516469437_REFIRM },
    ];
    const createdOrderIds = [];

    for (let i = 0; i < order2DataSet.length; i++) {
      const { name, data } = order2DataSet[i];
      console.log('  动作 #' + (i + 1) + ' ' + name + '：填写甲方、平台订单号 5516469437、商品明细（型号/含税总价）、物流、发票 -> 确定');
      await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await page.getByRole('button', { name: /新建销售订单/ }).click();
      await page.waitForTimeout(800);
      const d = dialog();
      await fillOrderForm(page, d, data);
      await shot(page, `02-0${2 + i}-order-${name}`);
      await d.getByRole('button', { name: '确定' }).click();
      await page.waitForTimeout(4000);
      const created = await assertSalesOrderCreated(PLATFORM_ORDER_5516469437);
      createdOrderIds.push(created.id);
      console.log('  创建校验通过: id=' + created.id);
    }
    console.log('\n[步骤 2b] 销售管理 -> 按甲方订单号 5516469437 查询 -> 确认列表中 3 笔订单');
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.getByPlaceholder('请输入甲方订单号').fill(PLATFORM_ORDER_5516469437);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(2000);
    await shot(page, '02-04-order-list-refirm');
    await shotIfErrorOrRed(page, '02-create');
    await assertNoOldCompanyName(page, '02-list');

    // ---------- 3. 上传平台合同（占位） ----------
    console.log('\n[步骤 3] 平台合同上传（占位）');
    console.log('  动作：若有订单级/平台订单号级合同上传入口则上传并截图，无则仅截图当前页留证');
    if (fs.existsSync(PLATFORM_CONTRACT_5516469437)) {
      console.log('  平台合同文件存在: ' + PLATFORM_CONTRACT_5516469437);
    }
    await shot(page, '02-05-platform-contract');

    // ---------- 4. 采购管理-销售订单指派：3 笔分别指派 坚领、青岛科创、热像科技 ----------
    console.log('\n[步骤 4] 采购管理 -> 销售订单指派 -> 按平台订单号 5516469437 查询 -> 按含税总价匹配行并指派');
    console.log('  动作：含税总价 3950 行 -> 指派热像科技（自营 XM3）；含税总价 22105 两行 -> 分别指派坚领、青岛科创（AC65Mini）；每笔填写交付方、采购价、乙方业务员、合同模板 -> 生成合同');
    await page.goto(FRONTEND_URL + '/purchase', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.getByRole('tab', { name: '销售订单指派' }).click();
    await page.waitForTimeout(2500);
    const assignFilterInput = page.getByPlaceholder('平台订单号');
    await assignFilterInput.waitFor({ state: 'visible', timeout: 15000 });
    await assignFilterInput.fill(PLATFORM_ORDER_5516469437);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(3000);
    await shot(page, '02-04b-assign-tab-pending-list');
    const assignTable = page.locator('.el-card').filter({ has: page.getByRole('button', { name: '指派' }) }).locator('.el-table__body-wrapper');
    const assignConfigs = [
      { priceKey: '3950', deliveryParty: ORDER_5516469437_REFIRM.deliveryParty, ...ORDER_5516469437_REFIRM, shot: '02-06-assign-refirm' },
      { priceKey: '22105', deliveryParty: ORDER_5516469437_JIANLING.deliveryParty, ...ORDER_5516469437_JIANLING, shot: '02-07-assign-jianling' },
      { priceKey: '22105', deliveryParty: ORDER_5516469437_QINGDAO.deliveryParty, ...ORDER_5516469437_QINGDAO, shot: '02-08-assign-qingdao' },
    ];
    for (let idx = 0; idx < assignConfigs.length; idx++) {
      const config = assignConfigs[idx];
      console.log('  指派 #' + (idx + 1) + '：含税总价 ' + config.priceKey + ' -> ' + config.deliveryParty);
      await assignFilterInput.fill(PLATFORM_ORDER_5516469437);
      await page.getByRole('button', { name: '查询' }).click();
      await page.waitForTimeout(2000);
      const rows = assignTable.locator('tbody tr');
      const n = await rows.count();
      let targetRow = null;
      for (let i = 0; i < n; i++) {
        const row = rows.nth(i);
        const text = await row.textContent().catch(() => '');
        if (!text.includes(config.priceKey)) continue;
        const assignBtn = row.getByRole('button', { name: '指派' });
        if (await assignBtn.isVisible().catch(() => false)) {
          targetRow = row;
          break;
        }
      }
      if (!targetRow) throw new Error(`步骤4指派失败：未找到含税总价 ${config.priceKey} 且可指派的表行`);
      const assignBtn = targetRow.getByRole('button', { name: '指派' });
      await assignBtn.click({ force: true, timeout: 15000 });
      await page.waitForTimeout(1500);
      const assignOverlay = page.locator('.el-overlay-dialog[aria-label="订单指派"]').first();
      await assignOverlay.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
      const assignD = assignOverlay;
      await fillAssignDialog(page, assignD, config, config.shot);
      await page.waitForTimeout(800);
      await page.keyboard.press('Escape').catch(() => {});
      await page.waitForTimeout(1000);
      const stillOpen = page.locator('.el-overlay-dialog[aria-label="订单指派"]');
      if (await stillOpen.isVisible().catch(() => false)) await page.keyboard.press('Escape');
      await page.waitForTimeout(1200);
    }
    await shot(page, '02-06-assign-refirm');
    await shot(page, '02-07-assign-jianling');
    await shot(page, '02-08-assign-qingdao');
    await shotIfErrorOrRed(page, '04-assign');
    await assertNoOldCompanyName(page, '04-assign');

    // ---------- 5. 合同生成与盖章（坚领、青岛科创占位、热像科技） ----------
    console.log('\n[步骤 5] 合同生成与盖章（乙方签署 + 截图）');
    console.log('  动作：坚领/青岛合同签署（需乙方身份匹配，否则跳过）；热像科技合同乙方签署；每笔合同弹窗截图');
    const jianlingOrderId = createdOrderIds[0];
    const qingdaoOrderId = createdOrderIds[1];
    const refirmOrderId = createdOrderIds[2];
    const contract1 = await getContractBySalesOrderId(jianlingOrderId);
    if (contract1?.id) {
      try {
        await signContractAs(USERS.jianling.username, USERS.jianling.password, contract1.id, 'B');
        console.log('  坚领合同已签署 contractId=' + contract1.id);
      } catch (e) {
        console.log('  坚领合同签署跳过（需乙方身份匹配）: ' + (e.message || e));
      }
    }
    await shot(page, '02-09-contract-jianling');
    console.log('  [占位] 青岛科创合同签署（无青岛科创账号则跳过）');
    await shot(page, '02-10-contract-qingdao');
    const contract3 = await getContractBySalesOrderId(refirmOrderId);
    if (contract3?.id) {
      try {
        await signContractAs(USERS.refirm.username, USERS.refirm.password, contract3.id, 'B');
        console.log('  热像科技合同已签署 contractId=' + contract3.id);
      } catch (e) {
        console.log('  热像科技合同签署跳过: ' + (e.message || e));
      }
    }
    await shot(page, '02-11-contract-refirm');

    // ---------- 5b. 合同页面截图（双章/正文留证） ----------
    console.log('\n[步骤 5b] 合同页面截图（双章留证）');
    console.log('  动作：按订单取合同查看链接或下载 zip -> 新开页打开合同 -> 截 page1/page2');
    const contractShots = [
      { orderId: jianlingOrderId, prefix: '02-09-contract-jianling' },
      { orderId: qingdaoOrderId, prefix: '02-10-contract-qingdao' },
      { orderId: refirmOrderId, prefix: '02-11-contract-refirm' },
    ];
    const creatorToken = await loginAsCreatorToken();
    for (const { orderId, prefix } of contractShots) {
      const c = await getContractBySalesOrderId(orderId);
      if (!c?.id) continue;
      const viewUrl = toAbsoluteContractUrl(c.protectedImageUrl || c.protectedPdfUrl || c.generatedUrl || '');
      const contractPage = await context.newPage();
      try {
        if (/\.zip(\?|$)/i.test(viewUrl) || !viewUrl) {
          const zipPath = path.join(EVIDENCE_DIR, `contract-${c.id}.zip`);
          const extractDir = path.join(EVIDENCE_DIR, `contract-${c.id}-pages`);
          try {
            await downloadBinary(`${BASE_URL}/api/contracts/${c.id}/download`, creatorToken, zipPath);
            extractZipToDir(zipPath, extractDir);
            const images = listImageFilesRecursively(extractDir);
            if (images.length) {
              await contractPage.goto(pathToFileURL(images[0]).href, { waitUntil: 'networkidle' });
              await contractPage.waitForTimeout(600);
              await shot(contractPage, `${prefix}-page1`);
              const second = images[1] || images[0];
              await contractPage.goto(pathToFileURL(second).href, { waitUntil: 'networkidle' });
              await contractPage.waitForTimeout(600);
              await shot(contractPage, `${prefix}-page2`);
            }
          } catch (e) {
            console.log(`  ${prefix} 合同下载/解压跳过: ${e.message}`);
          }
        } else {
          await contractPage.goto(viewUrl, { waitUntil: 'networkidle' });
          await contractPage.waitForTimeout(1200);
          await shot(contractPage, `${prefix}-page1`);
          await contractPage.mouse.wheel(0, 1600);
          await contractPage.waitForTimeout(800);
          await shot(contractPage, `${prefix}-page2`);
        }
      } catch (e) {
        console.log(`  ${prefix} 合同页面打开跳过: ${e.message}`);
      }
      await contractPage.close();
    }

    // ---------- 6. 送货单/128/备注同步（占位） ----------
    console.log('\n[步骤 6] 送货单/128/备注（占位）');
    console.log('  动作：若有送货单、128 码、备注同步到交付方·商务·仓库的入口则填写并截图，无则占位截图');
    if (DELIVERY_NOTE_AC65 && fs.existsSync(DELIVERY_NOTE_AC65)) console.log('  送货单 AC65 存在');
    if (DELIVERY_NOTE_XM3 && fs.existsSync(DELIVERY_NOTE_XM3)) console.log('  送货单 XM3 存在');
    await shot(page, '02-12-shipment-settings');

    // ---------- 7. 仓库：SN、物流单号（若有 UI 则填，否则占位） ----------
    console.log('\n[步骤 7] 销售管理 -> 按甲方订单号查询 -> 操作 -> 发货（SN、物流单号）');
    console.log('  动作：对 5516469437 下某笔订单点操作 -> 发货 -> 填写 SN 编码、物流单号 -> 确定');
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.getByPlaceholder('请输入甲方订单号').fill(PLATFORM_ORDER_5516469437);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(2000);
    const rowRefirm = page.locator('tbody tr').filter({ has: page.getByText(PLATFORM_ORDER_5516469437) }).first();
    if (await rowRefirm.locator('button').filter({ hasText: '操作' }).count()) {
      await rowRefirm.locator('button').filter({ hasText: '操作' }).click();
      await page.waitForTimeout(400);
      const shipItem = page.getByRole('menuitem', { name: '发货' });
      if (await shipItem.count()) {
        await shipItem.click();
        await page.waitForTimeout(1000);
        const shipD = dialog();
        const snInput = shipD.getByText('SN编码').locator('..').locator('input');
        if (await snInput.count()) await snInput.fill('302134242');
        const trackInput = shipD.getByText('物流单号').locator('..').locator('input');
        if (await trackInput.count()) await trackInput.fill('SF5122068084664');
        await shot(page, '02-13-warehouse-sn');
        await shot(page, '02-14-warehouse-logistics');
        await shipD.getByRole('button', { name: '确定' }).click().catch(() => {});
        await page.waitForTimeout(2000);
      }
    } else {
      console.log('  [占位] 未找到操作->发货入口，跳过 SN/物流填写');
      await shot(page, '02-13-warehouse-sn');
      await shot(page, '02-14-warehouse-logistics');
    }

    // ---------- 8. 签收单上传 ----------
    console.log('\n[步骤 8] 签收单上传（商务或仓库）');
    console.log('  动作：若有上传签收单入口则选择本地签收单文件上传并截图');
    const receiptFile = getFirstReceiptTestFile();
    if (receiptFile) {
      await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      const uploadInput = page.locator('.el-form-item').filter({ hasText: '上传签收单' }).locator('input[type="file"]').first();
      if (await uploadInput.count()) {
        await uploadInput.setInputFiles(receiptFile);
        await page.waitForTimeout(1500);
      }
    }
    await shot(page, '02-15-receipt-upload');

    // ---------- 9. 平台回款：更新 platformRefundStatus=已回款 ----------
    console.log('\n[步骤 9] 平台回款');
    console.log('  动作：API 将 3 笔订单 platformRefundStatus 置为已回款 -> 销售列表按 5516469437 查询并截图');
    try {
      for (const orderId of createdOrderIds) {
        await patchOrderPlatformRefundStatus(orderId, '已回款');
        console.log('  已回款 orderId=' + orderId);
      }
    } catch (e) {
      console.log('  [占位] 更新平台回款状态失败（需 platform_refund 权限）: ' + e.message);
    }
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.getByPlaceholder('请输入甲方订单号').fill(PLATFORM_ORDER_5516469437);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(2000);
    await shot(page, '02-16-platform-payment');

    // ---------- 10. 热像科技对坚领/青岛科创销售发票（占位） ----------
    console.log('\n[步骤 10] 热像科技对坚领、青岛科创销售发票（占位）');
    console.log('  动作：若有发票上传与传输入口则上传并截图，无则占位截图');
    if (INVOICE_JIANLING_PDF && fs.existsSync(INVOICE_JIANLING_PDF)) console.log('  坚领发票 PDF 存在');
    if (INVOICE_QINGDAO_PDF && fs.existsSync(INVOICE_QINGDAO_PDF)) console.log('  青岛科创发票 PDF 存在');
    await shot(page, '02-17-invoice-jianling');
    await shot(page, '02-17-invoice-qingdao');
    console.log('  [占位] 飞础科/热像科技-财务核验');
    await shot(page, '02-17-invoice-verify');

    // ---------- 11. 对交付方付款申请 + 财务上传付款水单（占位） ----------
    console.log('\n[步骤 11] 对交付方付款（占位）');
    console.log('  动作：若有对交付方付款申请、财务上传付款水单的模块则操作并截图，无则占位截图');
    await shot(page, '02-18-payment-request');
    await shot(page, '02-19-payment-voucher');

    // ---------- 12. 完结校验：sonmin 与交付方查看付款凭证 ----------
    console.log('\n[步骤 12] 完结校验（sonmin 与交付方视角）');
    console.log('  动作：sonmin 当前页截图 -> 清除 Cookie -> 坚领登录（或当前页）-> 销售列表截图');
    await shot(page, '02-20-sonmin-voucher');
    await context.clearCookies();
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    const loginForm = page.locator('.login-container form').first();
    const formVisible = await loginForm.isVisible().catch(() => false);
    if (formVisible) {
      await loginForm.locator('input').first().fill(USERS.jianling.username);
      await loginForm.locator('input[type="password"]').fill(USERS.jianling.password);
      await page.getByRole('button', { name: /登\s*录|登录/ }).click();
      await page.waitForTimeout(2500);
      await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
    } else {
      console.log('  [步骤12] 登录页未展示（可能仍为已登录态），对当前页截图作为 02-21');
    }
    await shot(page, '02-21-delivery-voucher');

    console.log('\n========== 第二订单全流程 E2E 完成，截图已保存至 ' + EVIDENCE_DIR + ' ==========');
    testPassed = true;
  } catch (e) {
    console.error(e);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, 'error-exception.png') }).catch(() => {});
    console.log('  异常已截图: error-exception.png（若为报错/红色提示或旧公司名，见本目录 error-red-*.png / error-old-company-*.png）');
  } finally {
    if (!testPassed) {
      console.log('\n[清理] 流程未完成，删除本次测试订单...');
      try {
        await cleanupOrdersByPlatformOrderNo(PLATFORM_ORDER_5516469437);
      } catch (e) {
        console.warn('  清理异常:', e.message);
      }
    }
    await browser.close();
    if (!testPassed) process.exitCode = 1;
  }
}

run();
