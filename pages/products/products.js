// 商品列表页面
const { productApi } = require('../../utils/request');

Page({
  data: {
    products: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    keyword: ''
  },

  onLoad() {
    this.loadProducts();
  },

  onPullDownRefresh() {
    this.setData({ page: 0, products: [], hasMore: true });
    this.loadProducts().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadProducts();
    }
  },

  async loadProducts() {
    if (this.data.loading) return;
    
    this.setData({ loading: true });
    try {
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.keyword) {
        params.name = this.data.keyword;
      }
      
      const res = await productApi.list(params);
      const newProducts = res.content || [];
      
      this.setData({
        products: this.data.page === 0 ? newProducts : [...this.data.products, ...newProducts],
        hasMore: newProducts.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    this.setData({ keyword: e.detail.value, page: 0, products: [], hasMore: true });
    this.loadProducts();
  }
});