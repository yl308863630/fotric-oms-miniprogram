// OMS 微信小程序 - 应用入口
const API_BASE = 'http://47.110.3.195:8080';

App({
  globalData: {
    userInfo: null,
    token: null,
    apiBase: API_BASE
  },

  onLaunch() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = wx.getStorageSync('userInfo');
    }
  },

  // 登录
  login(code) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.apiBase}/api/auth/login`,
        method: 'POST',
        data: { code },
        success: (res) => {
          if (res.data.token) {
            this.globalData.token = res.data.token;
            this.globalData.userInfo = res.data.user;
            wx.setStorageSync('token', res.data.token);
            wx.setStorageSync('userInfo', res.data.user);
            resolve(res.data);
          } else {
            reject(res.data);
          }
        },
        fail: reject
      });
    });
  },

  // 登出
  logout() {
    this.globalData.token = null;
    this.globalData.userInfo = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
  },

  // 判断是否有某权限
  hasPermission(permission) {
    const user = this.globalData.userInfo;
    if (!user) return false;
    
    // 管理员全部权限
    if (user.role === 'ADMIN' || user.isAdmin) return true;
    
    // 检查角色
    const rolePermissions = {
      // 业务员
      'SALES': ['create_order', 'view_order', 'view_product'],
      // 被指派方
      'PARTNER': ['confirm_order', 'sign_contract', 'view_order'],
      // 商务
      'BUSINESS': ['erp_entry', 'upload_receipt', 'view_order', 'view_contract'],
      // 仓库
      'WAREHOUSE': ['ship', 'upload_receipt', 'view_order'],
      // 财务
      'FINANCE': ['settle', 'invoice', 'view_order', 'view_settlement']
    };
    
    const perms = rolePermissions[user.role] || [];
    return perms.includes(permission);
  },

  // 判断是否业务员
  isSales() {
    const user = this.globalData.userInfo;
    return user?.role === 'SALES' || user?.isSales;
  },

  // 判断是否被指派方
  isPartner() {
    const user = this.globalData.userInfo;
    return user?.role === 'PARTNER' || user?.isPartner;
  },

  // 判断是否商务
  isBusiness() {
    const user = this.globalData.userInfo;
    return user?.role === 'BUSINESS' || user?.isBusiness;
  },

  // 判断是否仓库
  isWarehouse() {
    const user = this.globalData.userInfo;
    return user?.role === 'WAREHOUSE' || user?.isWarehouse;
  },

  // 判断是否财务
  isFinance() {
    const user = this.globalData.userInfo;
    return user?.role === 'FINANCE' || user?.isFinance;
  },

  // 判断是否管理员
  isAdmin() {
    const user = this.globalData.userInfo;
    return user?.role === 'ADMIN' || user?.isAdmin;
  }
});