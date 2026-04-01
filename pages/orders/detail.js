// 订单详情页
const { salesOrderApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    id: null,
    detail: null,
    loading: true,
    action: null,
    
    // 权限
    canConfirm: false,
    canShip: false,
    canSettle: false,
    canErpEntry: false,
    canUploadReceipt: false,
    canCreateOrder: false,
    
    // 操作弹窗
    showActionModal: false,
    actionType: '',
    actionLoading: false,
    
    // 发货表单
    shipForm: {
      logisticsCompany: '',
      trackingNumber: ''
    },
    receiptFile: null,
    receiptUrl: '',
    
    // 结算表单
    settleForm: {
      invoiceNumber: '',
      invoiceIssuedDate: '',
      partyReconciliationNo: '',
      settlementNo: ''
    },
    
    // 商务录单
    erpForm: {
      erpEntryStatus: '',
      erpEntryScreenshotUrl: ''
    },
    erpFile: null
  },

  onLoad(options) {
    this.checkPermissions();
    if (options.id) {
      this.setData({ id: options.id, action: options.action });
      this.loadDetail();
    }
  },

  onShow() {
    if (this.data.id) {
      this.loadDetail();
    }
  },

  // 检查权限
  checkPermissions() {
    this.setData({
      canConfirm: app.hasPermission('confirm_order'),
      canShip: app.hasPermission('ship'),
      canSettle: app.hasPermission('settle'),
      canErpEntry: app.hasPermission('erp_entry'),
      canUploadReceipt: app.hasPermission('upload_receipt'),
      canCreateOrder: app.hasPermission('create_order')
    });
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const res = await salesOrderApi.get(this.data.id);
      this.setData({ detail: res, loading: false });
      
      if (this.data.action) {
        this.handleAction(this.data.action);
      }
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 处理操作指令
  handleAction(action) {
    switch(action) {
      case 'confirm':
        if (this.data.canConfirm) {
          this.setData({ showActionModal: true, actionType: 'confirm' });
        }
        break;
      case 'ship':
        if (this.data.canShip) {
          this.setData({ showActionModal: true, actionType: 'ship' });
        }
        break;
      case 'settle':
        if (this.data.canSettle) {
          this.setData({ showActionModal: true, actionType: 'settle' });
        }
        break;
      case 'receipt':
        if (this.data.canUploadReceipt) {
          this.setData({ showActionModal: true, actionType: 'receipt' });
        }
        break;
      case 'erp':
        if (this.data.canErpEntry) {
          this.setData({ showActionModal: true, actionType: 'erp' });
        }
        break;
    }
  },

  // 打开操作弹窗
  openActionModal(e) {
    const type = e.currentTarget.dataset.type;
    
    // 权限检查
    if (type === 'confirm' && !this.data.canConfirm) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'ship' && !this.data.canShip) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'settle' && !this.data.canSettle) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'erp' && !this.data.canErpEntry) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'receipt' && !this.data.canUploadReceipt) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    
    this.setData({ showActionModal: true, actionType: type });
  },

  // 关闭弹窗
  closeModal() {
    this.setData({ showActionModal: false, actionType: '', action: null });
  },

  // ========== 发货 ==========
  onLogisticsChange(e) {
    this.setData({ 'shipForm.logisticsCompany': e.detail.value });
  },
  
  onTrackingNoChange(e) {
    this.setData({ 'shipForm.trackingNumber': e.detail.value });
  },

  async submitShip() {
    const { detail, shipForm, actionLoading } = this.data;
    if (!shipForm.logisticsCompany || !shipForm.trackingNumber) {
      wx.showToast({ title: '请填写物流信息', icon: 'none' });
      return;
    }
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.update(detail.id, {
        shippingParty: shipForm.logisticsCompany,
        platformReconciliationNo: shipForm.trackingNumber,
        status: '已发货'
      });
      wx.showToast({ title: '发货成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 上传签收单 ==========
  uploadReceipt() {
    wx.chooseImage({
      count: 1,
      success: (res) => {
        this.setData({ receiptFile: res.tempFiles[0] });
      }
    });
  },

  async submitReceipt() {
    const { detail, receiptFile, actionLoading } = this.data;
    if (!receiptFile) {
      wx.showToast({ title: '请上传签收单', icon: 'none' });
      return;
    }
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      const uploadRes = await new Promise((resolve, reject) => {
        wx.uploadFile({
          url: `${app.globalData.apiBase}/api/files/upload`,
          filePath: receiptFile.path,
          name: 'file',
          success: resolve,
          fail: reject
        });
      });
      
      const fileData = JSON.parse(uploadRes.data);
      
      await salesOrderApi.update(detail.id, {
        returnReceiptUrl: fileData.url,
        status: '已对账未开票'
      });
      
      wx.showToast({ title: '上传成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 结算 ==========
  onInvoiceNumberChange(e) {
    this.setData({ 'settleForm.invoiceNumber': e.detail.value });
  },
  
  onPartyReconciliationNoChange(e) {
    this.setData({ 'settleForm.partyReconciliationNo': e.detail.value });
  },
  
  onSettlementNoChange(e) {
    this.setData({ 'settleForm.settlementNo': e.detail.value });
  },

  async submitSettle() {
    const { detail, settleForm, actionLoading } = this.data;
    if (!settleForm.invoiceNumber) {
      wx.showToast({ title: '请填写发票号码', icon: 'none' });
      return;
    }
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.update(detail.id, {
        invoiceNumber: settleForm.invoiceNumber,
        partyReconciliationNo: settleForm.partyReconciliationNo,
        settlementNo: settleForm.settlementNo,
        status: '已开票待结算'
      });
      wx.showToast({ title: '结算成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 商务ERP录单 ==========
  onErpStatusChange(e) {
    this.setData({ 'erpForm.erpEntryStatus': e.detail.value });
  },

  uploadErpScreenshot() {
    wx.chooseImage({
      count: 1,
      success: (res) => {
        this.setData({ erpFile: res.tempFiles[0] });
      }
    });
  },

  async submitErp() {
    const { detail, erpForm, erpFile, actionLoading } = this.data;
    if (!erpForm.erpEntryStatus) {
      wx.showToast({ title: '请选择录单状态', icon: 'none' });
      return;
    }
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      let screenshotUrl = detail.erpEntryScreenshotUrl;
      
      if (erpFile) {
        const uploadRes = await new Promise((resolve, reject) => {
          wx.uploadFile({
            url: `${app.globalData.apiBase}/api/files/upload`,
            filePath: erpFile.path,
            name: 'file',
            success: resolve,
            fail: reject
          });
        });
        const fileData = JSON.parse(uploadRes.data);
        screenshotUrl = fileData.url;
      }

      await salesOrderApi.update(detail.id, {
        erpEntryStatus: erpForm.erpEntryStatus,
        erpEntryScreenshotUrl: screenshotUrl,
        erpEntryOperator: wx.getStorageSync('userInfo')?.username || '',
        erpEntryTime: new Date().toISOString()
      });
      
      wx.showToast({ title: '保存成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 确认订单 ==========
  async submitConfirm() {
    const { detail, actionLoading } = this.data;
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.update(detail.id, {
        status: '待合同盖章'
      });
      wx.showToast({ title: '确认成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 返回列表 ==========
  goBack() {
    wx.navigateBack();
  }
});