// 登录页面
const app = getApp();
const { authApi } = require('../../utils/request');

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    captchaImage: '',
    captchaKey: '',
    showCaptcha: false
  },

  onLoad() {
    // 检查是否已登录
    const token = wx.getStorageSync('token');
    if (token) {
      wx.switchTab({ url: '/pages/dashboard/dashboard' });
    }
  },

  // 输入用户名
  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  // 输入密码
  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  // 登录
  async handleLogin() {
    const { username, password, loading } = this.data;
    
    if (!username) {
      wx.showToast({ title: '请输入用户名', icon: 'none' });
      return;
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }
    
    if (loading) return;
    this.setData({ loading: true });
    
    try {
      const res = await authApi.login({ username, password });
      
      app.globalData.token = res.token;
      app.globalData.userInfo = {
        id: res.id,
        username: res.username,
        realName: res.realName,
        role: res.role
      };
      
      wx.setStorageSync('token', res.token);
      wx.setStorageSync('userInfo', app.globalData.userInfo);
      
      wx.showToast({ title: '登录成功', icon: 'success' });
      
      setTimeout(() => {
        wx.switchTab({ url: '/pages/dashboard/dashboard' });
      }, 1000);
      
    } catch (err) {
      wx.showToast({
        title: err.message || '登录失败',
        icon: 'none'
      });
    } finally {
      this.setData({ loading: false });
    }
  }
});