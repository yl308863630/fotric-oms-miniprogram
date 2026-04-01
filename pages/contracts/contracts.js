// 合同列表
const { contractApi } = require('../../utils/request');

Page({
  data: {
    contracts: [],
    loading: false
  },
  onLoad() { this.loadContracts(); },
  async loadContracts() {
    this.setData({ loading: true });
    try {
      const res = await contractApi.list({ page: 0, size: 20 });
      this.setData({ contracts: res.content || [], loading: false });
    } catch(e) { this.setData({ loading: false }); }
  }
});