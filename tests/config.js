/**
 * 销售订单流程验证 - 测试配置与测试数据
 * 数据来源：截图表格（两条销售订单）
 */

const BASE_URL = process.env.OMS_API_BASE || 'http://localhost:8080';
const FRONTEND_URL = process.env.OMS_FRONTEND_URL || 'http://localhost:3000';

// 账号（用于登录与指派）
const USERS = {
  // 工业电商业务员 - 创建订单并指派
  creator: { username: 'sonmin', password: 'Sm123456' },
  // 上海坚领 - 订单 D20260210SM01
  jianling: { username: '坚领', password: 'Yxh123456', companyTitle: '上海坚领电子科技有限公司' },
  // 上海热像科技 - 订单 D20260209SM01
  refirm: { username: '热像科技-商务', password: 'Sw123456', companyTitle: '上海热像科技股份有限公司' },
  // 热像科技-仓库 - 发货准备、SN 上传、物流信息、签收单上传（商务或仓库）
  refirmWarehouse: { username: '热像科技-仓库', password: 'Ck123456', companyTitle: '上海热像科技股份有限公司' },
};

// 代运营主体（合同甲方），须在合作管理-用户信息维护中存在
const DEFAULT_OPERATION_ENTITY = '飞础科智慧科技（上海）有限公司';

// 根据截图整理的订单数据
const ORDER_1 = {
  platformName: '销售',
  platformOrderNo: 'D20260210SM01',
  omsOrderNo: 'D20260210SM01',
  orderDate: '2026-02-10',
  deliveryDate: '2026-02-10',
  operationEntityTitle: DEFAULT_OPERATION_ENTITY,
  materialNo: 'SM-F8200',
  model: 'Fotric F8200-P-D',
  quantity: 1,
  taxIncludedPrice: 25063.4,
  taxIncludedTotal: 25063.4,
  amount: 22180,
  receiverName: '上海坚领电子科技有限公司',
  receiverPhone: '2157502781',
  orderType: '第三方订单',
  deliveryParty: '上海坚领电子科技有限公司',
  deliveryPartyPurchasePrice: 17747.78,
  deductionRate: '20',
  settlementNo: '',
  status: '待指派',
  products: [
    {
      model: 'Fotric F8200-P-D',
      materialNo: 'SM-F8200',
      quantity: 1,
      taxIncludedPrice: 25063.4,
      taxIncludedTotal: 25063.4,
      deductionRate: '20',
      deliveryPartyPurchasePrice: 17747.78,
      orderType: '第三方订单',
    },
  ],
  logistics: [{ receiverName: '上海坚领电子科技有限公司', receiverPhone: '2157502781', deliveryParty: '上海坚领电子科技有限公司', deliveryDate: '2026-02-10' }],
  invoices: [],
  reconciliations: [],
};

const ORDER_2 = {
  platformName: '销售',
  platformOrderNo: 'D20260209SM01',
  omsOrderNo: 'D20260209SM01',
  orderDate: '2026-02-09',
  deliveryDate: '2026-02-10',
  operationEntityTitle: DEFAULT_OPERATION_ENTITY,
  materialNo: 'SM-F8200',
  model: 'Fotric F8200-P-D',
  quantity: 1,
  taxIncludedPrice: 24793.5,
  taxIncludedTotal: 24793.5,
  amount: 21950,
  receiverName: '上海热像科技股份有限公司',
  receiverPhone: '2157502781',
  orderType: '第三方订单',
  deliveryParty: '上海热像科技股份有限公司',
  deliveryPartyPurchasePrice: 22014.15, // 按约 11% 扣点估算，或与业务一致
  deductionRate: '11',
  settlementNo: '',
  status: '待指派',
  products: [
    {
      model: 'Fotric F8200-P-D',
      materialNo: 'SM-F8200',
      quantity: 1,
      taxIncludedPrice: 24793.5,
      taxIncludedTotal: 24793.5,
      deductionRate: '11',
      deliveryPartyPurchasePrice: 22014.15,
      orderType: '第三方订单',
    },
  ],
  logistics: [{ receiverName: '上海热像科技股份有限公司', receiverPhone: '2157502781', deliveryParty: '上海热像科技股份有限公司', deliveryDate: '2026-02-10' }],
  invoices: [],
  reconciliations: [],
};

// 指派预期：订单号 -> 被指派账号
const ASSIGN_EXPECTATIONS = {
  D20260210SM01: { assignedUsername: '坚领', deliveryParty: '上海坚领电子科技有限公司', deliveryPartyPurchasePrice: 17747.78 },
  D20260209SM01: { assignedUsername: '热像科技-商务', deliveryParty: '上海热像科技股份有限公司' },
};

// ---------- 标准 E2E 测试集：三账号指派与发货 ----------
function getStandardE2EDeliveryDate() {
  const d = new Date();
  d.setDate(d.getDate() + 7);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

const STANDARD_E2E_ORDER = {
  platformName: '震坤行工业超市（上海）有限公司',
  platformOrderNo: '5324435',
  platformSku: 'SK2242',
  model: 'XM3',
  quantity: 1,
  productConfig: '标准配置',
  warrantyPeriod: '1年',
  taxIncludedPrice: 3950,
  logistics: [
    { receiverName: '李丽', receiverPhone: '13333333333', receiverAddress: '上海青浦', deliveryDate: getStandardE2EDeliveryDate(), productQuantity: 1 },
  ],
  invoice: {
    invoiceTitle: '震坤行工业超市（上海）有限公司',
    paymentMethod: '背靠背',
  },
};

const STANDARD_E2E_ASSIGN = {
  deliveryParty: '上海坚领电子科技有限公司', // 须与 USERS.jianling.companyTitle 一致，后端按交付方查用户并写 assignedUsername
  deductionRate: 2,
  // 坚领采购价按扣点计算（A含税单价 * (1 - 扣点%)）
  useDeductionPrice: true,
  deliveryPartyPurchasePrice: 3750,
  partyBRepresentative: '袁星辉',
  templateName: 'FOTRIC电商扣点派单采购合同',
  platformName: '飞础科智慧科技（上海）有限公司',
};

const STANDARD_E2E_REASSIGN = {
  deliveryParty: '上海热像科技股份有限公司',
  // 坚领转派给热像科技使用固定价，不按扣点计算；支付方式为账期
  deliveryPartyPurchasePrice: 3550,
  paymentMethod: '账期',
  partyBRepresentative: '商务',
  templateName: 'FOTRIC电商扣点派单采购合同',
  platformName: '上海坚领电子科技有限公司',
};

const STANDARD_E2E_SHIPMENT = {
  deliveryMethod: '商家联系物流',
  logisticsCompany: '顺丰',
  trackingNumber: 'SF2342423',
  printBarcode128: true,
  printQuantity: 2,
};

// 热像科技-仓库：发货准备（平台送货单模板、128/箱唛）、SN 编码、物流、签收单
const STANDARD_E2E_WAREHOUSE = {
  snCode: '2701005927',
  logisticsCompany: '顺丰',
  trackingNumber: 'SF2342423',
  needReceiptReturn: true, // 需要签收单回传；签收单可由商务上传或仓库上传
};

// 签收单测试文件：取该文件夹下任意一个文件用于 E2E 上传签收单（若文件夹不存在或为空则跳过上传）
const RECEIPT_TEST_FOLDER = process.env.RECEIPT_TEST_FOLDER || 'D:\\Fotric\\平台方\\震坤行\\日常订单\\20251205 5515438564 震坤行 346L 上海电气斯必克工程技术有限公司 签收单';

// ---------- 第二测试集：平台订单 5516469437（第三方拆两单 + 自营一单）----------
const PLATFORM_ORDER_5516469437 = '5516469437';

function getOrder2DeliveryDate(offsetDays = 7) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

// 第三方-坚领：AC65Mini x1, 22105, 交付方 上海坚领电子科技有限公司, 采购价 21000, 顺丰 SF5122068084664
const ORDER_5516469437_JIANLING = {
  platformName: '震坤行工业超市（上海）有限公司',
  platformOrderNo: PLATFORM_ORDER_5516469437,
  platformSku: 'AF1866754',
  model: 'AC65Mini',
  quantity: 1,
  productConfig: '标准配置',
  warrantyPeriod: '1年',
  taxIncludedPrice: 22105,
  taxIncludedTotal: 22105,
  logistics: [
    {
      receiverName: '真诺测量仪表（上海）有限公司',
      receiverPhone: '13333333333',
      receiverAddress: '上海',
      deliveryDate: getOrder2DeliveryDate(7),
      productQuantity: 1,
    },
  ],
  invoice: {
    invoiceTitle: '震坤行工业超市（上海）有限公司',
    paymentMethod: '背靠背',
  },
  orderType: '第三方订单',
  // 指派用
  deliveryParty: '上海坚领电子科技有限公司',
  deliveryPartyPurchasePrice: 21000,
  partyBRepresentative: '袁星辉',
  templateName: 'FOTRIC电商扣点派单采购合同',
  offlineSales: '杨阳',
  offlineContractNo: '20260211YY01',
};

// 第三方-青岛科创：AC65Mini x1, 22105, 交付方 青岛科创贸易有限公司, 采购价 20800, 京东 JDV08466424234
const ORDER_5516469437_QINGDAO = {
  platformName: '震坤行工业超市（上海）有限公司',
  platformOrderNo: PLATFORM_ORDER_5516469437,
  platformSku: 'AF1866754',
  model: 'AC65Mini',
  quantity: 1,
  productConfig: '标准配置',
  warrantyPeriod: '1年',
  taxIncludedPrice: 22105,
  taxIncludedTotal: 22105,
  logistics: [
    {
      receiverName: '真诺测量仪表（上海）有限公司',
      receiverPhone: '13333333333',
      receiverAddress: '上海',
      deliveryDate: getOrder2DeliveryDate(7),
      productQuantity: 1,
    },
  ],
  invoice: {
    invoiceTitle: '震坤行工业超市（上海）有限公司',
    paymentMethod: '背靠背',
  },
  orderType: '第三方订单',
  deliveryParty: '青岛科创贸易有限公司',
  deliveryPartyPurchasePrice: 20800,
  partyBRepresentative: '商务',
  templateName: 'FOTRIC电商扣点派单采购合同',
  offlineSales: '许超',
  offlineContractNo: '20260211XC01',
};

// 自营：XM3 x1, 3950, 交付方 上海热像科技股份有限公司, 采购价 3160（订单号不带 D，常规采购）
const ORDER_5516469437_REFIRM = {
  platformName: '震坤行工业超市（上海）有限公司',
  platformOrderNo: PLATFORM_ORDER_5516469437,
  platformSku: 'AF3824158',
  model: 'XM3',
  orderType: '自营',
  quantity: 1,
  productConfig: '标准配置',
  warrantyPeriod: '1年',
  taxIncludedPrice: 3950,
  taxIncludedTotal: 3950,
  logistics: [
    {
      receiverName: '上海万芊荟商业发展有限公司',
      receiverPhone: '13333333333',
      receiverAddress: '上海',
      deliveryDate: getOrder2DeliveryDate(7),
      productQuantity: 1,
    },
  ],
  invoice: {
    invoiceTitle: '震坤行工业超市（上海）有限公司',
    paymentMethod: '背靠背',
  },
  deliveryParty: '上海热像科技股份有限公司',
  deliveryPartyPurchasePrice: 3160,
  partyBRepresentative: '商务',
  templateName: 'FOTRIC电商扣点派单采购合同',
};

// 第二测试集路径常量（可从环境变量覆盖）
const PLATFORM_CONTRACT_5516469437 =
  process.env.PLATFORM_CONTRACT_5516469437 ||
  'c:\\Users\\fotric\\Desktop\\5516469437真诺测量仪表（上海）有限公司 ac65mini 上海万芊荟商业发展有限公司 XM3.pdf';
const DELIVERY_NOTE_AC65 =
  process.env.DELIVERY_NOTE_AC65 ||
  'd:\\Fotric\\平台方\\震坤行\\日常订单\\20260209 5516469437真诺测量仪表（上海）有限公司 ac65mini 上海万芊荟商业发展有限公司 XM3\\AC65MINI 送货单 VC-SHD-2602108193325.pdf';
const DELIVERY_NOTE_XM3 =
  process.env.DELIVERY_NOTE_XM3 ||
  'd:\\Fotric\\平台方\\震坤行\\日常订单\\20260209 5516469437真诺测量仪表（上海）有限公司 ac65mini 上海万芊荟商业发展有限公司 XM3\\XM3 送货单VC-SHD-2602108196586.pdf';
// 热像科技对坚领/青岛科创销售发票测试用 PDF
const INVOICE_JIANLING_PDF =
  process.env.INVOICE_JIANLING_PDF ||
  'd:\\Fotric\\平台方\\西域\\日常订单\\20250827-西域-PO20250827046480 AC67FLEX\\dzfp_25312000000312058661_西域智慧供应链（上海）股份公司_20250928112426.pdf';
const INVOICE_QINGDAO_PDF =
  process.env.INVOICE_QINGDAO_PDF ||
  'd:\\Fotric\\平台方\\西域\\日常订单\\20251031-西域-PO20251030122156 克诺尔商用车 AC65\\dzfp_25312000000400355308_西域智慧供应链（上海）股份公司_20251205112824.pdf';

module.exports = {
  BASE_URL,
  FRONTEND_URL,
  USERS,
  ORDER_1,
  ORDER_2,
  ASSIGN_EXPECTATIONS,
  DEFAULT_OPERATION_ENTITY,
  STANDARD_E2E_ORDER,
  STANDARD_E2E_ASSIGN,
  STANDARD_E2E_REASSIGN,
  STANDARD_E2E_SHIPMENT,
  STANDARD_E2E_WAREHOUSE,
  RECEIPT_TEST_FOLDER,
  // 第二测试集 5516469437
  PLATFORM_ORDER_5516469437,
  ORDER_5516469437_JIANLING,
  ORDER_5516469437_QINGDAO,
  ORDER_5516469437_REFIRM,
  getOrder2DeliveryDate,
  PLATFORM_CONTRACT_5516469437,
  DELIVERY_NOTE_AC65,
  DELIVERY_NOTE_XM3,
  INVOICE_JIANLING_PDF,
  INVOICE_QINGDAO_PDF,
};
