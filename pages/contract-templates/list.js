// 合同模板管理页面
const { contractApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    templates: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    
    // 新建/编辑弹窗
    showModal: false,
    modalType: 'create', // create or edit
    editId: null,
    form: {
      name: '',
      content: '',
      remark: ''
    },
    submitting: false
  },

  onLoad() {
    this.loadTemplates();
  },

  onPullDownRefresh() {
    this.setData({ page: 0, templates: [], hasMore: true });
    this.loadTemplates().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadTemplates();
    }
  },

  async loadTemplates() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loading: true });
    try {
      const res = await contractApi.templateList({ page: this.data.page, size: this.data.size });
      this.setData({
        templates: this.data.page === 0 ? res.content || [] : [...this.data.templates, ...res.content || []],
        hasMore: (res.content || []).length >= this.data.size,
        loading: false
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 新建模板
  showCreateModal() {
    this.setData({
      showModal: true,
      modalType: 'create',
      editId: null,
      form: { name: '', content: '', remark: '' }
    });
  },

  // 编辑模板
  showEditModal(e) {
    const template = e.currentTarget.dataset.item;
    this.setData({
      showModal: true,
      modalType: 'edit',
      editId: template.id,
      form: {
        name: template.name,
        content: template.content,
        remark: template.remark || ''
      }
    });
  },

  // 关闭弹窗
  hideModal() {
    this.setData({ showModal: false });
  },

  // 表单输入
  onNameInput(e) {
    this.setData({ 'form.name': e.detail.value });
  },
  onContentInput(e) {
    this.setData({ 'form.content': e.detail.value });
  },
  onRemarkInput(e) {
    this.setData({ 'form.remark': e.detail.value });
  },

  // 保存模板
  async saveTemplate() {
    const { form, modalType, editId, submitting } = this.data;
    if (!form.name) {
      wx.showToast({ title: '请输入模板名称', icon: 'none' });
      return;
    }
    if (!form.content) {
      wx.showToast({ title: '请输入模板内容', icon: 'none' });
      return;
    }
    if (submitting) return;
    this.setData({ submitting: true });

    try {
      if (modalType === 'create') {
        await contractApi.createTemplate(form);
        wx.showToast({ title: '创建成功', icon: 'success' });
      } else {
        await contractApi.updateTemplate(editId, form);
        wx.showToast({ title: '修改成功', icon: 'success' });
      }
      this.hideModal();
      this.setData({ page: 0, templates: [], hasMore: true });
      this.loadTemplates();
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  // 删除模板
  deleteTemplate(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await contractApi.deleteTemplate(id);
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.setData({ page: 0, templates: [], hasMore: true });
            this.loadTemplates();
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  }
});