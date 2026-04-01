// 订单详情页
const { salesOrderApi, partnerApi } = require('../../utils/request');
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
    canAssign: false,
    canReassign: false,
    canReturn: false,
    canEdit: false,
    
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
    erpFile: null,
    
    // 指派表单
    assignForm: {
      deliveryParty: '',
      deliveryPartyId: null,
      deliveryPartyPurchasePrice: '',
      paymentMethod: '',
      contractTemplate: ''
    },
    deliveryPartyList: [],
    deliveryPartyLoading: false,
    
    // 编辑表单
    editForm: {}
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
      canCreateOrder: app.hasPermission('create_order'),
      canAssign: app.hasPermission('assign_order'),
      canReassign: app.hasPermission('reassign_order'),
      canReturn: app.hasPermission('return_order'),
      canEdit: app.hasPermission('edit_order')
    });
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const res = await salesOrderApi.get(this.data.id);
      this.setData({ 
        detail: res, 
        loading: false,
        // 回填表单数据
        'assignForm.deliveryParty': res.deliveryParty || '',
        'assignForm.deliveryPartyPurchasePrice': res.deliveryPartyPurchasePrice || '',
        'assignForm.paymentMethod': res.paymentMethod || ''
      });
      
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
      case 'assign':
        if (this.data.canAssign) {
          this.setData({ showActionModal: true, actionType: 'assign' });
        }
        break;
      case 'edit':
        if (this.data.canEdit) {
          this.setData({ showActionModal: true, actionType: 'edit' });
        }
        break;
    }
  },

  // 打开操作弹窗
  openActionModal(e) {
    const type = e.currentTarget.dataset.type;
    
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
    if (type === 'assign' && !this.data.canAssign) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'edit' && !this.data.canEdit) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'reassign' && !this.data.canReassign) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    if (type === 'return' && !this.data.canReturn) {
      wx.showToast({ title: '无权限操作', icon: 'none' });
      return;
    }
    
    this.setData({ showActionModal: true, actionType: type });
  },

  // 关闭弹窗
  closeModal() {
    this.setData({ showActionModal: false, actionType: '', action: null });
  },

  // ========== 指派 ==========
  showDeliveryPartyPicker() {
    this.setData({ deliveryPartyList: [], deliveryPartyLoading: true });
    this.loadDeliveryPartyList();
  },

  async loadDeliveryPartyList() {
    try {
      const res = await partnerApi.list({ page: 0, size: 50, type: '乙方' });
      this.setData({ 
        deliveryPartyList: res.content || [],
        deliveryPartyLoading: false 
      });
    } catch(e) {
      this.setData({ deliveryPartyLoading: false });
    }
  },

  selectDeliveryParty(e) {
    const { id, title } = e.currentTarget.dataset;
    this.setData({
      'assignForm.deliveryPartyId': id,
      'assignForm.deliveryParty': title
    });
  },

  onAssignPriceChange(e) {
    this.setData({ 'assignForm.deliveryPartyPurchasePrice': e.detail.value });
  },

  onAssignPaymentChange(e) {
    const methods = ['全款', '账期', '背靠背'];
    this.setData({ 'assignForm.paymentMethod': methods[e.detail.value] });
  },

  onAssignTemplateChange(e) {
    const templates = ['标准合同', '简易合同', '第三方合同'];
    this.setData({ 'assignForm.contractTemplate': templates[e.detail.value] });
  },

  async submitAssign() {
    const { detail, assignForm, actionLoading } = this.data;
    
    if (!assignForm.deliveryParty) {
      wx.showToast({ title: '请选择交付方', icon: 'none' });
      return;
    }
    
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.assign(detail.id, {
        deliveryParty: assignForm.deliveryParty,
        deliveryPartyId: assignForm.deliveryPartyId,
        deliveryPartyPurchasePrice: parseFloat(assignForm.deliveryPartyPurchasePrice) || 0,
        paymentMethod: assignForm.paymentMethod,
        contractTemplate: assignForm.contractTemplate,
        status: '待确认订单'
      });
      
      wx.showToast({ title: '指派成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 编辑订单 ==========
  async submitEdit() {
    const { detail, editForm, actionLoading } = this.data;
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.update(detail.id, editForm);
      wx.showToast({ title: '保存成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  // ========== 退回订单 ==========
  async submitReturn() {
    const { detail, actionLoading } = this.data;
    if (actionLoading) return;
    this.setData({ actionLoading: true });

    try {
      await salesOrderApi.returnOrder(detail.id, {
        returnReason: '小程序退回'
      });
      wx.showToast({ title: '退回成功', icon: 'success' });
      this.closeModal();
      this.loadDetail();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ actionLoading: false });
    }
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