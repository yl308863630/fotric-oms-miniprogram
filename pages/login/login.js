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
    const token = wx.getStorageSync('token');
    if (token) {
      wx.switchTab({ url: '/pages/dashboard/dashboard' });
    }
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

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
      console.log('登录返回:', res);
      
      // 兼容不同的返回格式
      const token = res.token || res.data?.token;
      const userInfo = res.user || res.data?.user || res.data;
      
      if (!token) {
        throw new Error(res.message || '登录失败');
      }
      
      app.globalData.token = token;
      app.globalData.userInfo = {
        id: userInfo.id,
        username: userInfo.username,
        realName: userInfo.realName || userInfo.name,
        role: userInfo.role || userInfo.userType
      };
      
      wx.setStorageSync('token', token);
      wx.setStorageSync('userInfo', app.globalData.userInfo);
      
      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/dashboard/dashboard' });
      }, 1000);
    } catch (err) {
      console.error('登录失败:', err);
      wx.showToast({ title: err.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});