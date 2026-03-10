/**
 * 读取 订单实测.xlsx 并输出为 JSON，便于后续按数据集录入
 */
const path = require('path');
const fs = require('fs');

const xlsxPath = process.argv[2] || 'e:\\OMS原型图\\订单实测.xlsx';

let XLSX;
try {
  XLSX = require('xlsx');
} catch (e) {
  console.error('请先安装: npm install xlsx');
  process.exit(1);
}

const wb = XLSX.readFile(xlsxPath);
const sheetName = wb.SheetNames[0];
const ws = wb.Sheets[sheetName];
const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });

console.log('Sheet:', sheetName);
console.log('行数:', rows.length);
console.log('表头(第1行):', JSON.stringify(rows[0]));
console.log('前3行数据:');
for (let i = 1; i <= Math.min(3, rows.length - 1); i++) {
  console.log('  行' + (i + 1) + ':', JSON.stringify(rows[i]));
}

const outPath = path.join(__dirname, 'evidence', '订单实测-解析.json');
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, JSON.stringify(rows, null, 2), 'utf8');
console.log('\n已写入:', outPath);
