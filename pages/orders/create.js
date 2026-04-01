// 新建销售订单页面
const { salesOrderApi, productApi, partnerApi } = require('../../utils/request');

Page({
  data: {
    // 表单数据
    formData: {
      partyATitle: '',      // 甲方名称
      partyAId: null,       // 甲方ID
      platformName: '',     // 平台名称
      platformOrderNo: '',  // 平台订单号
      contactName: '',      // 联系人
      contactPhone: '',     // 电话
      deliveryAddress: '',  // 收货地址
      remark: '',           // 备注
      items: []             // 商品明细
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
    ]
  },

  onLoad() {
    // 可以从参数获取是否是指派过来的
  },

  // 输入处理
  onPlatformChange(e) {
    const index = e.detail.value;
    this.setData({ 'formData.platformName': this.data.platformOptions[index].value });
  },
  onPlatformOrderNoInput(e) {
    this.setData({ 'formData.platformOrderNo': e.detail.value });
  },
  onContactNameInput(e) {
    this.setData({ 'formData.contactName': e.detail.value });
  },
  onContactPhoneInput(e) {
    this.setData({ 'formData.contactPhone': e.detail.value });
  },
  onAddressInput(e) {
    this.setData({ 'formData.deliveryAddress': e.detail.value });
  },
  onRemarkInput(e) {
    this.setData({ 'formData.remark': e.detail.value });
  },

  // 选择甲方
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

  // 选择甲方
  selectParty(e) {
    const { id, title } = e.currentTarget.dataset;
    this.setData({
      'formData.partyAId': id,
      'formData.partyATitle': title,
      showPartyPicker: false
    });
  },

  // 显示商品选择弹窗
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

  // 选择商品
  selectProduct(e) {
    const product = e.currentTarget.dataset.item;
    // 检查是否已添加
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

  // 删除商品
  removeItem(e) {
    const index = e.currentTarget.dataset.index;
    const items = [...this.data.formData.items];
    items.splice(index, 1);
    this.setData({ 'formData.items': items });
  },

  // 修改商品数量
  onQuantityChange(e) {
    const { index, value } = e.detail;
    const items = [...this.data.formData.items];
    items[index].quantity = parseInt(value) || 1;
    this.setData({ 'formData.items': items });
  },

  // 修改商品单价
  onPriceChange(e) {
    const { index, value } = e.detail;
    const items = [...this.data.formData.items];
    items[index].price = parseFloat(value) || 0;
    this.setData({ 'formData.items': items });
  },

  // 计算总金额
  calculateTotal() {
    const { items } = this.data.formData;
    return items.reduce((sum, item) => sum + (item.price * item.quantity), 0).toFixed(2);
  },

  // 提交订单
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
    if (!formData.contactName || !formData.contactPhone) {
      wx.showToast({ title: '请填写联系人信息', icon: 'none' });
      return;
    }
    if (!formData.deliveryAddress) {
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
      const submitData = {
        partyAId: formData.partyAId,
        partyATitle: formData.partyATitle,
        platformName: formData.platformName,
        platformOrderNo: formData.platformOrderNo,
        contactName: formData.contactName,
        contactPhone: formData.contactPhone,
        deliveryAddress: formData.deliveryAddress,
        remark: formData.remark,
        items: formData.items.map(item => ({
          productId: item.productId,
          productName: item.productName,
          model: item.model,
          price: item.price,
          quantity: item.quantity,
          amount: item.price * item.quantity
        })),
        totalAmount: this.calculateTotal()
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