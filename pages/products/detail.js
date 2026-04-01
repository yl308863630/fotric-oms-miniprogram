// 商品详情页面
const { productApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    id: null,
    detail: null,
    loading: true
  },

  onLoad(options) {
    const id = options.id;
    if (id) {
      this.setData({ id });
      this.loadDetail(id);
    }
  },

  async loadDetail(id) {
    this.setData({ loading: true });
    try {
      const res = await productApi.get(id);
      this.setData({ detail: res, loading: false });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 编辑商品
  handleEdit() {
    wx.navigateTo({
      url: `/pages/products/products?id=${this.data.id}&action=edit`
    });
  }
});