// 订单详情页
const { salesOrderApi } = require('../../utils/request');

Page({
  data: {
    id: null,
    detail: null,
    loading: true
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ id: options.id });
      this.loadDetail();
    }
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const res = await salesOrderApi.get(this.data.id);
      this.setData({ detail: res, loading: false });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  }
});