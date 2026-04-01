// 合作伙伴页面
const { partnerApi } = require('../../utils/request');

Page({
  data: {
    partners: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    keyword: ''
  },

  onLoad() {
    this.loadPartners();
  },

  onPullDownRefresh() {
    this.setData({ page: 0, partners: [], hasMore: true });
    this.loadPartners().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadPartners();
    }
  },

  async loadPartners() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    
    try {
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.keyword) params.title = this.data.keyword;
      
      const res = await partnerApi.list(params);
      const newPartners = res.content || [];
      
      this.setData({
        partners: this.data.page === 0 ? newPartners : [...this.data.partners, ...newPartners],
        hasMore: newPartners.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    this.setData({ keyword: e.detail.value, page: 0, partners: [], hasMore: true });
    this.loadPartners();
  }
});