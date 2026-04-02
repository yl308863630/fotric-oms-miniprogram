// 商机详情/新建/编辑页面
const { opportunityApi, productApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    id: null,
    action: '',
    loading: true,
    submitting: false,
    
    // 表单数据
    formData: {
      title: '',
      customerCode: '',
      category: '',
      source: '',
      customerName: '',
      inquiryHeader: '',
      orderHeader: '',
      contact: '',
      phone: '',
      productId: null,
      productModel: '',
      quantity: 1,
      estimatedAmount: 0,
      platform: '',
      serviceProvider: '',
      shippingChannel: '',
      expectedDate: '',
      region: '',
      industry: '',
      problemSolved: '',
      budget: '',
      competitor: '',
      ecommerceSales: '',
      offlineSales: '',
      stage: '跟进中',
      deliveryPeriod: '',
      latestFollowUpRecord: ''
    },
    
    // 选项
    categoryOptions: [
      { value: 'reported', label: '报备' },
      { value: 'inquiry', label: '询价' },
      { value: 'tender', label: '投标' },
      { value: 'project', label: '项目' }
    ],
    sourceOptions: [
      { value: 'phone', label: '电话咨询' },
      { value: 'visit', label: '拜访客户' },
      { value: 'exhibition', label: '展会' },
      { value: 'referral', label: '转介绍' },
      { value: 'online', label: '线上咨询' },
      { value: 'other', label: '其他' }
    ],
    stageOptions: [
      { value: '跟进中', label: '跟进中' },
      { value: '已报价', label: '已报价' },
      { value: '已签约', label: '已签约' },
      { value: '已流失', label: '已流失' }
    ],
    platformOptions: [
      { value: '京东工业', label: '京东工业' },
      { value: '震坤行', label: '震坤行' },
      { value: '西域供应链', label: '西域供应链' },
      { value: '欧菲斯', label: '欧菲斯' },
      { value: '晨光', label: '晨光' },
      { value: '齐心', label: '齐心' },
      { value: '线下', label: '线下' }
    ],
    
    canSave: false
  },

  onLoad(options) {
    const id = options.id;
    const action = options.action || 'view';
    
    this.setData({ id, action });
    this.checkPermissions();
    
    if (action === 'create') {
      this.setData({ loading: false });
      wx.setNavigationBarTitle({ title: '新增商机' });
    } else if (id) {
      this.loadDetail(id);
      wx.setNavigationBarTitle({ title: action === 'edit' ? '编辑商机' : '商机详情' });
    }
  },

  checkPermissions() {
    const canSave = this.data.action === 'create' 
      ? app.hasPermission('create_opportunity') 
      : app.hasPermission('edit_opportunity');
    this.setData({ canSave });
  },

  async loadDetail(id) {
    this.setData({ loading: true });
    try {
      const res = await opportunityApi.get(id);
      this.setData({ 
        formData: { ...this.data.formData, ...res },
        loading: false 
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    this.setData({ [`formData.${field}`]: value });
  },

  onNumberInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = parseFloat(e.detail.value) || 0;
    this.setData({ [`formData.${field}`]: value });
  },

  onPickerChange(e) {
    const field = e.currentTarget.dataset.field;
    const options = this.data[`${field}Options`];
    const index = parseInt(e.detail.value);
    const value = options[index].value;
    this.setData({ [`formData.${field}`]: value });
  },

  onDateChange(e) {
    this.setData({ 'formData.expectedDate': e.detail.value });
  },

  handleSubmit() {
    if (!this.data.canSave) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    const { formData, action, id } = this.data;
    
    if (!formData.title) {
      wx.showToast({ title: '请填写商机标题', icon: 'none' });
      return;
    }
    if (!formData.customerName) {
      wx.showToast({ title: '请填写客户名称', icon: 'none' });
      return;
    }
    
    this.setData({ submitting: true });
    
    opportunityApi[action === 'create' ? 'create' : 'update'](action === 'create' ? formData : { ...formData, id })
      .then(() => {
        wx.showToast({ title: action === 'create' ? '新增成功' : '更新成功', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 1500);
      })
      .catch(err => {
        console.error('提交失败:', err);
        wx.showToast({ title: action === 'create' ? '新增失败' : '更新失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ submitting: false });
      });
  },

  // 编辑商机
  handleEdit() {
    wx.navigateTo({ url: `/pages/opportunity/detail?id=${this.data.id}&action=edit` });
  },

  handleDelete() {
    if (!app.hasPermission('delete_opportunity')) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    wx.showModal({
      title: '确认删除',
      content: `确定删除商机 "${this.data.formData.title}"？`,
      success: (res) => {
        if (res.confirm) {
          opportunityApi.delete(this.data.id)
            .then(() => {
              wx.showToast({ title: '删除成功', icon: 'success' });
              setTimeout(() => wx.navigateBack(), 1500);
            })
            .catch(() => {
              wx.showToast({ title: '删除失败', icon: 'none' });
            });
        }
      }
    });
  }
});