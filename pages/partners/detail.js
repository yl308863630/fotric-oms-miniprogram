// 合作伙伴详情/新建/编辑页面
const { partnerApi } = require('../../utils/request');
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
      type: 'A', // A=甲方, B=乙方
      code: '',
      contactPerson: '',
      contactPhone: '',
      email: '',
      address: '',
      taxNumber: '',
      bankName: '',
      bankAccount: '',
      bankAddress: '',
      remark: '',
      isDeliveryParty: false // 是否为交付方
    },
    
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
      wx.setNavigationBarTitle({ title: '新增合作伙伴' });
    } else if (id) {
      this.loadDetail(id);
      wx.setNavigationBarTitle({ title: action === 'edit' ? '编辑合作伙伴' : '合作伙伴详情' });
    }
  },

  checkPermissions() {
    const canSave = this.data.action === 'create' 
      ? app.hasPermission('create_partner') 
      : app.hasPermission('edit_partner');
    this.setData({ canSave });
  },

  async loadDetail(id) {
    this.setData({ loading: true });
    try {
      const res = await partnerApi.get(id);
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

  onTypeChange(e) {
    this.setData({ 'formData.type': e.currentTarget.dataset.type });
  },

  onSwitchChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`formData.${field}`]: e.detail.value });
  },

  handleSubmit() {
    if (!this.data.canSave) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    const { formData, action, id } = this.data;
    
    if (!formData.title) {
      wx.showToast({ title: '请填写抬头信息', icon: 'none' });
      return;
    }
    
    this.setData({ submitting: true });
    
    const submitData = { ...formData };
    if (action === 'create') {
      partnerApi.create(submitData)
        .then(() => {
          wx.showToast({ title: '新增成功', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 1500);
        })
        .catch(err => {
          wx.showToast({ title: '新增失败', icon: 'none' });
        })
        .finally(() => this.setData({ submitting: false }));
    } else {
      partnerApi.update(id, submitData)
        .then(() => {
          wx.showToast({ title: '更新成功', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 1500);
        })
        .catch(err => {
          wx.showToast({ title: '更新失败', icon: 'none' });
        })
        .finally(() => this.setData({ submitting: false }));
    }
  },

  // 编辑（在详情页点击编辑按钮）
  handleEdit() {
    wx.navigateTo({ url: `/pages/partners/detail?id=${this.data.id}&action=edit` });
  },

  // 删除
  handleDelete() {
    if (!app.hasPermission('delete_partner')) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    
    wx.showModal({
      title: '确认删除',
      content: `确定删除 "${this.data.formData.title}"？`,
      success: (res) => {
        if (res.confirm) {
          partnerApi.delete(this.data.id)
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