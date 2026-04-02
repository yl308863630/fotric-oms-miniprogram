// API 请求封装
const app = getApp();

const request = (url, options = {}) => {
  return new Promise((resolve, reject) => {
    const fullUrl = url.startsWith('http') ? url : `${app.globalData.apiBase}${url}`;
    
    wx.request({
      url: fullUrl,
      ...options,
      header: {
        'Authorization': `Bearer ${app.globalData.token}`,
        'Content-Type': 'application/json',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.data);
        } else if (res.statusCode === 401) {
          // 未登录，跳转到登录页
          app.logout();
          wx.redirectTo({ url: '/pages/login/login' });
          reject(new Error('未登录'));
        } else {
          reject(res.data);
        }
      },
      fail: (err) => {
        wx.showToast({
          title: '网络请求失败',
          icon: 'none'
        });
        reject(err);
      }
    });
  });
};

// 销售订单 API
export const salesOrderApi = {
  list: (params) => request('/api/sales-order-masters', { method: 'GET', data: params }),
  get: (id) => request(`/api/sales-order-masters/${id}`, { method: 'GET' }),
  create: (data) => request('/api/sales-order-masters', { method: 'POST', data }),
  update: (id, data) => request(`/api/sales-order-masters/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/sales-order-masters/${id}`, { method: 'DELETE' }),
  // 指派订单
  assign: (id, data) => request(`/api/sales-order-masters/${id}/assign`, { method: 'POST', data }),
  // 退回订单
  returnOrder: (id, data) => request(`/api/sales-order-masters/${id}/return`, { method: 'POST', data }),
  // 获取订单关联的子单（主链视角）
  getChildren: (id) => request(`/api/sales-order-masters/${id}/children`, { method: 'GET' }),
};

// 商品 API
export const productApi = {
  list: (params) => request('/api/products', { method: 'GET', data: params }),
  get: (id) => request(`/api/products/${id}`, { method: 'GET' }),
  create: (data) => request('/api/products', { method: 'POST', data }),
  update: (id, data) => request(`/api/products/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/products/${id}`, { method: 'DELETE' }),
  // 上下架
  updateStatus: (id, isActive) => request(`/api/products/${id}/status`, { method: 'PATCH', data: { isActive } }),
};

// 合作伙伴 API
export const partnerApi = {
  list: (params) => request('/api/partner-info', { method: 'GET', data: params }),
  get: (id) => request(`/api/partner-info/${id}`, { method: 'GET' }),
  create: (data) => request('/api/partner-info', { method: 'POST', data }),
  update: (id, data) => request(`/api/partner-info/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/partner-info/${id}`, { method: 'DELETE' }),
};

// 采购订单 API
export const purchaseOrderApi = {
  list: (params) => request('/api/purchase-orders', { method: 'GET', data: params }),
  get: (id) => request(`/api/purchase-orders/${id}`, { method: 'GET' }),
  create: (data) => request('/api/purchase-orders', { method: 'POST', data }),
  update: (id, data) => request(`/api/purchase-orders/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/purchase-orders/${id}`, { method: 'DELETE' }),
  // 更新付款状态
  updatePaymentStatus: (id, status) => request(`/api/purchase-orders/${id}/payment-status`, { method: 'PATCH', data: { status } }),
  // 更新对账状态
  updateReconciliationStatus: (id, status, invoiceNumber) => request(`/api/purchase-orders/${id}/reconciliation-status`, { method: 'PATCH', data: { status, invoiceNumber } }),
  // ERP录单
  updateErpEntry: (id, data) => request(`/api/purchase-orders/${id}/erp-entry`, { method: 'PATCH', data }),
};

// 合同 API
export const contractApi = {
  list: (params) => request('/api/contracts', { method: 'GET', data: params }),
  get: (id) => request(`/api/contracts/${id}`, { method: 'GET' }),
  create: (data) => request('/api/contracts', { method: 'POST', data }),
  update: (id, data) => request(`/api/contracts/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/contracts/${id}`, { method: 'DELETE' }),
};

// 销售对账/结算 API
export const settlementApi = {
  salesReconciliationList: (params) => request('/api/sales-reconciliations', { method: 'GET', data: params }),
  salesSettlementList: (params) => request('/api/sales-settlements', { method: 'GET', data: params }),
  salesInvoiceList: (params) => request('/api/sales-invoices', { method: 'GET', data: params }),
  // 创建合并对账单
  createReconciliation: (data) => request('/api/sales-reconciliations/batch', { method: 'POST', data }),
};

// 登录 API
export const authApi = {
  login: (data) => request('/api/auth/login', { method: 'POST', data }),
  getUserInfo: () => request('/api/auth/me', { method: 'GET' }),
};

// 合同模板 API
export const contractApi = {
  templateList: (params) => request('/api/contract-templates', { method: 'GET', data: params }),
  createTemplate: (data) => request('/api/contract-templates', { method: 'POST', data }),
  updateTemplate: (id, data) => request(`/api/contract-templates/${id}`, { method: 'PUT', data }),
  deleteTemplate: (id) => request(`/api/contract-templates/${id}`, { method: 'DELETE' }),
};

export default request;