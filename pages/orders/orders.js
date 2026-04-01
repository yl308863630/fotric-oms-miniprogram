// 订单列表页面
const { salesOrderApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    orders: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    statusFilter: '',
    searchKeyword: '',
    actionSheetVisible: false,
    currentOrder: null,
    
    // 当前用户权限
    canCreateOrder: false,
    canConfirm: false,
    canShip: false,
    canSettle: false,
    canErpEntry: false,
    canUploadReceipt: false,
    
    statusOptions: [
      { value: '', label: '全部' },
      { value: '待指派', label: '待指派' },
      { value: '待确认订单', label: '待确认' },
      { value: '待合同盖章', label: '待盖章' },
      { value: '已发货', label: '已发货' },
      { value: '已对账未开票', label: '待开票' },
      { value: '已开票待结算', label: '待结算' },
      { value: '已完成', label: '已完成' },
      { value: '已取消', label: '已取消' }
    ]
  },

  onLoad() {
    this.checkPermissions();
    this.loadOrders();
  },

  onShow() {
    this.setData({ page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  // 检查权限
  checkPermissions() {
    const canCreateOrder = app.hasPermission('create_order');
    const canConfirm = app.hasPermission('confirm_order');
    const canShip = app.hasPermission('ship');
    const canSettle = app.hasPermission('settle');
    const canErpEntry = app.hasPermission('erp_entry');
    const canUploadReceipt = app.hasPermission('upload_receipt');
    
    this.setData({
      canCreateOrder,
      canConfirm,
      canShip,
      canSettle,
      canErpEntry,
      canUploadReceipt
    });
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
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
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

  onSearch(e) {
    const keyword = e.detail.value;
    this.setData({ 
      searchKeyword: keyword,
      page: 0, 
      orders: [], 
      hasMore: true 
    });
    this.loadOrders();
  },

  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ statusFilter: status, page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  goToCreate() {
    wx.navigateTo({ url: '/pages/orders/create' });
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}` });
  },

  showActionSheet(e) {
    const order = e.currentTarget.dataset.item;
    this.setData({
      actionSheetVisible: true,
      currentOrder: order
    });
  },

  hideActionSheet() {
    this.setData({
      actionSheetVisible: false,
      currentOrder: null
    });
  },

  // 根据权限判断是否显示操作
  handleConfirm() {
    if (!this.data.canConfirm) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=confirm` });
  },

  handleShip() {
    if (!this.data.canShip) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=ship` });
  },

  handleSettle() {
    if (!this.data.canSettle) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=settle` });
  },

  handleReceipt() {
    if (!this.data.canUploadReceipt) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=receipt` });
  },

  handleContract() {
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=contract` });
  },

  handleErp() {
    if (!this.data.canErpEntry) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=erp` });
  }
});