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
};

// 商品 API
export const productApi = {
  list: (params) => request('/api/products', { method: 'GET', data: params }),
  get: (id) => request(`/api/products/${id}`, { method: 'GET' }),
  create: (data) => request('/api/products', { method: 'POST', data }),
  update: (id, data) => request(`/api/products/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/products/${id}`, { method: 'DELETE' }),
};

// 合作伙伴 API
export const partnerApi = {
  list: (params) => request('/api/partner-info', { method: 'GET', data: params }),
  get: (id) => request(`/api/partner-info/${id}`, { method: 'GET' }),
  create: (data) => request('/api/partner-info', { method: 'POST', data }),
  update: (id, data) => request(`/api/partner-info/${id}`, { method: 'PUT', data }),
  delete: (id) => request(`/api/partner-info/${id}`, { method: 'DELETE' }),
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
};

// 登录 API
export const authApi = {
  login: (data) => request('/api/auth/login', { method: 'POST', data }),
  getUserInfo: () => request('/api/auth/me', { method: 'GET' }),
};

export default request;