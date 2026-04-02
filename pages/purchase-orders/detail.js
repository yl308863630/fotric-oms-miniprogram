// 采购订单详情页
const { purchaseOrderApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    id: null,
    order: null,
    loading: true,
    action: '', // 当前操作类型
    // 权限
    canConfirm: false,
    canSettle: false,
    canErpEntry: false,
    canUploadReceipt: false,
    canEdit: false,
    canReturn: false
  },

  onLoad(options) {
    const id = options.id;
    const action = options.action || '';
    this.setData({ id, action });
    this.checkPermissions();
    this.loadOrder();
  },

  checkPermissions() {
    const canConfirm = app.hasPermission('confirm_order');
    const canSettle = app.hasPermission('settle');
    const canErpEntry = app.hasPermission('erp_entry');
    const canUploadReceipt = app.hasPermission('upload_receipt');
    const canEdit = app.hasPermission('edit_order');
    const canReturn = app.hasPermission('return_order');
    this.setData({ canConfirm, canSettle, canErpEntry, canUploadReceipt, canEdit, canReturn });
  },

  async loadOrder() {
    try {
      const order = await purchaseOrderApi.get(this.data.id);
      this.setData({ order, loading: false });
      
      // 根据action自动触发相应操作
      if (this.data.action) {
        this.handleAction(this.data.action);
      }
    } catch (err) {
      console.error('加载订单失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  handleAction(action) {
    switch (action) {
      case 'confirm':
        this.handleConfirm();
        break;
      case 'settle':
        this.handleSettle();
        break;
      case 'receipt':
        this.handleReceipt();
        break;
      case 'erp':
        this.handleErp();
        break;
      case 'edit':
        this.handleEdit();
        break;
      case 'return':
        this.handleReturn();
        break;
      case 'contract':
        this.handleContract();
        break;
    }
  },

  handleConfirm() {
    if (!this.data.canConfirm) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '确认订单',
      content: '确定要确认此采购订单吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await purchaseOrderApi.update(this.data.id, { ...this.data.order, status: '待盖章' });
            wx.showToast({ title: '确认成功', icon: 'success' });
            this.loadOrder();
          } catch (err) {
            wx.showToast({ title: '操作失败', icon: 'none' });
          }
        }
      }
    });
  },

  handleSettle() {
    if (!this.data.canSettle) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.showToast({ title: '结算功能开发中', icon: 'none' });
  },

  handleReceipt() {
    if (!this.data.canUploadReceipt) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      success: async (res) => {
        const file = res.tempFiles[0];
        wx.showLoading({ title: '上传中...' });
        // TODO: 上传签收单
        wx.hideLoading();
        wx.showToast({ title: '上传成功', icon: 'success' });
      }
    });
  },

  handleErp() {
    if (!this.data.canErpEntry) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.showToast({ title: 'ERP录单功能开发中', icon: 'none' });
  },

  handleEdit() {
    if (!this.data.canEdit) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.showToast({ title: '编辑功能开发中', icon: 'none' });
  },

  handleReturn() {
    if (!this.data.canReturn) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '退回订单',
      content: '确定要退回此采购订单吗？',
      success: async (res) => {
        if (res.confirm) {
          // TODO: 实现退回逻辑
          wx.showToast({ title: '退回成功', icon: 'success' });
        }
      }
    });
  },

  handleContract() {
    if (!this.data.order.contractUrl) {
      wx.showToast({ title: '暂无合同', icon: 'none' });
      return;
    }
    wx.downloadFile({
      url: this.data.order.contractUrl,
      success: (res) => {
        wx.openDocument({ filePath: res.tempFilePath });
      }
    });
  }
});