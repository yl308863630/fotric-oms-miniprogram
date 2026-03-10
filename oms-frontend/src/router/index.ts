import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('../views/product/ProductList.vue'),
        meta: { title: '我的商品', icon: 'Goods' }
      },
      {
        path: 'opportunity',
        name: 'Opportunity',
        component: () => import('../views/opportunity/OpportunityList.vue'),
        meta: { title: '商机管理', icon: 'Promotion' }
      },
      {
        path: 'sales',
        name: 'Sales',
        component: () => import('../views/sales/OrderList.vue'),
        meta: { title: '销售管理', icon: 'Sell' }
      },
      {
        path: 'purchase',
        name: 'Purchase',
        component: () => import('../views/purchase/PurchaseList.vue'),
        meta: { title: '采购管理', icon: 'ShoppingCart' }
      },
      {
        path: 'settlement',
        name: 'Settlement',
        redirect: '/settlement/invoice',
        meta: { title: '客户结算', icon: 'Money' },
        children: [
          {
            path: 'invoice',
            name: 'InvoiceList',
            component: () => import('../views/settlement/InvoiceList.vue'),
            meta: { title: '对账单' }
          },
          {
            path: 'list',
            name: 'SettlementList',
            component: () => import('../views/settlement/SettlementList.vue'),
            meta: { title: '结算单' }
          }
        ]
      },
      {
        path: 'cooperation',
        name: 'Cooperation',
        redirect: '/cooperation/contract',
        meta: { title: '合作管理', icon: 'Avatar' },
        children: [
          {
            path: 'contract',
            name: 'ContractList',
            component: () => import('../views/cooperation/ContractList.vue'),
            meta: { title: '合同管理' }
          },
          {
            path: 'contract-template',
            name: 'ContractTemplateList',
            component: () => import('../views/cooperation/ContractTemplateList.vue'),
            meta: { title: '合同模板管理' }
          },
          {
            path: 'partner-info',
            name: 'PartnerInfoList',
            component: () => import('../views/cooperation/PartnerInfoList.vue'),
            meta: { title: '用户信息维护' }
          }
        ]
      },
      {
        path: 'user',
        name: 'UserManagement',
        redirect: '/user/list',
        meta: { title: '用户管理', icon: 'User' },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('../views/user/UserList.vue'),
            meta: { title: '用户列表' }
          }
        ]
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    if (token) {
      next('/')
    } else {
      next()
    }
  } else if (to.path === '/product' || to.path === '/sales') {
    // 允许无需token访问我的商品和销售管理页面
    next()
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
