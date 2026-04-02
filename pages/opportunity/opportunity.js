// 商机列表页面
const { opportunityApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    opportunities: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    searchKeyword: '',
    stageFilter: '',
    actionSheetVisible: false,
    currentOpportunity: null,
    // 权限
    canCreate: false,
    canEdit: false,
    canDelete: false,
    // 状态选项
    stageOptions: [
      { value: '', label: '全部' },
      { value: '跟进中', label: '跟进中' },
      { value: '已报价', label: '已报价' },
      { value: '已签约', label: '已签约' },
      { value: '已流失', label: '已流失' }
    ]
  },

  onLoad() {
    this.checkPermissions();
    this.loadOpportunities();
  },

  onShow() {
    this.setData({ page: 0, opportunities: [], hasMore: true });
    this.loadOpportunities();
  },

  checkPermissions() {
    const canCreate = app.hasPermission('create_opportunity');
    const canEdit = app.hasPermission('edit_opportunity');
    const canDelete = app.hasPermission('delete_opportunity');
    this.setData({ canCreate, canEdit, canDelete });
  },

  onPullDownRefresh() {
    this.setData({ page: 0, opportunities: [], hasMore: true });
    this.loadOpportunities().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadOpportunities();
    }
  },

  async loadOpportunities() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loading: true });
    try {
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
      }
      if (this.data.stageFilter) {
        params.stage = this.data.stageFilter;
      }
      const res = await opportunityApi.list(params);
      const newOpportunities = res.content || [];
      this.setData({
        opportunities: this.data.page === 0 ? newOpportunities : [...this.data.opportunities, ...newOpportunities],
        hasMore: newOpportunities.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      console.error('加载商机失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    const keyword = e.detail.value;
    this.setData({ searchKeyword: keyword, page: 0, opportunities: [], hasMore: true });
    this.loadOpportunities();
  },

  onStageChange(e) {
    const stage = e.currentTarget.dataset.stage;
    this.setData({ stageFilter: stage, page: 0, opportunities: [], hasMore: true });
    this.loadOpportunities();
  },

  goToCreate() {
    wx.navigateTo({ url: '/pages/opportunity/detail?action=create' });
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/opportunity/detail?id=${id}` });
  },

  showActionSheet(e) {
    const opportunity = e.currentTarget.dataset.item;
    this.setData({ actionSheetVisible: true, currentOpportunity: opportunity });
  },

  hideActionSheet() {
    this.setData({ actionSheetVisible: false, currentOpportunity: null });
  },

  handleEdit() {
    if (!this.data.canEdit) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/opportunity/detail?id=${this.data.currentOpportunity.id}&action=edit` });
  },

  handleDelete() {
    if (!this.data.canDelete) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.showModal({
      title: '确认删除',
      content: `确定删除商机 "${this.data.currentOpportunity.title}"？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            await opportunityApi.delete(this.data.currentOpportunity.id);
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.setData({ page: 0, opportunities: [], hasMore: true });
            this.loadOpportunities();
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  },

  handleGenerateQuotation() {
    this.hideActionSheet();
    wx.showToast({ title: '生成报价单功能开发中', icon: 'none' });
  },

  // 获取阶段标签类型
  getStageType(stage) {
    const map = {
      '跟进中': 'info',
      '已报价': 'warning',
      '已签约': 'success',
      '已流失': 'danger'
    };
    return map[stage] || 'info';
  }
});