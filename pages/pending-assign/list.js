// 待指派订单列表页面
const { salesOrderApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    orders: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    searchKeyword: '',
    
    // 当前用户权限
    canAssign: false
  },

  onLoad() {
    this.checkPermissions();
    this.loadOrders();
  },

  onShow() {
    this.setData({ page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  checkPermissions() {
    const canAssign = app.hasPermission('assign_order');
    this.setData({ canAssign });
  },

  onPullDownRefresh() {
    this.setData({ page: 0, orders: [], hasMore: true });
    this.loadOrders().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadOrders();
    }
  },

  async loadOrders() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loading: true });
    try {
      const params = { 
        page: this.data.page, 
        size: this.data.size,
        masterStatus: '待指派' 
      };
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
      }
      const res = await salesOrderApi.list(params);
      this.setData({
        orders: this.data.page === 0 ? res.content || [] : [...this.data.orders, ...res.content || []],
        hasMore: (res.content || []).length >= this.data.size,
        loading: false
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    const keyword = e.detail.value;
    this.setData({ searchKeyword: keyword, page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}&action=assign` });
  },

  // 批量指派
  goToBatchAssign() {
    if (!this.data.canAssign) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    // TODO: 批量指派功能
    wx.showToast({ title: '批量指派开发中', icon: 'none' });
  }
});