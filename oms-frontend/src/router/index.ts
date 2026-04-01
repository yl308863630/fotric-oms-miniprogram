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
    path: '/authorization/verify',
    name: 'AuthorizationVerify',
    component: () => import('../views/authorization/AuthorizationVerify.vue'),
    meta: { title: '授权验真' }
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
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { title: '个人中心' }
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
        redirect: '/opportunity/list',
        meta: { title: '商机', icon: 'Promotion' },
        children: [
          {
            path: 'list',
            name: 'OpportunityList',
            component: () => import('../views/opportunity/OpportunityList.vue'),
            meta: { title: '商机管理' }
          },
          {
            path: 'quotation-template',
            name: 'QuotationTemplateList',
            component: () => import('../views/opportunity/QuotationTemplateList.vue'),
            meta: { title: '报价单模板管理' }
          }
        ]
      },
      {
        path: 'sales',
        name: 'Sales',
        component: () => import('../views/sales/OrderList.vue'),
        meta: { title: '销售管理', icon: 'Sell' }
      },
      {
        path: 'sales/reconciliation',
        name: 'SalesReconciliationList',
        component: () => import('../views/sales/SalesReconciliationList.vue'),
        meta: { title: '销售对账单' }
      },
      {
        path: 'sales/settlement',
        name: 'SalesSettlementListNew',
        component: () => import('../views/sales/SalesSettlementList.vue'),
        meta: { title: '销售结算单' }
      },
      {
        path: 'chain/observe',
        name: 'ChainObserver',
        component: () => import('../views/chain/ChainObserver.vue'),
        meta: { title: '链路观察' }
      },
      {
        path: 'purchase',
        name: 'Purchase',
        component: () => import('../views/purchase/PurchaseList.vue'),
        meta: { title: '采购管理', icon: 'ShoppingCart' }
      },
      {
        path: 'purchase/reconciliation',
        name: 'PurchaseReconciliationList',
        component: () => import('../views/purchase/PurchaseReconciliationList.vue'),
        meta: { title: '采购对账单' }
      },
      {
        path: 'purchase/settlement',
        name: 'PurchaseSettlementListNew',
        component: () => import('../views/purchase/PurchaseSettlementList.vue'),
        meta: { title: '采购结算单' }
      },
      {
        path: 'invoice',
        name: 'InvoiceManagement',
        redirect: '/invoice/output',
        meta: { title: '发票管理', icon: 'Tickets' },
        children: [
          {
            path: 'output',
            name: 'SalesOutputInvoiceList',
            component: () => import('../views/invoice/SalesOutputInvoiceList.vue'),
            meta: { title: '销项发票', requiredPermissions: ['sales', 'settlement'] }
          },
          {
            path: 'input',
            name: 'PurchaseInputInvoiceList',
            component: () => import('../views/invoice/PurchaseInputInvoiceList.vue'),
            meta: { title: '进项发票', requiredPermissions: ['purchase', 'settlement'] }
          }
        ]
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
          },
          {
            path: 'party-a-payment-rules',
            name: 'PartyAPaymentRuleList',
            component: () => import('../views/cooperation/PartyAPaymentRuleList.vue'),
            meta: { title: '甲方回款规则' }
          },
          {
            path: 'subject-account-groups',
            name: 'SubjectAccountGroupList',
            component: () => import('../views/cooperation/SubjectAccountGroupList.vue'),
            meta: { title: '手工归组', requiredPermissions: ['cooperation'] }
          }
        ]
      },
      {
        path: 'authorization',
        name: 'Authorization',
        redirect: '/authorization/list',
        meta: { title: '平台授权', icon: 'Document' },
        children: [
          {
            path: 'list',
            name: 'AuthorizationList',
            component: () => import('../views/authorization/AuthorizationList.vue'),
            meta: { title: '授权统计' }
          },
          {
            path: 'scan-logs',
            name: 'AuthorizationScanLogList',
            component: () => import('../views/authorization/AuthorizationScanLogList.vue'),
            meta: { title: '扫码追踪' }
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
          },
          {
            path: 'login-security',
            name: 'LoginSecurity',
            component: () => import('../views/user/LoginSecurity.vue'),
            meta: { title: '登录安全' }
          },
          {
            path: 'privacy-logs',
            name: 'PrivacyLogList',
            component: () => import('../views/user/PrivacyLogList.vue'),
            meta: { title: '隐私访问记录' }
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

const APP_TITLE = '飞础科(FOTRIC)OMS 订单管理系统'
const AUTH_VERIFY_APP_TITLE = '飞础科(FOTRIC)授权验真系统'

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const username = localStorage.getItem('username') || ''
  const rawPermissions = localStorage.getItem('permissions') || ''
  const permissionSet = new Set(rawPermissions.split(',').map((s: string) => s.trim()).filter(Boolean))
  const isAdmin = username === 'admin'
  if (to.path.startsWith('/authorization/verify')) {
    next()
    return
  }
  if (to.path === '/login') {
    if (token) {
      next('/')
    } else {
      next()
    }
  } else {
    if (token) {
      const requiredPermissions = Array.isArray(to.meta?.requiredPermissions)
        ? (to.meta.requiredPermissions as string[])
        : []
      if (!isAdmin && requiredPermissions.length > 0) {
        const hasMatchedPermission = requiredPermissions.some((perm) => permissionSet.has(perm))
        if (!hasMatchedPermission) {
          next(from.path && from.path !== to.path ? from.fullPath : '/dashboard')
          return
        }
      }
      next()
    } else {
      next('/login')
    }
  }
})

router.afterEach((to) => {
  if (to.path.startsWith('/authorization/verify')) {
    document.title = AUTH_VERIFY_APP_TITLE
    return
  }
  if (to.path === '/login') {
    document.title = `${APP_TITLE} - 登录`
  } else {
    const title = (to.meta?.title as string) || '首页'
    document.title = title ? `${APP_TITLE} - ${title}` : APP_TITLE
  }
})

export default router
