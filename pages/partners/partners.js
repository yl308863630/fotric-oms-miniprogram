// 合作伙伴页面
const { partnerApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    partners: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    keyword: '',
    // 权限
    canCreate: false,
    canEdit: false,
    canDelete: false,
    // 操作菜单
    actionSheetVisible: false,
    currentPartner: null
  },

  onLoad() {
    this.checkPermissions();
    this.loadPartners();
  },

  onShow() {
    this.setData({ page: 0, partners: [], hasMore: true });
    this.loadPartners();
  },

  checkPermissions() {
    const canCreate = app.hasPermission('create_partner');
    const canEdit = app.hasPermission('edit_partner');
    const canDelete = app.hasPermission('delete_partner');
    this.setData({ canCreate, canEdit, canDelete });
  },

  onPullDownRefresh() {
    this.setData({ page: 0, partners: [], hasMore: true });
    this.loadPartners().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadPartners();
    }
  },

  async loadPartners() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.keyword) params.title = this.data.keyword;
      const res = await partnerApi.list(params);
      const newPartners = res.content || [];
      this.setData({
        partners: this.data.page === 0 ? newPartners : [...this.data.partners, ...newPartners],
        hasMore: newPartners.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    this.setData({ keyword: e.detail.value, page: 0, partners: [], hasMore: true });
    this.loadPartners();
  },

  // 新增合作伙伴
  goToCreate() {
    wx.navigateTo({ url: '/pages/partners/detail?action=create' });
  },

  // 查看详情
  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/partners/detail?id=${id}` });
  },

  // 操作菜单
  showActionSheet(e) {
    const partner = e.currentTarget.dataset.item;
    this.setData({ actionSheetVisible: true, currentPartner: partner });
  },

  hideActionSheet() {
    this.setData({ actionSheetVisible: false, currentPartner: null });
  },

  // 编辑
  handleEdit() {
    if (!this.data.canEdit) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/partners/detail?id=${this.data.currentPartner.id}&action=edit` });
  },

  // 删除
  handleDelete() {
    if (!this.data.canDelete) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.showModal({
      title: '确认删除',
      content: `确定删除 "${this.data.currentPartner.title}"？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            await partnerApi.delete(this.data.currentPartner.id);
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.setData({ page: 0, partners: [], hasMore: true });
            this.loadPartners();
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  }
});