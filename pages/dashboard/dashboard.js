// 首页/Dashboard
const app = getApp();
const { salesOrderApi, productApi, partnerApi } = require('../../utils/request');

Page({
  data: {
    userInfo: null,
    // 是否有主链观察权限
    canViewChain: false,
    stats: {
      pendingOrders: 0,
      totalOrders: 0,
      totalProducts: 0,
      totalPartners: 0,
      pendingAssignCount: 0
    },
    recentOrders: [],
    loading: true
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
    this.checkChainPermission();
    this.loadDashboard();
  },

  // 检查主链观察权限
  async checkChainPermission() {
    try {
      const { authApi } = require('../../utils/request');
      const userInfo = await authApi.getUserInfo();
      const company = userInfo.companyTitle || '';
      const role = userInfo.role || '';
      
      // 判断条件：(飞础科智慧科技 AND 业务员) OR (上海热像科技 AND (商务 OR 财务))
      const canViewChain = (
        (company.includes('飞础科智慧科技') && (role.includes('业务员') || role === 'SALES')) ||
        (company.includes('上海热像科技') && (role.includes('商务') || role.includes('财务') || role === 'BUSINESS' || role === 'FINANCE'))
      );
      
      this.setData({ canViewChain });
    } catch (err) {
      console.error('检查权限失败:', err);
    }
  },

  onShow() {
    // 每次显示刷新数据
    this.loadDashboard();
  },

  async loadDashboard() {
    this.setData({ loading: true });
    try {
      // 并行获取统计数据
      const [ordersRes, productsRes, partnersRes, pendingAssignRes] = await Promise.all([
        salesOrderApi.list({ page: 0, size: 5 }),
        productApi.list({ page: 0, size: 1 }),
        partnerApi.list({ page: 0, size: 1 }),
        salesOrderApi.list({ page: 0, size: 1, masterStatus: '待指派' })
      ]);
      
      this.setData({
        stats: {
          pendingOrders: ordersRes.content?.filter(o => ['PENDING', '待指派', '待确认'].includes(o.status || o.masterStatus)).length || 0,
          totalOrders: ordersRes.totalElements || 0,
          totalProducts: productsRes.totalElements || 0,
          totalPartners: partnersRes.totalElements || 0,
          pendingAssignCount: pendingAssignRes.totalElements || 0
        },
        recentOrders: ordersRes.content?.slice(0, 5) || [],
        loading: false
      });
    } catch (err) {
      console.error('加载Dashboard失败:', err);
      this.setData({ loading: false });
    }
  },

  // 跳转到订单列表
  goToOrders() {
    wx.switchTab({ url: '/pages/orders/orders' });
  },

  // 跳转到待指派列表
  goToPendingAssign() {
    wx.switchTab({ url: '/pages/pending-assign/list' });
  },

  // 跳转到合同模板
  goToContractTemplates() {
    wx.navigateTo({ url: '/pages/contract-templates/list' });
  },

  // 跳转到对账列表
  goToReconciliation() {
    wx.navigateTo({ url: '/pages/settlements/settlements' });
  },

  // 跳转到合同列表
  goToContracts() {
    wx.navigateTo({ url: '/pages/contracts/contracts' });
  },

  // 跳转到商品列表
  goToProducts() {
    wx.switchTab({ url: '/pages/products/products' });
  },

  // 跳转到合作伙伴
  goToPartners() {
    wx.switchTab({ url: '/pages/partners/partners' });
  },

  // 跳转到主链观察（需要权限）
  goToChainObserve() {
    wx.navigateTo({ url: '/pages/chain/chain' });
  },

  // 跳转到合作方管理
  goToPartnerManage() {
    wx.navigateTo({ url: '/pages/partners/partners' });
  },

  // 跳转到订单详情
  goToOrderDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}` });
  },

  // 登出
  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          wx.redirectTo({ url: '/pages/login/login' });
        }
      }
    });
  }
});