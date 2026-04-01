// 结算/对账页面
const { settlementApi } = require('../../utils/request');

Page({
  data: {
    activeTab: 'reconciliation', // reconciliation | settlement | invoice
    tabs: [
      { key: 'reconciliation', label: '销售对账' },
      { key: 'settlement', label: '销售结算' },
      { key: 'invoice', label: '销项发票' }
    ],
    reconciliationList: [],
    settlementList: [],
    invoiceList: [],
    page: 0,
    size: 20,
    loading: false,
    hasMore: true
  },

  onLoad() {
    this.loadData();
  },

  onPullDownRefresh() {
    this.setData({ page: 0, hasMore: true });
    this.loadData().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadData();
    }
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ 
      activeTab: tab, 
      page: 0, 
      hasMore: true,
      reconciliationList: [],
      settlementList: [],
      invoiceList: []
    });
    this.loadData();
  },

  async loadData() {
    const { activeTab, page, size } = this.data;
    if (this.data.loading) return;
    
    this.setData({ loading: true });
    try {
      let res;
      const params = { page, size };
      
      switch (activeTab) {
        case 'reconciliation':
          res = await settlementApi.salesReconciliationList(params);
          this.setData({
            reconciliationList: page === 0 ? (res.content || []) : [...this.data.reconciliationList, ...(res.content || [])]
          });
          break;
        case 'settlement':
          res = await settlementApi.salesSettlementList(params);
          this.setData({
            settlementList: page === 0 ? (res.content || []) : [...this.data.settlementList, ...(res.content || [])]
          });
          break;
        case 'invoice':
          res = await settlementApi.salesInvoiceList(params);
          this.setData({
            invoiceList: page === 0 ? (res.content || []) : [...this.data.invoiceList, ...(res.content || [])]
          });
          break;
      }
      
      this.setData({
        hasMore: res.content?.length >= size,
        loading: false
      });
    } catch (err) {
      console.error('加载数据失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 查看对账详情
  goToReconciliationDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/settlements/reconciliation-detail?id=${id}`
    });
  }
});