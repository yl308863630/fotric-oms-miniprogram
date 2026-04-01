// 登录页面
const app = getApp();
const { authApi } = require('../../utils/request');

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    debugInfo: ''
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
    this.setData({ loading: true, debugInfo: '正在登录...' });

    try {
      const res = await authApi.login({ username, password });
      console.log('登录返回完整:', JSON.stringify(res));
      this.setData({ debugInfo: '返回: ' + JSON.stringify(res).substring(0, 200) });
      
      // 尝试多种返回格式
      let token = null;
      let userInfo = null;
      
      // 格式1: {token, user}
      if (res.token && res.user) {
        token = res.token;
        userInfo = res.user;
      }
      // 格式2: {data: {token, user}}
      else if (res.data) {
        const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
        token = data.token;
        userInfo = data.user || data;
      }
      // 格式3: 直接返回token
      else if (res.token) {
        token = res.token;
        userInfo = { id: res.id, username: res.username, role: res.role };
      }
      
      console.log('解析结果 - token:', token, 'user:', userInfo);
      
      if (!token) {
        this.setData({ debugInfo: '无token: ' + JSON.stringify(res).substring(0, 200) });
        throw new Error(res.message || '登录失败：未获取到token');
      }
      
      app.globalData.token = token;
      app.globalData.userInfo = {
        id: userInfo?.id,
        username: userInfo?.username || username,
        realName: userInfo?.realName || userInfo?.name || username,
        role: userInfo?.role || userInfo?.userType || 'SALES'
      };
      
      wx.setStorageSync('token', token);
      wx.setStorageSync('userInfo', app.globalData.userInfo);
      
      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/dashboard/dashboard' });
      }, 1000);
    } catch (err) {
      console.error('登录失败:', err);
      this.setData({ debugInfo: '错误: ' + (err.message || err) });
      wx.showToast({ title: err.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});