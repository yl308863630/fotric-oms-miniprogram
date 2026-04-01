// 合并对账页面
const { salesOrderApi, settlementApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    // 可对账的订单列表
    availableOrders: [],
    page: 0,
    size: 20,
    loading: false,
    hasMore: true,
    
    // 已选订单
    selectedOrders: [],
    selectAll: false,
    
    // 对账信息
    reconciliationForm: {
      partyAId: null,
      partyATitle: '',
      partyReconciliationNo: '',
      remark: ''
    },
    
    // 甲方列表
    partyList: [],
    showPartyPicker: false,
    
    // 提交中
    submitting: false
  },

  onLoad() {
    this.loadAvailableOrders();
  },

  onPullDownRefresh() {
    this.setData({ 
      page: 0, 
      availableOrders: [], 
      hasMore: true,
      selectedOrders: [],
      selectAll: false 
    });
    this.loadAvailableOrders().then(() => {
      wx.stopPullDownRefresh();
    });
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadAvailableOrders();
    }
  },

  async loadAvailableOrders() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loading: true });
    
    try {
      const params = { 
        page: this.data.page, 
        size: this.data.size,
        // 已发货或待开票状态才能对账
        status: '已发货,已对账未开票,已开票待结算' 
      };
      const res = await salesOrderApi.list(params);
      const newOrders = res.content || [];
      
      // 过滤掉已选中的
      const selectedIds = this.data.selectedOrders.map(o => o.id);
      const filteredOrders = newOrders.filter(o => !selectedIds.includes(o.id));
      
      this.setData({
        availableOrders: this.data.page === 0 ? filteredOrders : [...this.data.availableOrders, ...filteredOrders],
        hasMore: newOrders.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      console.error('加载订单失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 选择订单
  toggleSelect(e) {
    const order = e.currentTarget.dataset.item;
    const selectedOrders = [...this.data.selectedOrders];
    const availableOrders = [...this.data.availableOrders];
    
    const existIndex = selectedOrders.findIndex(o => o.id === order.id);
    if (existIndex > -1) {
      // 取消选择
      selectedOrders.splice(existIndex, 1);
    } else {
      // 添加选择
      selectedOrders.push(order);
      // 从可用列表移除
      const idx = availableOrders.findIndex(o => o.id === order.id);
      if (idx > -1) availableOrders.splice(idx, 1);
    }
    
    this.setData({
      selectedOrders,
      availableOrders,
      selectAll: selectedOrders.length > 0 && selectedOrders.length + availableOrders.length === 0
    });
  },

  // 全选
  toggleSelectAll() {
    const { selectAll, selectedOrders, availableOrders } = this.data;
    
    if (selectAll) {
      // 取消全选
      this.setData({
        availableOrders: [...availableOrders, ...selectedOrders],
        selectedOrders: [],
        selectAll: false
      });
    } else {
      // 全选
      this.setData({
        selectedOrders: [...selectedOrders, ...availableOrders],
        availableOrders: [],
        selectAll: true
      });
    }
  },

  // 移除已选订单
  removeSelected(e) {
    const index = e.currentTarget.dataset.index;
    const order = this.data.selectedOrders[index];
    const selectedOrders = [...this.data.selectedOrders];
    const availableOrders = [...this.data.availableOrders];
    
    selectedOrders.splice(index, 1);
    availableOrders.push(order);
    
    this.setData({
      selectedOrders,
      availableOrders,
      selectAll: false
    });
  },

  // 选择甲方
  showPartyModal() {
    this.setData({ showPartyPicker: true });
  },

  async loadPartyList() {
    try {
      const res = await request('/api/partner-info', { 
        method: 'GET', 
        data: { page: 0, size: 100, type: '甲方' } 
      });
      this.setData({ partyList: res.content || [] });
    } catch(e) {
      console.error('加载甲方失败:', e);
    }
  },

  selectParty(e) {
    const { id, title } = e.currentTarget.dataset;
    this.setData({
      'reconciliationForm.partyAId': id,
      'reconciliationForm.partyATitle': title,
      showPartyPicker: false
    });
  },

  // 输入变化
  onReconciliationNoInput(e) {
    this.setData({ 'reconciliationForm.partyReconciliationNo': e.detail.value });
  },
  
  onRemarkInput(e) {
    this.setData({ 'reconciliationForm.remark': e.detail.value });
  },

  // 计算总金额
  calculateTotal() {
    return this.data.selectedOrders
      .reduce((sum, order) => sum + (parseFloat(order.amount) || 0), 0)
      .toFixed(2);
  },

  // 提交合并对账
  async submitReconciliation() {
    const { selectedOrders, reconciliationForm, submitting } = this.data;
    
    if (selectedOrders.length === 0) {
      wx.showToast({ title: '请选择订单', icon: 'none' });
      return;
    }
    if (!reconciliationForm.partyATitle) {
      wx.showToast({ title: '请选择甲方', icon: 'none' });
      return;
    }
    
    if (submitting) return;
    this.setData({ submitting: true });

    try {
      const data = {
        partyAId: reconciliationForm.partyAId,
        partyATitle: reconciliationForm.partyATitle,
        partyReconciliationNo: reconciliationForm.partyReconciliationNo,
        remark: reconciliationForm.remark,
        orderIds: selectedOrders.map(o => o.id),
        totalAmount: this.calculateTotal(),
        orderCount: selectedOrders.length
      };
      
      await settlementApi.createReconciliation(data);
      
      wx.showToast({ title: '创建成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    } catch (err) {
      wx.showToast({ title: err.message || '创建失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  }
});

// 辅助函数
function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    const fullUrl = url.startsWith('http') ? url : `${getApp().globalData.apiBase}${url}`;
    wx.request({
      url: fullUrl,
      ...options,
      header: {
        'Authorization': `Bearer ${getApp().globalData.token}`,
        'Content-Type': 'application/json',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 200) resolve(res.data);
        else reject(res.data);
      },
      fail: reject
    });
  });
}