// 商品列表页面
const { productApi } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    products: [],
    page: 0,
    size: 20,
    hasMore: true,
    loading: false,
    keyword: '',
    brand: '',
    category: '',
    // 筛选面板
    showFilter: false,
    categoryOptions: [
      { value: 'office', label: '办公用品' },
      { value: 'electronics', label: '电子设备' },
      { value: 'consumables', label: '耗材' }
    ],
    // 状态筛选
    activeStatus: 'all', // all/active/inactive/warning
    statusOptions: [
      { value: 'all', label: '全部' },
      { value: 'active', label: '上架中' },
      { value: 'inactive', label: '已下架' },
      { value: 'warning', label: '库存预警' }
    ],
    // 权限
    canCreate: false,
    canEdit: false,
    canDelete: false,
    canChangeStatus: false,
    // 操作菜单
    actionSheetVisible: false,
    currentProduct: null
  },

  onLoad() {
    this.checkPermissions();
    this.loadProducts();
  },

  onShow() {
    // 每次显示刷新列表
    this.setData({ page: 0, products: [], hasMore: true });
    this.loadProducts();
  },

  checkPermissions() {
    const canCreate = app.hasPermission('create_product');
    const canEdit = app.hasPermission('edit_product');
    const canDelete = app.hasPermission('delete_product');
    const canChangeStatus = app.hasPermission('change_product_status');
    this.setData({ canCreate, canEdit, canDelete, canChangeStatus });
  },

  onPullDownRefresh() {
    this.setData({ page: 0, products: [], hasMore: true });
    this.loadProducts().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadProducts();
    }
  },

  async loadProducts() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loading: true });
    try {
      const params = { page: this.data.page, size: this.data.size };
      if (this.data.keyword) params.name = this.data.keyword;
      if (this.data.brand) params.brand = this.data.brand;
      if (this.data.category) params.category = this.data.category;

      const res = await productApi.list(params);
      let products = res.content || [];

      // 前端状态筛选
      if (this.data.activeStatus === 'active') {
        products = products.filter(p => p.isActive);
      } else if (this.data.activeStatus === 'inactive') {
        products = products.filter(p => !p.isActive);
      } else if (this.data.activeStatus === 'warning') {
        products = products.filter(p => p.stock < 10);
      }

      this.setData({
        products: this.data.page === 0 ? products : [...this.data.products, ...products],
        hasMore: products.length >= this.data.size,
        loading: false
      });
    } catch (err) {
      console.error('加载商品失败:', err);
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  // 搜索
  onSearch(e) {
    this.setData({ keyword: e.detail.value, page: 0, products: [], hasMore: true });
    this.loadProducts();
  },

  // 品牌搜索
  onBrandInput(e) {
    this.setData({ brand: e.detail.value });
  },

  // 切换筛选面板
  toggleFilter() {
    this.setData({ showFilter: !this.data.showFilter });
  },

  // 执行搜索
  doSearch() {
    this.setData({ showFilter: false, page: 0, products: [], hasMore: true });
    this.loadProducts();
  },

  // 分类选择
  onCategoryChange(e) {
    this.setData({ category: e.detail.value, page: 0, products: [], hasMore: true });
    this.loadProducts();
  },

  // 状态页签切换
  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ activeStatus: status, page: 0, products: [], hasMore: true });
    this.loadProducts();
  },

  // 重置筛选
  resetFilter() {
    this.setData({
      keyword: '',
      brand: '',
      category: '',
      activeStatus: 'all',
      page: 0,
      products: [],
      hasMore: true
    });
    this.loadProducts();
  },

  // 新增商品
  goToCreate() {
    wx.navigateTo({ url: '/pages/products/detail?action=create' });
  },

  // 查看详情
  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/products/detail?id=${id}` });
  },

  // 操作菜单
  showActionSheet(e) {
    const product = e.currentTarget.dataset.item;
    this.setData({ actionSheetVisible: true, currentProduct: product });
  },

  hideActionSheet() {
    this.setData({ actionSheetVisible: false, currentProduct: null });
  },

  // 编辑
  handleEdit() {
    if (!this.data.canEdit) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    this.hideActionSheet();
    wx.navigateTo({ url: `/pages/products/detail?id=${this.data.currentProduct.id}&action=edit` });
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
      content: `确定删除商品 "${this.data.currentProduct.name}"？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            await productApi.delete(this.data.currentProduct.id);
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.setData({ page: 0, products: [], hasMore: true });
            this.loadProducts();
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  },

  // 上下架
  async handleToggleStatus() {
    if (!this.data.canChangeStatus) {
      wx.showToast({ title: '无权限', icon: 'none' });
      return;
    }
    const product = this.data.currentProduct;
    const newStatus = !product.isActive;
    this.hideActionSheet();
    try {
      await productApi.updateStatus(product.id, newStatus);
      wx.showToast({ title: newStatus ? '已上架' : '已下架', icon: 'success' });
      this.setData({ page: 0, products: [], hasMore: true });
      this.loadProducts();
    } catch (err) {
      wx.showToast({ title: '操作失败', icon: 'none' });
    }
  }
});