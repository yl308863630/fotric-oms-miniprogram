// 主链观察页面
const { salesOrderApi } = require('../../utils/request');

Page({
  data: {
    searchKeyword: '',
    loading: false,
    masterOrders: [],
    selectedMaster: null,
    allocations: [],
    currentTab: 'master', // master/chain
    // 权限
    canViewChain: false
  },

  onLoad() {
    this.checkPermission();
    this.loadAllMasterOrders();
  },

  async checkPermission() {
    try {
      const { authApi } = require('../../utils/request');
      const userInfo = await authApi.getUserInfo();
      const company = userInfo.companyTitle || '';
      const role = userInfo.role || '';
      
      const canViewChain = (
        (company.includes('飞础科智慧科技') && (role.includes('业务员') || role === 'SALES')) ||
        (company.includes('上海热像科技') && (role.includes('商务') || role.includes('财务') || role === 'BUSINESS' || role === 'FINANCE'))
      );
      
      if (!canViewChain) {
        wx.showToast({ title: '无权限访问', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1500);
        return;
      }
      
      this.setData({ canViewChain: true });
    } catch (err) {
      console.error('检查权限失败:', err);
    }
  },

  async loadAllMasterOrders() {
    this.setData({ loading: true });
    try {
      const res = await salesOrderApi.list({ page: 0, size: 100 });
      // 获取主单列表
      const masters = (res.content || []).filter(o => o.isMaster || o.masterId === null);
      this.setData({ 
        masterOrders: masters,
        loading: false 
      });
    } catch (err) {
      console.error('加载主单失败:', err);
      this.setData({ loading: false });
    }
  },

  onSearch(e) {
    const keyword = e.detail.value.trim();
    this.setData({ searchKeyword: keyword });
    if (keyword) {
      this.searchOrder(keyword);
    }
  },

  async searchOrder(keyword) {
    this.setData({ loading: true });
    try {
      // 搜索OMS单号
      let res = await salesOrderApi.list({ page: 0, size: 20, keyword });
      let orders = res.content || [];
      
      // 如果没搜到，尝试作为主单号搜索
      if (!orders.length) {
        res = await salesOrderApi.list({ page: 0, size: 20, masterNo: keyword });
        orders = res.content || [];
      }
      
      // 找到主单
      const master = orders.find(o => o.isMaster || o.masterId === null);
      if (master) {
        this.selectMaster(master);
      } else if (orders.length > 0) {
        // 如果找到的是子单，找主单
        const masterId = orders[0].masterId;
        if (masterId) {
          const masterRes = await salesOrderApi.get(masterId);
          this.selectMaster(masterRes);
        }
      } else {
        wx.showToast({ title: '未找到订单', icon: 'none' });
      }
      
      this.setData({ loading: false });
    } catch (err) {
      console.error('搜索失败:', err);
      wx.showToast({ title: '搜索失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  selectMaster(master) {
    this.setData({ 
      selectedMaster: master,
      currentTab: 'chain'
    });
    // 获取关联的子单/配货单
    this.loadAllocations(master.id);
  },

  async loadAllocations(masterId) {
    try {
      // 这里调用后端获取配货单/子单列表
      const res = await salesOrderApi.list({ page: 0, size: 50, masterId });
      const allocations = (res.content || []).filter(o => !o.isMaster && o.masterId);
      this.setData({ allocations });
    } catch (err) {
      console.error('加载配货单失败:', err);
    }
  },

  onTabChange(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ currentTab: tab });
  },

  goToOrderDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/orders/detail?id=${id}` });
  },

  goBack() {
    this.setData({ 
      selectedMaster: null,
      allocations: [],
      currentTab: 'master'
    });
  }
});