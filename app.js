// OMS 微信小程序 - 应用入口
const API_BASE = 'http://47.110.3.195:8080';

App({
  globalData: {
    userInfo: null,
    token: null,
    apiBase: API_BASE
  },
  
  onLaunch() {
    // 检查登录状态
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
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
  
  // 带Token的请求
  request(url, options = {}) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: url.startsWith('http') ? url : `${this.globalData.apiBase}${url}`,
        ...options,
        header: {
          'Authorization': `Bearer ${this.globalData.token}`,
          'Content-Type': 'application/json',
          ...options.header
        },
        success: resolve,
        fail: reject
      });
    });
  }
});