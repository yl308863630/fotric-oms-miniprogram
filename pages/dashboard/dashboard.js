// 首页/Dashboard
const app = getApp();
const { salesOrderApi, productApi, partnerApi } = require('../../utils/request');

Page({
  data: {
    userInfo: null,
    stats: {
      pendingOrders: 0,
      totalOrders: 0,
      totalProducts: 0,
      totalPartners: 0
    },
    recentOrders: [],
    loading: true
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
    this.loadDashboard();
  },

  onShow() {
    // 每次显示刷新数据
    this.loadDashboard();
  },

  async loadDashboard() {
    this.setData({ loading: true });
    
    try {
      // 并行获取统计数据
      const [ordersRes, productsRes, partnersRes] = await Promise.all([
        salesOrderApi.list({ page: 0, size: 5 }),
        productApi.list({ page: 0, size: 1 }),
        partnerApi.list({ page: 0, size: 1 })
      ]);
      
      this.setData({
        stats: {
          pendingOrders: ordersRes.content?.filter(o => o.status === 'PENDING').length || 0,
          totalOrders: ordersRes.totalElements || 0,
          totalProducts: productsRes.totalElements || 0,
          totalPartners: partnersRes.totalElements || 0
        },
        recentOrders: ordersRes.content?.slice(0, 5) || [],
        loading: false
      });
    } catch (err) {
      console.error('加载Dashboard失败:', err);
      this.setData({ loading: false });
    }
  },

  // 跳转到订单列表
  goToOrders() {
    wx.switchTab({ url: '/pages/orders/orders' });
  },

  // 跳转到商品列表
  goToProducts() {
    wx.switchTab({ url: '/pages/products/products' });
  },

  // 跳转到合作伙伴
  goToPartners() {
    wx.switchTab({ url: '/pages/partners/partners' });
  },

  // 登出
  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          wx.redirectTo({ url: '/pages/login/login' });
        }
      }
    });
  }
});