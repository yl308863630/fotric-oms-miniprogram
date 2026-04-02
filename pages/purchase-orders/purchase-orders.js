// 采购订单列表页面
const { purchaseOrderApi } = require('../../utils/request');
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
    // 权限
    canConfirm: false,
    canSettle: false,
    canErpEntry: false,
    canUploadReceipt: false,
    canEdit: false,
    canReturn: false,
    canViewContract: false,
    statusOptions: [
      { value: '', label: '全部' },
      { value: '待确认', label: '待确认' },
      { value: '待盖章', label: '待盖章' },
      { value: '待发货', label: '待发货' },
      { value: '待签收', label: '待签收' },
      { value: '待对账', label: '待对账' },
      { value: '待开票', label: '待开票' },
      { value: '待结算', label: '待结算' },
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
    const canConfirm = app.hasPermission('confirm_order'); // 采购确认
    const canSettle = app.hasPermission('settle'); // 结算
    const canErpEntry = app.hasPermission('erp_entry'); // ERP录单
    const canUploadReceipt = app.hasPermission('upload_receipt'); // 签收单
    const canEdit = app.hasPermission('edit_order'); // 编辑
    const canReturn = app.hasPermission('return_order'); // 退回
    const canViewContract = true; // 查看合同
    this.setData({ canConfirm, canSettle, canErpEntry, canUploadReceipt, canEdit, canReturn, canViewContract });
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
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.statusFilter) {
        params.status = this.data.statusFilter;
      }
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
      }
      const res = await purchaseOrderApi.list(params);
      const newOrders = res.content || [];
      this.setData({
        orders: this.data.page === 0 ? newOrders : [...this.data.orders, ...newOrders],
        hasMore: newOrders.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      console.error('加载采购订单失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    const keyword = e.detail.value;
    this.setData({ searchKeyword: keyword, page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ statusFilter: status, page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${id}` });
  },

  showActionSheet(e) {
    const order = e.currentTarget.dataset.item;
    this.setData({ actionSheetVisible: true, currentOrder: order });
  },

  hideActionSheet() {
    this.setData({ actionSheetVisible: false, currentOrder: null });
  },

  handleConfirm() {
    if (!this.data.canConfirm) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=confirm` });
  },

  handleSettle() {
    if (!this.data.canSettle) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=settle` });
  },

  handleReceipt() {
    if (!this.data.canUploadReceipt) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=receipt` });
  },

  handleErp() {
    if (!this.data.canErpEntry) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=erp` });
  },

  handleContract() {
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=contract` });
  },

  handleEdit() {
    if (!this.data.canEdit) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=edit` });
  },

  handleReturn() {
    if (!this.data.canReturn) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/purchase-orders/detail?id=${this.data.currentOrder.id}&action=return` });
  }
});