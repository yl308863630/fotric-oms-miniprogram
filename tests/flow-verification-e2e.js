/**
 * OMS 销售订单流程验证 - 浏览器 E2E（Playwright）+ 每步截图凭证
 * 运行：npm run test:e2e  或  npx playwright install chromium && node flow-verification-e2e.js
 * 环境：需先启动后端 (8080) 与前端 (3000)
 */

const path = require('path');
const fs = require('fs');
const { FRONTEND_URL, USERS } = require('./config.js');

const EVIDENCE_DIR = path.join(__dirname, 'evidence', 'screenshots');
if (!fs.existsSync(EVIDENCE_DIR)) {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
}

function screenshotName(step, label) {
  const t = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return path.join(EVIDENCE_DIR, `${t}-${step}-${label}.png`);
}

async function run() {
  let playwright;
  try {
    playwright = await import('playwright');
  } catch (e) {
    console.error('请先安装 Playwright: npx playwright install chromium');
    process.exit(1);
  }

  const { chromium } = playwright;
  let browser;
  try {
    browser = await chromium.launch({ headless: !!process.env.HEADLESS });
  } catch (e1) {
    try {
      browser = await chromium.launch({ channel: 'chrome', headless: !!process.env.HEADLESS });
    } catch (e2) {
      try {
        browser = await chromium.launch({ channel: 'msedge', headless: !!process.env.HEADLESS });
      } catch (e3) {
        console.error('无法启动浏览器。请执行: npx playwright install chromium');
        process.exit(1);
      }
    }
  }
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();

  try {
    console.log('========== OMS 流程验证 (E2E + 截图) ==========');
    console.log('前端:', FRONTEND_URL);

    // ---------- 1. 登录页 ----------
    console.log('\n[步骤 1] 打开登录页');
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.screenshot({ path: screenshotName('01', 'login-page') });
    console.log('  凭证: 01-login-page.png');

    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.creator.username);
    await page.getByPlaceholder(/密码|请输入密码/).fill(USERS.creator.password);
    await page.screenshot({ path: screenshotName('01', 'login-filled') });
    console.log('  凭证: 01-login-filled.png');

    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(3000);
    await page.screenshot({ path: screenshotName('01', 'after-login') });
    console.log('  凭证: 01-after-login.png');

    // ---------- 2. 销售管理列表 ----------
    console.log('\n[步骤 2] 进入销售管理');
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: screenshotName('02', 'sales-list') });
    console.log('  凭证: 02-sales-list.png');

    // ---------- 3. 新建订单入口 ----------
    console.log('\n[步骤 3] 点击新建销售订单');
    await page.getByRole('button', { name: /新建销售订单/ }).click();
    await page.waitForTimeout(1000);
    await page.screenshot({ path: screenshotName('03', 'new-order-dialog') });
    console.log('  凭证: 03-new-order-dialog.png');

    // ---------- 4. 以坚领账号查看（被指派方视角） ----------
    console.log('\n[步骤 4] 退出并以坚领账号登录（被指派方视角）');
    await context.clearCookies();
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.reload({ waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.jianling.username);
    await page.getByPlaceholder(/密码/).fill(USERS.jianling.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: screenshotName('04', 'jianling-login') });

    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: screenshotName('04', 'jianling-sales-list') });
    console.log('  凭证: 04-jianling-*.png（交付成交价应对被指派方可见）');

    // ---------- 5. 以热像科技-商务账号查看 ----------
    console.log('\n[步骤 5] 以热像科技-商务账号登录');
    await context.clearCookies();
    await page.goto(FRONTEND_URL + '/login', { waitUntil: 'networkidle' });
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.reload({ waitUntil: 'networkidle' });
    await page.getByPlaceholder(/用户|账号|用户名|请输入用户名/).fill(USERS.refirm.username);
    await page.getByPlaceholder(/密码/).fill(USERS.refirm.password);
    await page.getByRole('button', { name: /登\s*录|登录/ }).click();
    await page.waitForTimeout(2000);
    await page.goto(FRONTEND_URL + '/sales', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: screenshotName('05', 'refirm-sales-list') });
    console.log('  凭证: 05-refirm-sales-list.png');

    console.log('\n========== E2E 完成，截图已保存至 evidence/screenshots/ ==========');
  } catch (e) {
    console.error(e);
    await page.screenshot({ path: screenshotName('error', 'exception') }).catch(() => {});
    process.exit(1);
  } finally {
    await browser.close();
  }
}

run();
