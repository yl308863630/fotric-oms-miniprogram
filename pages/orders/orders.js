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
    // 搜索
    searchKeyword: '',
    // 操作菜单
    actionSheetVisible: false,
    currentOrder: null,
    // 状态选项
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
    this.loadOrders();
  },

  onShow() {
    this.setData({ page: 0, orders: [], hasMore: true });
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

  // 搜索
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

  // 筛选状态
  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ statusFilter: status, page: 0, orders: [], hasMore: true });
    this.loadOrders();
  },

  // 新建订单
  goToCreate() {
    wx.navigateTo({ url: '/pages/orders/create' });
  },

  // 查看详情
  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}` });
  },

  // 显示操作菜单
  showActionSheet(e) {
    const order = e.currentTarget.dataset.item;
    this.setData({
      actionSheetVisible: true,
      currentOrder: order
    });
  },

  // 隐藏操作菜单
  hideActionSheet() {
    this.setData({
      actionSheetVisible: false,
      currentOrder: null
    });
  },

  // 操作：确认订单
  handleConfirm() {
    this.hideActionSheet();
    wx.navigateTo({ 
      url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=confirm` 
    });
  },

  // 操作：发货
  handleShip() {
    this.hideActionSheet();
    wx.navigateTo({ 
      url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=ship` 
    });
  },

  // 操作：结算
  handleSettle() {
    this.hideActionSheet();
    wx.navigateTo({ 
      url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=settle` 
    });
  },

  // 操作：上传签收单
  handleReceipt() {
    this.hideActionSheet();
    wx.navigateTo({ 
      url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=receipt` 
    });
  },

  // 操作：查看合同
  handleContract() {
    this.hideActionSheet();
    wx.navigateTo({ 
      url: `/pages/orders/detail?id=${this.data.currentOrder.id}&action=contract` 
    });
  }
});