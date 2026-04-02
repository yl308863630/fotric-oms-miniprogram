// 商品详情/新建/编辑页面
const { productApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    id: null,
    action: '', // create/edit/view
    loading: true,
    submitting: false,
    
    // 表单数据
    formData: {
      name: '',
      code: '',
      barcode: '',
      materialNo: '',
      brand: 'FOTRIC',
      model: '',
      specs: '',
      productConfig: '标准配置',
      deliveryPeriod: '',
      warrantyPeriod: '',
      unit: '台',
      category: '',
      price: 0,
      stock: 0,
      image: '',
      remark: '',
      competitorLink: '',
      isActive: true
    },
    
    // 分类选项
    categoryOptions: [
      { value: 'office', label: '办公用品' },
      { value: 'electronics', label: '电子设备' },
      { value: 'consumables', label: '耗材' }
    ],
    
    // 权限
    canSave: false
  },

  onLoad(options) {
    const id = options.id;
    const action = options.action || 'view';
    
    this.setData({ id, action });
    this.checkPermissions();
    
    if (action === 'create') {
      this.setData({ loading: false });
      wx.setNavigationBarTitle({ title: '新增商品' });
    } else if (id) {
      this.loadDetail(id);
      wx.setNavigationBarTitle({ title: action === 'edit' ? '编辑商品' : '商品详情' });
    }
  },

  checkPermissions() {
    const canSave = this.data.action === 'create' 
      ? app.hasPermission('create_product') 
      : app.hasPermission('edit_product');
    this.setData({ canSave });
  },

  async loadDetail(id) {
    this.setData({ loading: true });
    try {
      const res = await productApi.get(id);
      this.setData({ 
        formData: { ...this.data.formData, ...res },
        loading: false 
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 输入处理
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    this.setData({ [`formData.${field}`]: value });
  },

  // 数字输入
  onNumberInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = parseFloat(e.detail.value) || 0;
    this.setData({ [`formData.${field}`]: value });
  },

  // 分类选择
  onCategoryChange(e) {
    const index = parseInt(e.detail.value);
    const category = this.data.categoryOptions[index].value;
    this.setData({ 'formData.category': category });
  },

  // 状态switch
  onStatusChange(e) {
    this.setData({ 'formData.isActive': e.detail.value });
  },

  // 选择图片
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        this.setData({ 'formData.image': tempFilePath });
        // TODO: 实际上传图片到服务器
      }
    });
  },

  // 提交表单
  async handleSubmit() {
    if (!this.data.canSave) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    const { formData, action, id } = this.data;
    
    // 验证必填
    if (!formData.name) {
      wx.showToast({ title: '请填写商品名称', icon: 'none' });
      return;
    }
    if (!formData.code) {
      wx.showToast({ title: '请填写商品编码', icon: 'none' });
      return;
    }
    if (!formData.brand) {
      wx.showToast({ title: '请填写品牌', icon: 'none' });
      return;
    }
    if (!formData.unit) {
      wx.showToast({ title: '请填写单位', icon: 'none' });
      return;
    }
    
    this.setData({ submitting: true });
    
    try {
      if (action === 'create') {
        await productApi.create(formData);
        wx.showToast({ title: '新增成功', icon: 'success' });
      } else {
        await productApi.update(id, formData);
        wx.showToast({ title: '更新成功', icon: 'success' });
      }
      
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    } catch (err) {
      console.error('提交失败:', err);
      wx.showToast({ title: action === 'create' ? '新增失败' : '更新失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  // 编辑商品（在详情页点击编辑按钮）
  handleEdit() {
    wx.navigateTo({ url: `/pages/products/detail?id=${this.data.id}&action=edit` });
  },

  // 删除商品
  handleDelete() {
    if (!app.hasPermission('delete_product')) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    wx.showModal({
      title: '确认删除',
      content: `确定删除商品 "${this.data.formData.name}"？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            await productApi.delete(this.data.id);
            wx.showToast({ title: '删除成功', icon: 'success' });
            setTimeout(() => wx.navigateBack(), 1500);
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  }
});