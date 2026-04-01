// 个人中心页面
const app = getApp();

Page({
  data: {
    userInfo: null,
    menuItems: [
      { id: 'orders', title: '我的订单', icon: 'order' },
      { id: 'contracts', title: '我的合同', icon: 'contract' },
      { id: 'settlements', title: '对账结算', icon: 'settle' },
      { id: 'products', title: '商品管理', icon: 'product' },
      { id: 'partners', title: '合作方管理', icon: 'partner' }
    ]
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
  },

  onShow() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
  },

  // 点击菜单项
  onMenuTap(e) {
    const { id } = e.currentTarget.dataset;
    switch (id) {
      case 'orders':
        wx.switchTab({ url: '/pages/orders/orders' });
        break;
      case 'contracts':
        wx.navigateTo({ url: '/pages/contracts/contracts' });
        break;
      case 'settlements':
        wx.switchTab({ url: '/pages/settlements/settlements' });
        break;
      case 'products':
        wx.switchTab({ url: '/pages/products/products' });
        break;
      case 'partners':
        wx.switchTab({ url: '/pages/partners/partners' });
        break;
    }
  },

  // 退出登录
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