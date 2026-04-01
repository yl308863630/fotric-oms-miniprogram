/**
 * OMS 标准 E2E 测试：三账号指派与发货 + 关键截图
 * 流程：sonmin 新建订单 -> 采购指派坚领 -> 合同截图 -> 坚领转派热像科技 -> 热像科技发货
 * 运行：cd tests && npx playwright install chromium && node standard-flow-e2e.js
 * 环境：需先启动后端 (8080) 与前端 (3000)
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
  STANDARD_E2E_ORDER,
  STANDARD_E2E_ASSIGN,
  STANDARD_E2E_REASSIGN,
  STANDARD_E2E_SHIPMENT,
  STANDARD_E2E_WAREHOUSE,
  RECEIPT_TEST_FOLDER,
} = require('./config.js');

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

const EVIDENCE_BASE = path.join(__dirname, 'evidence', 'screenshots');
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
const EVIDENCE_DIR = path.join(EVIDENCE_BASE, `standard-${RUN_ID}`);
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

/** 若页面出现错误提示或红色校验文字则截图（el-message--error / el-form-item__error） */
async function shotIfErrorOrRed(page, stepLabel) {
  await page.waitForTimeout(600);
  const errMsg = page.locator('.el-message--error, .el-form-item__error, [class*="error"]').first();
  if (await errMsg.isVisible().catch(() => false)) {
    const name = `error-red-${stepLabel}.png`.replace(/[^a-z0-9.-]/gi, '-');
    await page.screenshot({ path: path.join(EVIDENCE_DIR, name) });
    console.log(`  [截图] 检测到报错/红色提示: ${name}`);
  }
}

/** 若页面出现旧公司名「上海坚领仪器有限公司」则截图并抛错 */
async function assertNoOldCompanyName(page, stepLabel) {
  const body = await page.locator('body').textContent().catch(() => '');
  if (body && body.includes('上海坚领仪器有限公司')) {
    const name = `error-old-company-${stepLabel}.png`.replace(/[^a-z0-9.-]/gi, '-');
    await page.screenshot({ path: path.join(EVIDENCE_DIR, name) });
    console.error(`  [失败] 页面仍出现「上海坚领仪器有限公司」，已截图: ${name}`);
    console.error('  可能来源：合同模板(.docx)内写死旧名称、或合作管理/历史合同数据。请检查模板中乙方处是否用占位符 ${partyBName}/${乙方}，及合作管理-用户信息维护中坚领关联公司抬头。');
    throw new Error('页面出现旧公司名：上海坚领仪器有限公司，已截图 ' + name);
  }
}

// 统一处理“可输入+可下拉”的选择框：直接输入并回车/失焦，不强依赖下拉点击
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

function calcDeductionPrice(basePrice, deductionRate) {
  const b = Number(basePrice || 0);
  const d = Number(deductionRate || 0);
  const v = b * (1 - d / 100);
  return Math.round(v * 100) / 100;
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

async function setOrderStatusToPendingShip(platformOrderNo) {
  const token = await loginAsCreatorToken();
  const listRes = await apiRequest(
    'GET',
    `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=1`,
    token
  );
  const list = listRes.content || listRes;
  const orders = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
  const order = orders.find((o) => o.platformOrderNo === platformOrderNo) || orders[0];
  if (!order || !order.id) throw new Error('Order not found for platformOrderNo: ' + platformOrderNo);
  await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${order.id}/status`, token, { status: '待发货' });
  return order.id;
}

// 新建后硬性校验：订单必须真实落库，否则后续指派/转派一定失败
async function assertSalesOrderCreated(platformOrderNo) {
  const token = await loginAsCreatorToken();

  const listRes = await apiRequest(
    'GET',
    `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=20`,
    token
  );
  const list = listRes.content || listRes;
  const orders = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
  const matched = orders.filter((o) => o.platformOrderNo === platformOrderNo);
  const order = matched.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0];
  if (!order || !order.id) {
    throw new Error(`创建校验失败：未找到平台订单号 ${platformOrderNo}（通常是商品信息未保存成功）`);
  }
  return order;
}

async function getLatestOrderByPlatformOrderNo(platformOrderNo) {
  const token = await loginAsCreatorToken();
  const listRes = await apiRequest(
    'GET',
    `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=50`,
    token
  );
  const list = listRes?.content || listRes;
  const orders = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
  const matched = orders.filter((o) => o.platformOrderNo === platformOrderNo);
  return matched.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0] || null;
}

async function patchOrderStatusById(orderId, status) {
  const token = await loginAsCreatorToken();
  await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${orderId}/status`, token, { status });
}

async function getContractBySalesOrderId(salesOrderId) {
  const token = await loginAsCreatorToken();
  return apiRequest('GET', `${BASE_URL}/api/contracts/sales-order/${salesOrderId}`, token);
}

async function getOrdersByPlatformOrderNoAsUser(username, password, platformOrderNo) {
  const token = await loginToken(username, password);
  const listRes = await apiRequest('GET', `${BASE_URL}/api/sales-orders?platformOrderNo=${encodeURIComponent(platformOrderNo)}&size=50`, token);
  const list = listRes?.content || listRes;
  const orders = Array.isArray(list) ? list : (list && list.content) ? list.content : [];
  return (orders || []).filter((o) => o.platformOrderNo === platformOrderNo);
}

async function getContractBySalesOrderIdAsUser(username, password, salesOrderId) {
  const token = await loginToken(username, password);
  return apiRequest('GET', `${BASE_URL}/api/contracts/sales-order/${salesOrderId}`, token);
}

async function signContractAs(username, password, contractId, partyType) {
  const token = await loginToken(username, password);
  return apiRequest('POST', `${BASE_URL}/api/contracts/${contractId}/sign`, token, { partyType });
}

function toAbsoluteContractUrl(urlPath) {
  if (!urlPath) return '';
  if (/^https?:\/\//i.test(urlPath)) return urlPath;
  if (urlPath.startsWith('/')) return `http://localhost:8080${urlPath}`;
  return `http://localhost:8080/${urlPath}`;
}

function downloadBinary(url, token, outputFile) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
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
      if (e.isDirectory()) {
        walk(full);
      } else if (/\.(png|jpg|jpeg|webp)$/i.test(e.name)) {
        out.push(full);
      }
    }
  };
  walk(rootDir);
  return out.sort();
}

async function cleanupOrdersByPlatformOrderNo(platformOrderNo) {
  const accounts = [
    USERS.creator,
    USERS.jianling,
    USERS.refirm,
    USERS.refirmWarehouse,
  ].filter(Boolean);

  let touched = 0;
  for (const acc of accounts) {
    let token;
    try {
      token = await loginToken(acc.username, acc.password);
    } catch (e) {
      console.warn(`  清理：账号登录失败 ${acc.username} -> ${e.message}`);
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
      console.warn(`  清理：查询失败 ${acc.username} -> ${e.message}`);
      continue;
    }

    const sorted = orders.sort((a, b) => Number(b.id || 0) - Number(a.id || 0));
    for (const o of sorted) {
      try {
        if (o.status === '已退回') continue;
        // 优先走退回接口（会清理指派信息并删关联合同），失败再兜底改状态
        try {
          await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${o.id}/return`, token, {
            returnReason: 'E2E自动清理：流程未跑通',
          });
          console.log(`  清理：已退回订单 id=${o.id}, platformOrderNo=${platformOrderNo}, by=${acc.username}`);
        } catch {
          await apiRequest('PATCH', `${BASE_URL}/api/sales-orders/${o.id}/status`, token, { status: '已退回' });
          console.log(`  清理：已标记已退回 id=${o.id}, platformOrderNo=${platformOrderNo}, by=${acc.username}`);
        }
        touched += 1;
      } catch (e) {
        console.warn(`  清理：处理失败 id=${o.id}, by=${acc.username} -> ${e.message}`);
      }
    }
  }
  if (touched === 0) {
    console.log(`  清理：未找到可处理的 ${platformOrderNo} 订单`);
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
      try {
        browser = await chromium.launch({ ...launchOptions, channel: 'msedge' });
      } catch (e3) {
        console.error('无法启动浏览器。请执行: npx playwright install chromium');
        process.exit(1);
      }
    }
  }

  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  const dialog = () => page.locator('.el-dialog').filter({ has: page.locator('.el-dialog__body') });
  const assignDialog = () => page.locator('.el-dialog').filter({ has: page.locator('.el-dialog__body') }).filter({ hasText: '交付方' }).last();

  try {
    console.log('========== OMS 标准 E2E：三账号指派与发货 ==========');
    console.log('截图目录:', EVIDENCE_DIR);
    console.log('前端:', FRONTEND_URL);

    // ---------- 1. sonmin 登录 ----------
    console.log('\n[步骤 1] sonmin 登录');
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.creator.username);
    await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.creator.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(3000);
    await shot(page, '01-sonmin-login');

    // ---------- 2. 新建销售订单 ----------
    console.log('\n[步骤 2] 销售管理 -> 新建销售订单');
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.getByRole('button', { name: /新建销售订单/ }).click();
    await page.waitForTimeout(800);

    const d = dialog();
    // 甲方抬头（el-select allow-create）：输入后点击下一格触发 blur 自动提交，无需点选下拉
    const platformInput = d.locator('input').first();
    await platformInput.click();
    await platformInput.fill(STANDARD_E2E_ORDER.platformName);
    await page.waitForTimeout(400);
    const orderNoInput = d.locator('input').nth(1);
    await orderNoInput.click(); // blur 甲方，触发自动提交到单元格
    await page.waitForTimeout(300);
    // 甲方订单号
    await orderNoInput.fill(STANDARD_E2E_ORDER.platformOrderNo);
    await page.waitForTimeout(200);
    // 业务员：由当前登录账号自动带出真实姓名，无需选择
    // 订单类型：默认第三方订单，直接往下执行
    // 商品明细：按列填写，避免把含税单价误填到平台SKU导致出现 39 等错误（列序：序号/操作/订单类型/OMS/平台SKU/型号/产品配置/保修期/数量/含税单价/含税总价）
    await d.getByRole('tab', { name: '商品明细' }).click();
    await page.waitForTimeout(300);
    const productTable = d.locator('.el-table').first();
    const firstRow = productTable.locator('tbody tr').first();
    if (STANDARD_E2E_ORDER.platformSku) {
      await firstRow.locator('td').nth(4).locator('input').fill(STANDARD_E2E_ORDER.platformSku);
      await page.waitForTimeout(150);
    }
    const modelCell = firstRow.locator('td').nth(5);
    await fillSelectByTyping(page, modelCell, STANDARD_E2E_ORDER.model);
    await page.waitForTimeout(200);
    if (STANDARD_E2E_ORDER.productConfig) await firstRow.locator('td').nth(6).locator('input').fill(STANDARD_E2E_ORDER.productConfig);
    if (STANDARD_E2E_ORDER.warrantyPeriod) await firstRow.locator('td').nth(7).locator('input').fill(STANDARD_E2E_ORDER.warrantyPeriod);
    await firstRow.locator('td').nth(9).locator('input').fill(String(STANDARD_E2E_ORDER.taxIncludedPrice));
    await page.waitForTimeout(200);
    // 物流信息：按列填写，不填发货数量（列序：序号/操作/商品信息/发货数量/收货人/电话/收货地址/交货日期）
    await d.getByRole('tab', { name: '物流信息' }).click();
    await page.waitForTimeout(300);
    const logRow = STANDARD_E2E_ORDER.logistics[0];
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
    // 发票信息
    await d.getByRole('tab', { name: '发票信息' }).click();
    await page.waitForTimeout(300);
    const invoiceTable = d.locator('.el-table').nth(2);
    const invoiceFirstRow = invoiceTable.locator('tbody tr').first();
    const invoiceTitleCell = invoiceFirstRow.locator('td').nth(3);
    await fillSelectByTyping(page, invoiceTitleCell, STANDARD_E2E_ORDER.invoice.invoiceTitle || STANDARD_E2E_ORDER.platformName);
    const payCell = invoiceFirstRow.locator('td').nth(5);
    await fillSelectByTyping(page, payCell, STANDARD_E2E_ORDER.invoice.paymentMethod);
    await page.waitForTimeout(300);

    await shot(page, '02-new-order-form-filled');
    await d.getByRole('button', { name: '确定' }).click();
    await page.waitForTimeout(4000);
    await shot(page, '03-sales-list-after-create');
    await shotIfErrorOrRed(page, '02-create');
    await assertNoOldCompanyName(page, '02-create');
    // 页面可见性 + API 落库双校验，确保 5324435 真实创建成功
    await page.getByText(STANDARD_E2E_ORDER.platformOrderNo).first().waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
    const createdOrder = await assertSalesOrderCreated(STANDARD_E2E_ORDER.platformOrderNo);
    console.log(`  创建校验通过: ${createdOrder.platformOrderNo}, id=${createdOrder.id}, status=${createdOrder.status}`);
    if (createdOrder.status !== '待指派') {
      await patchOrderStatusById(createdOrder.id, '待指派');
      console.log(`  状态修正: 订单 ${createdOrder.id} -> 待指派`);
    }

    // ---------- 2b. 采购管理-销售订单指派：检查是否生成待指派订单行 ----------
    console.log('\n[步骤 2b] 采购管理 -> 销售订单指派 -> 搜索待指派订单行');
    await page.goto(FRONTEND_URL + '/purchase', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await page.getByRole('tab', { name: '销售订单指派' }).click();
    await page.waitForTimeout(1200);
    const assignFilterInput = page.locator('.el-form-item').filter({ hasText: '平台订单号' }).locator('input').first();
    await assignFilterInput.fill(STANDARD_E2E_ORDER.platformOrderNo);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(3000);
    await shot(page, '02b-assign-tab-pending-row');
    const assignTableForCheck = page.locator('.el-card').filter({ has: page.getByRole('button', { name: '指派' }) }).locator('.el-table__body-wrapper');
    const pendingRow = assignTableForCheck.locator('tbody tr').filter({ hasText: STANDARD_E2E_ORDER.platformOrderNo }).first();
    const hasPendingRow = await pendingRow.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasPendingRow) throw new Error('步骤2b失败：采购管理-销售订单指派中未找到待指派订单行（平台订单号 ' + STANDARD_E2E_ORDER.platformOrderNo + '）');
    console.log('  待指派订单行已生成，截图: 02b-assign-tab-pending-row.png');

    // ---------- 3. 采购管理 -> 销售订单指派 -> 指派坚领 ----------
    console.log('\n[步骤 3] 采购管理 -> 销售订单指派 -> 指派坚领');
    await page.goto(FRONTEND_URL + '/purchase', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await page.getByRole('tab', { name: '销售订单指派' }).click();
    await page.waitForTimeout(1200);
    // 先按平台订单号过滤，确保命中刚创建的订单
    const assignFilterInput2 = page.locator('.el-form-item').filter({ hasText: '平台订单号' }).locator('input').first();
    await assignFilterInput2.fill(STANDARD_E2E_ORDER.platformOrderNo);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(3000);
    await shot(page, '03b-assign-tab-table');
    // 指派表格在「销售订单指派」tab 的第二个 card 内，限定在该表格中找行或指派按钮
    const assignTable = page.locator('.el-card').filter({ has: page.getByRole('button', { name: '指派' }) }).locator('.el-table__body-wrapper');
    const rowWithOrder = assignTable.locator('tbody tr').filter({ hasText: STANDARD_E2E_ORDER.platformOrderNo }).first();
    await rowWithOrder.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
    const assignBtnInRow = rowWithOrder.getByRole('button', { name: '指派' });
    if (await assignBtnInRow.count() > 0) {
      await assignBtnInRow.scrollIntoViewIfNeeded();
      await assignBtnInRow.click({ force: true });
    } else {
      const firstAssign = assignTable.getByRole('button', { name: '指派' }).first();
      await firstAssign.waitFor({ state: 'visible', timeout: 10000 });
      await firstAssign.scrollIntoViewIfNeeded();
      await firstAssign.click({ force: true });
    }
    await page.waitForTimeout(1500);

    // 严格判定：点击指派后必须弹窗；若无弹窗，立即终止测试
    const assignOverlay = page.locator('.el-overlay-dialog[aria-label="订单指派"]').first();
    const dialogVisible = await assignOverlay.waitFor({ state: 'visible', timeout: 8000 }).then(() => true).catch(() => false);
    if (!dialogVisible) {
      throw new Error('步骤3失败：点击指派后未弹出指派窗口，终止测试。');
    }
    const assignD = assignOverlay;

    const deliveryInput = assignD.getByPlaceholder('交付方（下一级采购方/乙方）');
    await deliveryInput.fill(STANDARD_E2E_ASSIGN.deliveryParty, { force: true });
      const partyATitleInput = assignD.locator('.el-form-item').filter({ hasText: '甲方抬头' }).locator('input').first();
      if (await partyATitleInput.count()) {
        await partyATitleInput.fill(STANDARD_E2E_ASSIGN.platformName || STANDARD_E2E_ORDER.platformName, { force: true });
      }
      const assignPrice = STANDARD_E2E_ASSIGN.useDeductionPrice
        ? calcDeductionPrice(STANDARD_E2E_ORDER.taxIncludedPrice, STANDARD_E2E_ASSIGN.deductionRate ?? 0)
        : STANDARD_E2E_ASSIGN.deliveryPartyPurchasePrice;
      const deductionInput = assignD.locator('.el-form-item').filter({ hasText: '扣点' }).locator('input').first();
      if (await deductionInput.count() && STANDARD_E2E_ASSIGN.deductionRate != null) {
        await deductionInput.fill(String(STANDARD_E2E_ASSIGN.deductionRate), { force: true });
      }
      const priceInputAssign = assignD.getByPlaceholder(/A的采购价|交付方采购价/).first();
      await priceInputAssign.fill(String(assignPrice), { force: true });
      await page.waitForTimeout(200);
      // 乙方业务员：按你的要求直接输入袁星辉，不依赖下拉可选项
      const partyBSelect = assignD.getByText('乙方业务员').locator('..').locator('.el-select');
      await partyBSelect.waitFor({ state: 'visible', timeout: 8000 });
      await fillSelectByTyping(page, partyBSelect, STANDARD_E2E_ASSIGN.partyBRepresentative || '袁星辉');

      // 合同模板：按你的要求直接输入模板名，不依赖下拉项
      const templateSelect = assignD.getByText('合同模板').locator('..').locator('.el-select');
      await templateSelect.waitFor({ state: 'visible', timeout: 8000 });
      await fillSelectByTyping(page, templateSelect, STANDARD_E2E_ASSIGN.templateName || 'FOTRIC电商扣点派单采购合同');
      await page.waitForTimeout(200);
      await shot(page, '04-assign-form-jianling');
      try {
        const genContractBtn = page.locator('button').filter({ hasText: '生成合同' }).first();
        await genContractBtn.click({ force: true, timeout: 8000 });
        await page.waitForTimeout(3500);
      } catch (e) {
        console.warn('  点击生成合同未成功（弹窗可能被判定为不可见）:', e.message);
        await page.keyboard.press('Escape');
        await page.waitForTimeout(500);
      }
    await shot(page, '05-after-assign');
    await shotIfErrorOrRed(page, '03-assign');
    await assertNoOldCompanyName(page, '03-assign');
    const assignedOrder = await getLatestOrderByPlatformOrderNo(STANDARD_E2E_ORDER.platformOrderNo);
    if (!assignedOrder) throw new Error('步骤3失败：指派后查不到订单');
    if (assignedOrder.assignedUsername !== USERS.jianling.username) {
      throw new Error(
        `步骤3失败：订单未指派到坚领。当前 assignedUsername=${assignedOrder.assignedUsername || '空'}，期望=${USERS.jianling.username}`
      );
    }
    console.log(`  指派校验通过: assignedUsername=${assignedOrder.assignedUsername}, status=${assignedOrder.status}`);

    // ---------- 3b. sonmin 指派后：采购管理-采购订单 检查是否生成采购订单行 ----------
    console.log('\n[步骤 3b] 采购管理 -> 采购订单 -> 检查 sonmin 采购订单行');
    await page.getByRole('tab', { name: '采购订单' }).click();
    await page.waitForTimeout(2500);
    await shot(page, '05b-sonmin-purchase-orders');
    const purchaseTableSonmin = page.locator('.el-card').filter({ has: page.getByText('采购订单号') }).locator('.el-table__body-wrapper').first();
    const poRowsSonmin = purchaseTableSonmin.locator('tbody tr');
    const poCountSonmin = await poRowsSonmin.count();
    const firstRowTextSonmin = poCountSonmin > 0 ? await poRowsSonmin.first().textContent().catch(() => '') : '';
    const hasPoSonmin = poCountSonmin > 0 && !firstRowTextSonmin.includes('暂无数据');
    if (!hasPoSonmin) throw new Error('步骤3b失败：sonmin 指派后采购管理-采购订单中未生成采购订单行');
    console.log('  sonmin 采购订单行已生成，截图: 05b-sonmin-purchase-orders.png');

    // ---------- 4. 合同签署与下载（乙方签署 + 两页截图） ----------
    console.log('\n[步骤 4] 合同签署与下载（乙方签署 + 两页截图）');
    const contract = await getContractBySalesOrderId(createdOrder.id);
    if (!contract?.id) {
      throw new Error(`步骤4失败：未找到销售单 ${createdOrder.id} 的合同`);
    }
    await shot(page, '06-contract-dialog');
    // 乙方（被指派方）签署
    await signContractAs(USERS.jianling.username, USERS.jianling.password, contract.id, 'B');
    // 重新获取合同，取可查看链接
    const signedContract = await getContractBySalesOrderId(createdOrder.id);
    const viewUrl = toAbsoluteContractUrl(
      signedContract?.protectedImageUrl || signedContract?.protectedPdfUrl || signedContract?.generatedUrl || ''
    );
    if (!viewUrl) {
      throw new Error('步骤4失败：合同签署后无可查看链接');
    }
    const contractPage = await context.newPage();
    if (/\.zip(\?|$)/i.test(viewUrl)) {
      // 合同为多页图片压缩包：先下载，再解压后逐页截图
      const creatorToken = await loginAsCreatorToken();
      const zipPath = path.join(EVIDENCE_DIR, `contract-${contract.id}.zip`);
      const extractDir = path.join(EVIDENCE_DIR, `contract-${contract.id}-pages`);
      await downloadBinary(`${BASE_URL}/api/contracts/${contract.id}/download`, creatorToken, zipPath);
      extractZipToDir(zipPath, extractDir);
      const images = listImageFilesRecursively(extractDir);
      if (!images.length) {
        throw new Error('步骤4失败：合同zip解压后未找到图片页');
      }
      await contractPage.goto(pathToFileURL(images[0]).href, { waitUntil: 'networkidle' });
      await contractPage.waitForTimeout(600);
      await shot(contractPage, '06-contract-page1');
      const second = images[1] || images[0];
      await contractPage.goto(pathToFileURL(second).href, { waitUntil: 'networkidle' });
      await contractPage.waitForTimeout(600);
      await shot(contractPage, '06-contract-page2');
    } else {
      await contractPage.goto(viewUrl, { waitUntil: 'networkidle' });
      await contractPage.waitForTimeout(1200);
      await shot(contractPage, '06-contract-page1');
      await contractPage.mouse.wheel(0, 1600);
      await contractPage.waitForTimeout(800);
      await shot(contractPage, '06-contract-page2');
    }
    await contractPage.close();

    // ---------- 5. API 将订单改为待发货 ----------
    console.log('\n[步骤 5] API 将订单状态改为待发货');
    try {
      await setOrderStatusToPendingShip(STANDARD_E2E_ORDER.platformOrderNo);
      console.log('  已设为待发货');
    } catch (e) {
      console.warn('  设置待发货失败（可手动改状态后继续）:', e.message);
    }

    // ---------- 6. 坚领登录、销售列表、转派热像科技 ----------
    console.log('\n[步骤 6] 坚领登录 -> 销售列表 -> 转派热像科技');
    await context.clearCookies();
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.reload({ waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.jianling.username);
    await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.jianling.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(2500);
    await shot(page, '07-jianling-login');

    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await shot(page, '08-jianling-sales-list');
    await assertNoOldCompanyName(page, '06-jianling-list');
    await page.getByText(STANDARD_E2E_ORDER.platformOrderNo).first().waitFor({ state: 'visible', timeout: 12000 });

    await page.goto(FRONTEND_URL + '/purchase', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await page.getByRole('tab', { name: '销售订单指派' }).click();
    await page.waitForTimeout(800);
    await page.locator('.el-form-item').filter({ hasText: '平台订单号' }).locator('input').first().fill(STANDARD_E2E_ORDER.platformOrderNo);
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForTimeout(2000);
    {
      const reassignBtn = page.getByRole('button', { name: '转派' }).first();
      const assignBtn = page.getByRole('button', { name: '指派' }).first();
      const hasReassign = await reassignBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasReassign) {
        await reassignBtn.click();
      } else {
        const hasAssign = await assignBtn.isVisible({ timeout: 12000 }).catch(() => false);
        if (!hasAssign) {
          throw new Error('步骤6失败：坚领账号未出现“转派/指派”按钮，无法继续下游发货流程');
        }
        await assignBtn.click();
      }
      await page.waitForTimeout(600);
      const reassignD = page.locator('.el-overlay-dialog[aria-label="订单指派"]:visible').first();
      await reassignD.waitFor({ state: 'visible', timeout: 8000 });
      const deliveryInputRe = reassignD.getByPlaceholder(/交付方.*乙方|交付方/).first();
      if (await deliveryInputRe.count()) {
        await deliveryInputRe.fill(STANDARD_E2E_REASSIGN.deliveryParty, { force: true });
      } else {
        const deliverySelectRe = reassignD.locator('.el-form-item').filter({ hasText: '交付方' }).locator('.el-select').first();
        await fillSelectByTyping(page, deliverySelectRe, STANDARD_E2E_REASSIGN.deliveryParty);
      }
      const partyATitleReInput = reassignD.locator('.el-form-item').filter({ hasText: '甲方抬头' }).locator('input').first();
      if (await partyATitleReInput.count()) {
        await partyATitleReInput.fill(STANDARD_E2E_REASSIGN.platformName, { force: true });
      }
      const partyBSelectRe = reassignD.getByText('乙方业务员').locator('..').locator('.el-select');
      if (await partyBSelectRe.count()) {
        await fillSelectByTyping(page, partyBSelectRe, STANDARD_E2E_REASSIGN.partyBRepresentative || '商务');
      }
      const priceInputRe = reassignD.getByPlaceholder(/A的采购价|交付方采购价/).first();
      await priceInputRe.fill(String(STANDARD_E2E_REASSIGN.deliveryPartyPurchasePrice), { force: true });
      await page.waitForTimeout(200);
      if (STANDARD_E2E_REASSIGN.paymentMethod) {
        const paySelectRe = reassignD.getByText('支付方式').locator('..').locator('.el-select');
        if (await paySelectRe.count()) await fillSelectByTyping(page, paySelectRe, STANDARD_E2E_REASSIGN.paymentMethod);
      }
      await page.waitForTimeout(200);
      const reassignTemplate = reassignD.getByText('合同模板').locator('..').locator('.el-select');
      if (await reassignTemplate.count()) {
        await fillSelectByTyping(page, reassignTemplate, STANDARD_E2E_REASSIGN.templateName || 'FOTRIC电商扣点派单采购合同');
      }
      await shot(page, '09-reassign-form-refirm');
      const genRe = page.locator('button').filter({ hasText: '生成合同' }).first();
      if (await genRe.count()) await genRe.click({ force: true });
      await page.waitForTimeout(3500);
    }
    await shot(page, '10-after-reassign');
    await shotIfErrorOrRed(page, '06-reassign');
    await assertNoOldCompanyName(page, '06-reassign');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(800);
    const assignDialogAfterReassign = page.locator('.el-overlay-dialog[aria-label="订单指派"]');
    if (await assignDialogAfterReassign.isVisible().catch(() => false)) {
      await page.keyboard.press('Escape');
      await page.waitForTimeout(500);
    }

    // ---------- 6a. 坚领指派后：采购管理-采购订单 检查是否生成采购订单行 ----------
    console.log('\n[步骤 6a] 坚领指派后 -> 采购管理 -> 采购订单 -> 检查坚领采购订单行');
    await page.getByRole('tab', { name: '采购订单' }).click();
    await page.waitForTimeout(2500);
    await shot(page, '10c-jianling-purchase-orders');
    const purchaseTableJl = page.locator('.el-card').filter({ has: page.getByText('采购订单号') }).locator('.el-table__body-wrapper').first();
    const poRowsJl = purchaseTableJl.locator('tbody tr');
    const poCountJl = await poRowsJl.count();
    const firstRowTextJl = poCountJl > 0 ? await poRowsJl.first().textContent().catch(() => '') : '';
    const hasPoJl = poCountJl > 0 && !firstRowTextJl.includes('暂无数据');
    if (!hasPoJl) throw new Error('步骤6a失败：坚领指派后采购管理-采购订单中未生成采购订单行');
    console.log('  坚领采购订单行已生成，截图: 10c-jianling-purchase-orders.png');

    // ---------- 6b. 坚领-热像科技双章合同（商务合同）：乙方签署 + 两页截图 ----------
    console.log('\n[步骤 6b] 坚领-热像科技合同（商务合同）签署与两页截图');
    await page.waitForTimeout(2000);
    try {
      const jianlingOrders = await getOrdersByPlatformOrderNoAsUser(USERS.jianling.username, USERS.jianling.password, STANDARD_E2E_ORDER.platformOrderNo);
      if (jianlingOrders.length === 0) {
        console.warn('  步骤6b：坚领账号下未查到平台订单号 ' + STANDARD_E2E_ORDER.platformOrderNo + '，尝试用创建人 token 查该单');
      } else {
        console.log('  步骤6b：坚领账号下查到 ' + jianlingOrders.length + ' 条订单，deliveryParty: ' + jianlingOrders.map((o) => o.deliveryParty).join(', '));
      }
      let reassignOrder = jianlingOrders.find((o) => (o.deliveryParty || '').indexOf('热像科技') >= 0 || (o.deliveryParty || '') === (STANDARD_E2E_REASSIGN.deliveryParty || ''));
      if (!reassignOrder && jianlingOrders.length > 0) {
        reassignOrder = jianlingOrders.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0];
        console.log('  步骤6b：按交付方未命中热像科技，取最新一条订单 id=' + reassignOrder.id + ' deliveryParty=' + reassignOrder.deliveryParty);
      }
      if (reassignOrder && reassignOrder.id) {
        const contract2 = await getContractBySalesOrderIdAsUser(USERS.jianling.username, USERS.jianling.password, reassignOrder.id);
        if (contract2 && contract2.id) {
          console.log('  商务合同: contractId=' + contract2.id + ' salesOrderId=' + reassignOrder.id + ' partyA=' + (contract2.partyAName || '') + ' partyB=' + (contract2.partyBName || ''));
          await signContractAs(USERS.refirm.username, USERS.refirm.password, contract2.id, 'B');
          const signed2 = await getContractBySalesOrderIdAsUser(USERS.jianling.username, USERS.jianling.password, reassignOrder.id);
          const viewUrl2 = toAbsoluteContractUrl(signed2?.protectedImageUrl || signed2?.protectedPdfUrl || signed2?.generatedUrl || '');
          if (viewUrl2) {
            const contractPage2 = await context.newPage();
            if (/\.zip(\?|$)/i.test(viewUrl2)) {
              const jianlingToken = await loginToken(USERS.jianling.username, USERS.jianling.password);
              const zipPath2 = path.join(EVIDENCE_DIR, `contract2-${contract2.id}.zip`);
              const extractDir2 = path.join(EVIDENCE_DIR, `contract2-${contract2.id}-pages`);
              await downloadBinary(`${BASE_URL}/api/contracts/${contract2.id}/download`, jianlingToken, zipPath2);
              extractZipToDir(zipPath2, extractDir2);
              const images2 = listImageFilesRecursively(extractDir2);
              if (images2.length) {
                await contractPage2.goto(pathToFileURL(images2[0]).href, { waitUntil: 'networkidle' });
                await contractPage2.waitForTimeout(600);
                await shot(contractPage2, '10b-contract-jianling-refirm-page1');
                await contractPage2.goto(pathToFileURL(images2[1] || images2[0]).href, { waitUntil: 'networkidle' });
                await contractPage2.waitForTimeout(600);
                await shot(contractPage2, '10b-contract-jianling-refirm-page2');
              }
            } else {
              await contractPage2.goto(viewUrl2, { waitUntil: 'networkidle' });
              await contractPage2.waitForTimeout(1200);
              await shot(contractPage2, '10b-contract-jianling-refirm-page1');
              await contractPage2.mouse.wheel(0, 1600);
              await contractPage2.waitForTimeout(800);
              await shot(contractPage2, '10b-contract-jianling-refirm-page2');
            }
            await contractPage2.close();
            console.log('  坚领-热像科技双章合同截图: 10b-contract-jianling-refirm-page1/2');
          }
        }
      } else {
        console.warn('  步骤6b跳过：未找到坚领转派热像科技的订单');
      }
    } catch (e) {
      console.warn('  步骤6b坚领-热像科技合同截图失败:', e.message);
    }

    // ---------- 7. 热像科技-商务登录 -> 发货 ----------
    console.log('\n[步骤 7] 热像科技-商务登录 -> 发货信息维护');
    await context.clearCookies();
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.reload({ waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.refirm.username);
    await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.refirm.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(2500);
    await shot(page, '11-refirm-login');

    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await shot(page, '12-refirm-sales-list');
    await assertNoOldCompanyName(page, '07-refirm-list');
    await page.getByText(STANDARD_E2E_ORDER.platformOrderNo).first().waitFor({ state: 'visible', timeout: 12000 });

    const rowRefirm = page.locator('tbody tr').filter({ has: page.getByText(STANDARD_E2E_ORDER.platformOrderNo) }).first();
    await rowRefirm.locator('button').filter({ hasText: '操作' }).waitFor({ state: 'visible', timeout: 10000 });
    await rowRefirm.locator('button').filter({ hasText: '操作' }).click();
      await page.waitForTimeout(400);
      await page.getByRole('menuitem', { name: '发货' }).click();
      await page.waitForTimeout(1000);

      const shipD = dialog();
      const deliverySelect = shipD.getByText('配送方式').locator('..').locator('.el-select');
      if (await deliverySelect.count()) {
        try {
          await deliverySelect.click({ force: true });
          await page.waitForTimeout(300);
          const methodOpt = page.locator('.el-select-dropdown').last().locator('.el-select-option').filter({ hasText: STANDARD_E2E_SHIPMENT.deliveryMethod }).first();
          if (await methodOpt.count()) await methodOpt.click({ force: true });
          else await fillSelectByTyping(page, deliverySelect, STANDARD_E2E_SHIPMENT.deliveryMethod);
        } catch {
          // 失败则使用页面默认配送方式继续
        }
      }
      await page.waitForTimeout(300);
      const companySelect = shipD.getByText('物流公司').locator('..').locator('.el-select');
      if (await companySelect.count()) {
        try {
          await fillSelectByTyping(page, companySelect, STANDARD_E2E_SHIPMENT.logisticsCompany);
        } catch {
          await companySelect.click({ force: true });
          await page.waitForTimeout(300);
          const sf = page.locator('.el-select-dropdown').last().locator('.el-select-option').filter({ hasText: STANDARD_E2E_SHIPMENT.logisticsCompany }).first();
          if (await sf.count()) await sf.click({ force: true });
        }
      }
      const trackInput = shipD.getByText('物流单号').locator('..').locator('input');
      if (await trackInput.count()) await trackInput.fill(STANDARD_E2E_SHIPMENT.trackingNumber);
      if (STANDARD_E2E_SHIPMENT.printBarcode128) {
        const barcodeSwitch = shipD.getByText('打印128条形码').locator('..').locator('.el-switch');
        if (await barcodeSwitch.count()) await barcodeSwitch.click();
      }
      const printQty = shipD.getByText('送货单打印数量').locator('..').locator('.el-input-number input');
      if (await printQty.count()) await printQty.fill(String(STANDARD_E2E_SHIPMENT.printQuantity));
      const shipAddr = shipD.getByText('发货地址').locator('..').locator('textarea, input');
      if (await shipAddr.count()) await shipAddr.fill('上海青浦');
      const receiptSwitchRefirm = shipD.getByText('需要签收单回传').locator('..').locator('.el-switch');
      if (await receiptSwitchRefirm.count()) await receiptSwitchRefirm.click();
      await page.waitForTimeout(600);
      const receiptFileRefirm = getFirstReceiptTestFile();
      if (receiptFileRefirm) {
        const receiptInputRefirm = shipD.locator('.el-form-item').filter({ hasText: '上传签收单' }).locator('input[type="file"]');
        if (await receiptInputRefirm.count()) {
          await receiptInputRefirm.setInputFiles(receiptFileRefirm);
          await page.waitForTimeout(1500);
        }
      }
      await page.waitForTimeout(500);
      await shot(page, '13-shipment-form');
      await shot(page, '14-barcode128');
      await shipD.getByRole('button', { name: '确定' }).click();
      await page.waitForTimeout(2500);
    await shot(page, '15-after-shipment');
    await shotIfErrorOrRed(page, '07-shipment');

    // ---------- 8. 热像科技-仓库：发货准备 / SN / 签收单（商务上传 or 仓库上传）----------
    if (USERS.refirmWarehouse && STANDARD_E2E_WAREHOUSE) {
      console.log('\n[步骤 8] 热像科技-仓库登录 -> 销售列表 -> 发货(SN/签收单)');
      await context.clearCookies();
      await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
      await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
      await page.reload({ waitUntil: 'networkidle' });
      await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.refirmWarehouse.username);
      await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.refirmWarehouse.password);
      await page.getByRole('button', { name: /登\s*录|登录/ }).click();
      await page.waitForTimeout(2500);
      await shot(page, '16-warehouse-login');

      await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await shot(page, '17-warehouse-sales-list');

      const rowWarehouse = page.locator('tr').filter({ has: page.getByText(STANDARD_E2E_ORDER.platformOrderNo) }).first();
      if (await rowWarehouse.count()) {
        await rowWarehouse.locator('button').filter({ hasText: '操作' }).click();
        await page.waitForTimeout(400);
        const shipmentItem = page.getByRole('menuitem', { name: '发货' });
        if (await shipmentItem.count()) {
          await shipmentItem.click();
          await page.waitForTimeout(1000);
          const shipW = dialog();
          const snInput = shipW.getByText('SN编码').locator('..').locator('input');
          if (await snInput.count()) await snInput.fill(STANDARD_E2E_WAREHOUSE.snCode || '2701005927');
          if (STANDARD_E2E_WAREHOUSE.needReceiptReturn) {
            const receiptSwitch = shipW.getByText('需要签收单回传').locator('..').locator('.el-switch');
            if (await receiptSwitch.count()) await receiptSwitch.click();
            await page.waitForTimeout(600);
            const receiptFile = getFirstReceiptTestFile();
            if (receiptFile) {
              const receiptInput = shipW.locator('.el-form-item').filter({ hasText: '上传签收单' }).locator('input[type="file"]');
              if (await receiptInput.count()) {
                await receiptInput.setInputFiles(receiptFile);
                await page.waitForTimeout(1500);
              }
            }
          }
          await page.waitForTimeout(500);
          await shot(page, '18-warehouse-shipment-sn-receipt');
        }
      }
    }

    console.log('\n========== 标准 E2E 完成，截图已保存至 ' + EVIDENCE_DIR + ' ==========');
    testPassed = true;
  } catch (e) {
    console.error(e);
    await page.screenshot({ path: path.join(EVIDENCE_DIR, 'error-exception.png') }).catch(() => {});
    console.log('  异常已截图: error-exception.png（若为报错/红色提示或旧公司名，见本目录 error-red-*.png / error-old-company-*.png）');
  } finally {
    if (!testPassed) {
      console.log('\n[清理] 流程未跑通，开始删除本次测试订单数据...');
      try {
        await cleanupOrdersByPlatformOrderNo(STANDARD_E2E_ORDER.platformOrderNo);
      } catch (e) {
        console.warn('  清理异常:', e.message);
      }
    }
    await browser.close();
    if (!testPassed) process.exitCode = 1;
  }
}

run();
