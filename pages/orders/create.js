// 新建销售订单页面
const { salesOrderApi, productApi, partnerApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    // 表单数据
    formData: {
      // 甲方信息
      partyATitle: '',
      partyAId: null,
      // 平台信息
      platformName: '',
      platformOrderNo: '',
      topLevelCustomerName: '',  // 顶层客户名
      // 收货信息
      receiverName: '',
      receiverPhone: '',
      receiverAddress: '',
      // 支付与订单类型
      paymentMethod: '',
      orderType: '',
      // 线下销售
      offlineSales: '',
      offlineContractNo: '',
      offlineShippingPrice: '',
      deductionRate: '',
      // 交付方采购价
      deliveryPartyPurchasePrice: '',
      // 备注
      remark: '',
      // 商品明细
      items: [],
      // 合同
      contractFile: null,
      contractUrl: ''
    },

    // 商品选择弹窗
    showProductPicker: false,
    productList: [],
    productPage: 0,
    productLoading: false,

    // 甲方选择弹窗
    showPartyPicker: false,
    partyList: [],
    partyPage: 0,
    partyLoading: false,

    // 提交中
    submitting: false,

    // 平台选项
    platformOptions: [
      { value: '京东工业', label: '京东工业' },
      { value: '震坤行', label: '震坤行' },
      { value: '西域供应链', label: '西域供应链' },
      { value: '欧菲斯', label: '欧菲斯' },
      { value: '晨光', label: '晨光' },
      { value: '齐心', label: '齐心' },
      { value: '其他', label: '其他' }
    ],

    // 支付方式选项
    paymentMethodOptions: [
      { value: '全款', label: '全款' },
      { value: '账期', label: '账期' },
      { value: '背靠背', label: '背靠背' }
    ],

    // 订单类型选项
    orderTypeOptions: [
      { value: '自营', label: '自营' },
      { value: '第三方', label: '第三方' }
    ],

    // 线下销售选项
    offlineSalesOptions: [
      { value: '是', label: '是' },
      { value: '否', label: '否' }
    ]
  },

  onLoad() {},

  // ========== 输入处理 ==========
  onPlatformChange(e) {
    const index = e.detail.value;
    this.setData({ 'formData.platformName': this.data.platformOptions[index].value });
  },

  onPlatformOrderNoInput(e) {
    this.setData({ 'formData.platformOrderNo': e.detail.value });
  },

  onTopLevelCustomerInput(e) {
    this.setData({ 'formData.topLevelCustomerName': e.detail.value });
  },

  onReceiverNameInput(e) {
    this.setData({ 'formData.receiverName': e.detail.value });
  },

  onReceiverPhoneInput(e) {
    this.setData({ 'formData.receiverPhone': e.detail.value });
  },

  onReceiverAddressInput(e) {
    this.setData({ 'formData.receiverAddress': e.detail.value });
  },

  onPaymentMethodChange(e) {
    const index = e.detail.value;
    this.setData({ 'formData.paymentMethod': this.data.paymentMethodOptions[index].value });
  },

  onOrderTypeChange(e) {
    const index = e.detail.value;
    this.setData({ 'formData.orderType': this.data.orderTypeOptions[index].value });
  },

  onOfflineSalesChange(e) {
    const index = e.detail.value;
    this.setData({ 'formData.offlineSales': this.data.offlineSalesOptions[index].value });
  },

  onOfflineContractNoInput(e) {
    this.setData({ 'formData.offlineContractNo': e.detail.value });
  },

  onOfflineShippingPriceInput(e) {
    this.setData({ 'formData.offlineShippingPrice': e.detail.value });
  },

  onDeductionRateInput(e) {
    this.setData({ 'formData.deductionRate': e.detail.value });
  },

  onDeliveryPartyPurchasePriceInput(e) {
    this.setData({ 'formData.deliveryPartyPurchasePrice': e.detail.value });
  },

  onRemarkInput(e) {
    this.setData({ 'formData.remark': e.detail.value });
  },

  // ========== 合同上传 ==========
  uploadContract() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (res) => {
        const file = res.tempFiles[0];
        this.setData({ 'formData.contractFile': file });
        this.parseContract(file);
      }
    });
  },

  async parseContract(file) {
    wx.showLoading({ title: '正在解析合同...' });
    try {
      const uploadRes = await new Promise((resolve, reject) => {
        wx.uploadFile({
          url: `${app.globalData.apiBase}/api/files/upload`,
          filePath: file.path,
          name: 'file',
          success: resolve,
          fail: reject
        });
      });

      const parseData = JSON.parse(uploadRes.data);
      if (parseData.url) {
        const ocrRes = await new Promise((resolve, reject) => {
          wx.request({
            url: `${app.globalData.apiBase}/api/contracts/parse`,
            method: 'POST',
            data: { fileUrl: parseData.url },
            success: resolve,
            fail: reject
          });
        });

        const result = ocrRes.data;
        if (result.success && result.data) {
          const data = result.data;
          wx.showToast({ title: '合同解析成功', icon: 'success' });
          
          if (data.partyAName) {
            this.setData({ 'formData.partyATitle': data.partyAName });
          }
          if (data.amount) {
            this.setData({ 'formData.remark': `合同金额：¥${data.amount}` });
          }
          this.setData({ 'formData.contractUrl': parseData.url });
        }
      }
    } catch (err) {
      console.error('合同解析失败:', err);
      wx.showToast({ title: '解析失败，请重试', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  removeContract() {
    this.setData({ 'formData.contractFile': null, 'formData.contractUrl': '' });
  },

  // ========== 选择甲方 ==========
  showPartyModal() {
    this.setData({ showPartyPicker: true, partyPage: 0, partyList: [] });
    this.loadPartyList();
  },

  async loadPartyList() {
    if (this.data.partyLoading) return;
    this.setData({ partyLoading: true });
    try {
      const res = await partnerApi.list({ page: this.data.partyPage, size: 20 });
      this.setData({
        partyList: this.data.partyPage === 0 ? res.content : [...this.data.partyList, ...(res.content || [])],
        partyLoading: false
      });
    } catch(e) {
      this.setData({ partyLoading: false });
    }
  },

  selectParty(e) {
    const { id, title } = e.currentTarget.dataset;
    this.setData({
      'formData.partyAId': id,
      'formData.partyATitle': title,
      showPartyPicker: false
    });
  },

  // ========== 选择商品 ==========
  showProductModal() {
    this.setData({ showProductPicker: true, productPage: 0, productList: [] });
    this.loadProductList();
  },

  async loadProductList() {
    if (this.data.productLoading) return;
    this.setData({ productLoading: true });
    try {
      const res = await productApi.list({ page: this.data.productPage, size: 20 });
      this.setData({
        productList: this.data.productPage === 0 ? res.content : [...this.data.productList, ...(res.content || [])],
        productLoading: false
      });
    } catch(e) {
      this.setData({ productLoading: false });
    }
  },

  selectProduct(e) {
    const product = e.currentTarget.dataset.item;
    const existingIndex = this.data.formData.items.findIndex(item => item.productId === product.id);
    if (existingIndex > -1) {
      wx.showToast({ title: '商品已添加', icon: 'none' });
      return;
    }
    const newItem = {
      productId: product.id,
      productName: product.name,
      model: product.model || '',
      price: product.price || 0,
      quantity: 1
    };
    this.setData({
      'formData.items': [...this.data.formData.items, newItem],
      showProductPicker: false
    });
  },

  removeItem(e) {
    const index = e.currentTarget.dataset.index;
    const items = [...this.data.formData.items];
    items.splice(index, 1);
    this.setData({ 'formData.items': items });
  },

  onQuantityChange(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const items = [...this.data.formData.items];
    items[index].quantity = parseInt(value) || 1;
    this.setData({ 'formData.items': items });
  },

  onPriceChange(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const items = [...this.data.formData.items];
    items[index].price = parseFloat(value) || 0;
    this.setData({ 'formData.items': items });
  },

  // ========== 计算 ==========
  calculateTotal() {
    const { items } = this.data.formData;
    return items.reduce((sum, item) => sum + (item.price * item.quantity), 0).toFixed(2);
  },

  // ========== 提交订单 ==========
  async submitOrder() {
    const { formData, submitting } = this.data;

    // 验证必填项
    if (!formData.partyATitle) {
      wx.showToast({ title: '请选择甲方', icon: 'none' });
      return;
    }
    if (!formData.platformName) {
      wx.showToast({ title: '请选择平台', icon: 'none' });
      return;
    }
    if (!formData.platformOrderNo) {
      wx.showToast({ title: '请输入平台订单号', icon: 'none' });
      return;
    }
    if (!formData.receiverName || !formData.receiverPhone) {
      wx.showToast({ title: '请填写收货人信息', icon: 'none' });
      return;
    }
    if (!formData.receiverAddress) {
      wx.showToast({ title: '请填写收货地址', icon: 'none' });
      return;
    }
    if (!formData.items.length) {
      wx.showToast({ title: '请添加商品', icon: 'none' });
      return;
    }

    if (submitting) return;
    this.setData({ submitting: true });

    try {
      const totalAmount = this.calculateTotal();
      const submitData = {
        // 甲方
        partyAId: formData.partyAId,
        partyATitle: formData.partyATitle,
        // 平台
        platformName: formData.platformName,
        platformOrderNo: formData.platformOrderNo,
        topLevelCustomerName: formData.topLevelCustomerName,
        // 收货
        receiverName: formData.receiverName,
        receiverPhone: formData.receiverPhone,
        receiverAddress: formData.receiverAddress,
        // 支付方式
        paymentMethod: formData.paymentMethod,
        orderType: formData.orderType,
        // 线下销售
        offlineSales: formData.offlineSales,
        offlineContractNo: formData.offlineContractNo,
        offlineShippingPrice: formData.offlineShippingPrice ? parseFloat(formData.offlineShippingPrice) : null,
        deductionRate: formData.deductionRate ? parseFloat(formData.deductionRate) : null,
        // 交付方采购价
        deliveryPartyPurchasePrice: formData.deliveryPartyPurchasePrice ? parseFloat(formData.deliveryPartyPurchasePrice) : null,
        // 备注
        remark: formData.remark,
        // 商品
        items: formData.items.map(item => ({
          productId: item.productId,
          productName: item.productName,
          model: item.model,
          price: item.price,
          quantity: item.quantity,
          amount: item.price * item.quantity
        })),
        // 金额
        amount: totalAmount,
        taxIncludedTotal: totalAmount,
        // 合同
        contractUrl: formData.contractUrl
      };

      await salesOrderApi.create(submitData);
      
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