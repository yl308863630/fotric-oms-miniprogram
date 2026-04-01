// 订单列表页面
const { salesOrderApi } = require('../../utils/request');

Page({
  data: {
    orders: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    statusFilter: '',
    statusOptions: [
      { value: '', label: '全部' },
      { value: 'PENDING', label: '待处理' },
      { value: 'CONFIRMED', label: '已确认' },
      { value: 'SHIPPED', label: '已发货' },
      { value: 'COMPLETED', label: '已完成' },
      { value: 'CANCELLED', label: '已取消' }
    ]
  },

  onLoad() {
    this.loadOrders();
  },

  onPullDownRefresh() {
    this.setData({ page: 0, orders: [], hasMore: true });
    this.loadOrders().then(() => {
      wx.stopPullDownRefresh();
    });
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
        size: this.data.size
      };
      
      if (this.data.statusFilter) {
        params.masterStatus = this.data.statusFilter;
      }
      
      const res = await salesOrderApi.list(params);
      
      const newOrders = res.content || [];
      
      this.setData({
        orders: this.data.page === 0 ? newOrders : [...this.data.orders, ...newOrders],
        hasMore: newOrders.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      console.error('加载订单失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 筛选状态
  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ 
      statusFilter: status, 
      page: 0, 
      orders: [], 
      hasMore: true 
    });
    this.loadOrders();
  },

  // 查看详情
  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}` });
  }
});