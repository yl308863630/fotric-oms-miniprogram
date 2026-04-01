<template>
  <el-container class="layout-container">
    <!-- 桌面端：固定侧栏 -->
    <el-aside v-if="!isMobile" :width="sidebarCollapsed ? '60px' : '220px'" class="aside">
      <div class="logo">
        <div class="logo-row">
          <img v-show="!logoSrcFail" src="/logo.png" alt="FOTRIC" class="logo-img" @error="logoSrcFail = true" />
          <el-icon v-if="logoSrcFail" size="28" color="#fff"><Platform /></el-icon>
        </div>
        <span v-if="!sidebarCollapsed" class="logo-title">OMS订单系统</span>
      </div>
      <el-menu
        :default-active="activePath"
        class="el-menu-vertical"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409EFF"
        :collapse="sidebarCollapsed"
      >
        <el-menu-item v-if="hasPermission('dashboard')" index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item v-if="hasPermission('product')" index="/product">
          <el-icon><Goods /></el-icon>
          <span>我的商品</span>
        </el-menu-item>
        <el-sub-menu v-if="hasPermission('opportunity')" index="/opportunity">
          <template #title>
            <el-icon><Promotion /></el-icon>
            <span>商机</span>
          </template>
          <el-menu-item index="/opportunity/list">商机管理</el-menu-item>
          <el-menu-item index="/opportunity/quotation-template">报价单模板管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showSalesMenu" index="/sales">
          <template #title>
            <el-icon><Sell /></el-icon>
            <span>销售管理</span>
          </template>
          <el-menu-item v-if="hasPermission('sales')" index="/sales">销售订单</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/sales/reconciliation">销售对账单</el-menu-item>
          <el-menu-item v-if="canAccessSalesInvoice" index="/invoice/output">销项发票</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/sales/settlement">销售结算单</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/chain/observe">链路观察</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showPurchaseMenu" index="/purchase">
          <template #title>
            <el-icon><ShoppingCart /></el-icon>
            <span>采购管理</span>
          </template>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase">采购订单</el-menu-item>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase/reconciliation">采购对账单</el-menu-item>
          <el-menu-item v-if="canAccessPurchaseInvoice" index="/invoice/input">进项发票</el-menu-item>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase/settlement">采购结算单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasPermission('cooperation')" index="/cooperation">
          <template #title>
            <el-icon><Avatar /></el-icon>
            <span>合作管理</span>
          </template>
          <el-menu-item index="/cooperation/contract">合同管理</el-menu-item>
          <el-menu-item index="/cooperation/contract-template">合同模板管理</el-menu-item>
          <el-menu-item index="/cooperation/partner-info">用户信息维护</el-menu-item>
          <el-menu-item index="/cooperation/party-a-payment-rules">甲方回款规则</el-menu-item>
          <el-menu-item v-if="canManageSubjectGroups" index="/cooperation/subject-account-groups">手工归组</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasPermission('authorization')" index="/authorization">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>平台授权</span>
          </template>
          <el-menu-item index="/authorization/list">授权统计</el-menu-item>
          <el-menu-item index="/authorization/scan-logs">扫码追踪</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showUserMenu" index="/user">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </template>
          <el-menu-item index="/user/list">用户列表</el-menu-item>
          <el-menu-item v-if="isAdmin" index="/user/login-security">登录安全</el-menu-item>
          <el-menu-item index="/user/privacy-logs">隐私访问记录</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 移动端：侧栏抽屉 -->
    <el-drawer
      v-model="mobileDrawerVisible"
      direction="ltr"
      size="280px"
      class="mobile-drawer"
      :with-header="false"
    >
      <div class="drawer-logo">
        <div class="drawer-logo-row">
          <img v-show="!drawerLogoSrcFail" src="/logo.png" alt="FOTRIC" class="drawer-logo-img" @error="drawerLogoSrcFail = true" />
          <el-icon v-if="drawerLogoSrcFail" size="26" color="#409EFF"><Platform /></el-icon>
        </div>
        <span class="drawer-logo-title">OMS订单系统</span>
      </div>
      <el-menu
        :default-active="activePath"
        class="el-menu-vertical drawer-menu"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409EFF"
        @select="mobileDrawerVisible = false"
      >
        <el-menu-item v-if="hasPermission('dashboard')" index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item v-if="hasPermission('product')" index="/product">
          <el-icon><Goods /></el-icon>
          <span>我的商品</span>
        </el-menu-item>
        <el-sub-menu v-if="hasPermission('opportunity')" index="/opportunity">
          <template #title>
            <el-icon><Promotion /></el-icon>
            <span>商机</span>
          </template>
          <el-menu-item index="/opportunity/list">商机管理</el-menu-item>
          <el-menu-item index="/opportunity/quotation-template">报价单模板管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showSalesMenu" index="/sales">
          <template #title>
            <el-icon><Sell /></el-icon>
            <span>销售管理</span>
          </template>
          <el-menu-item v-if="hasPermission('sales')" index="/sales">销售订单</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/sales/reconciliation">销售对账单</el-menu-item>
          <el-menu-item v-if="canAccessSalesInvoice" index="/invoice/output">销项发票</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/sales/settlement">销售结算单</el-menu-item>
          <el-menu-item v-if="hasPermission('sales')" index="/chain/observe">链路观察</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showPurchaseMenu" index="/purchase">
          <template #title>
            <el-icon><ShoppingCart /></el-icon>
            <span>采购管理</span>
          </template>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase">采购订单</el-menu-item>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase/reconciliation">采购对账单</el-menu-item>
          <el-menu-item v-if="canAccessPurchaseInvoice" index="/invoice/input">进项发票</el-menu-item>
          <el-menu-item v-if="hasPermission('purchase')" index="/purchase/settlement">采购结算单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasPermission('cooperation')" index="/cooperation">
          <template #title>
            <el-icon><Avatar /></el-icon>
            <span>合作管理</span>
          </template>
          <el-menu-item index="/cooperation/contract">合同管理</el-menu-item>
          <el-menu-item index="/cooperation/contract-template">合同模板管理</el-menu-item>
          <el-menu-item index="/cooperation/partner-info">用户信息维护</el-menu-item>
          <el-menu-item index="/cooperation/party-a-payment-rules">甲方回款规则</el-menu-item>
          <el-menu-item v-if="canManageSubjectGroups" index="/cooperation/subject-account-groups">手工归组</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasPermission('authorization')" index="/authorization">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>平台授权</span>
          </template>
          <el-menu-item index="/authorization/list">授权统计</el-menu-item>
          <el-menu-item index="/authorization/scan-logs">扫码追踪</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="showUserMenu" index="/user">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </template>
          <el-menu-item index="/user/list">用户列表</el-menu-item>
          <el-menu-item v-if="isAdmin" index="/user/login-security">登录安全</el-menu-item>
          <el-menu-item index="/user/privacy-logs">隐私访问记录</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-drawer>

    <el-container class="main-wrapper">
      <el-header class="header">
        <div class="header-left">
          <!-- 移动端：菜单按钮打开抽屉 -->
          <el-icon v-if="isMobile" class="fold-btn menu-btn" @click="mobileDrawerVisible = true"><Menu /></el-icon>
          <!-- 桌面端：折叠按钮 -->
          <el-icon v-else class="fold-btn" @click="toggleSidebar"><Fold /></el-icon>
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRouteTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              {{ username }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 多页签 -->
      <div class="tags-container">
        <el-tabs
          v-model="activePath"
          type="card"
          class="tags-tabs"
          @tab-click="handleTabClick"
          @tab-remove="handleTabRemove"
        >
          <el-tab-pane
            v-for="item in tagStore.tags"
            :key="item.path"
            :label="item.title"
            :name="item.path"
            :closable="item.path !== '/dashboard'"
          />
        </el-tabs>
      </div>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagStore } from '../store/tag'
import { 
  HomeFilled, 
  Goods, 
  Sell, 
  ShoppingCart, 
  Avatar,
  User,
  ArrowDown,
  Fold,
  Menu,
  Platform,
  Promotion,
  Document
} from '@element-plus/icons-vue'
import { useBreakpoint } from '../composables/useBreakpoint'

const { isMobile } = useBreakpoint()
const route = useRoute()
const router = useRouter()
const tagStore = useTagStore()

const activePath = computed({
  get: () => tagStore.activePath,
  set: (val) => tagStore.setActivePath(val)
})

const currentRouteTitle = computed(() => route.meta.title || '首页')

// 侧边栏折叠状态（桌面端）
const sidebarCollapsed = ref(false)
// 移动端抽屉显隐
const mobileDrawerVisible = ref(false)
// logo 图片加载失败时显示图标占位
const logoSrcFail = ref(false)
const drawerLogoSrcFail = ref(false)

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 路由变化时关闭移动端抽屉
watch(() => route.path, () => {
  mobileDrawerVisible.value = false
})

// 监听路由变化，添加页签
watch(() => route.path, (newPath) => {
  if (route.name) {
    tagStore.addTag({
      name: route.name as string,
      path: newPath,
      title: route.meta.title as string || '首页'
    })
  }
}, { immediate: true })

const handleTabClick = (tab: any) => {
  router.push(tab.props.name)
}

const username = ref(localStorage.getItem('username') || '管理员')

// 权限列表（与用户管理里权限配置的 label 一致：dashboard, product, opportunity, sales, purchase, warehouse, settlement, cooperation, user）
const permissionSet = computed(() => {
  const raw = localStorage.getItem('permissions') || ''
  return new Set(raw.split(',').map((s: string) => s.trim()).filter(Boolean))
})
const isAdmin = computed(() => localStorage.getItem('username') === 'admin')
const companyTitle = computed(() => (localStorage.getItem('companyTitle') || '').trim())
const hasPermission = (perm: string) => isAdmin.value || permissionSet.value.has(perm)
const canAccessSalesInvoice = computed(() => hasPermission('sales') || hasPermission('settlement'))
const canAccessPurchaseInvoice = computed(() => hasPermission('purchase') || hasPermission('settlement'))
const showSalesMenu = computed(() => hasPermission('sales') || hasPermission('settlement'))
const showPurchaseMenu = computed(() => hasPermission('purchase') || hasPermission('settlement'))
const canManageSubjectGroups = computed(() => {
  return hasPermission('cooperation') && (isAdmin.value || companyTitle.value.includes('飞础科智慧科技（上海）有限公司'))
})

const showUserMenu = computed(() => {
  if (isAdmin.value) return true
  const role = localStorage.getItem('role') || ''
  return role === 'ROLE_DELIVERY' && permissionSet.value.has('user')
})

const handleCommand = (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    router.push('/login')
  }
}

const handleTabRemove = (path: string) => {
  tagStore.removeTag(path)
  if (activePath.value !== route.path) {
    router.push(activePath.value)
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.aside {
  background-color: #001529;
  color: #fff;
  transition: width 0.3s;
}
.logo {
  min-height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: bold;
  font-size: 13px;
  color: #fff;
  border-bottom: 1px solid #1f2d3d;
  padding: 8px 12px;
  min-width: 0;
}
.logo-row {
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-img {
  height: 28px;
  width: auto;
  max-width: 100px;
  flex-shrink: 0;
  object-fit: contain;
}
.logo-title {
  white-space: nowrap;
  line-height: 1.2;
}
.el-menu-vertical {
  border-right: none;
}
.header {
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 50px !important;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}
.fold-btn {
  font-size: 20px;
  cursor: pointer;
}
.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
}
.tags-container {
  background: #fff;
  padding: 6px 15px 0;
  border-bottom: 1px solid #dcdfe6;
}
.tags-tabs {
  border-bottom: none;
}
:deep(.el-tabs--card > .el-tabs__header) {
  border-bottom: none;
  margin: 0;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__nav) {
  border: none;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__item) {
  border: 1px solid #dcdfe6;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  margin-right: 5px;
  height: 32px;
  line-height: 32px;
  background: #f5f7fa;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__item.is-active) {
  background: #fff;
  border-bottom: 1px solid #fff;
}
.main {
  background-color: #f0f2f5;
  padding: 15px;
}
.main-wrapper {
  flex: 1;
  min-width: 0;
}
.drawer-logo {
  min-height: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: bold;
  font-size: 13px;
  color: #001529;
  border-bottom: 1px solid #eee;
  padding: 8px 16px;
  min-width: 0;
}
.drawer-logo-row {
  display: flex;
  align-items: center;
  justify-content: center;
}
.drawer-logo-img {
  height: 26px;
  width: auto;
  max-width: 90px;
  flex-shrink: 0;
  object-fit: contain;
}
.drawer-logo-title {
  white-space: nowrap;
  line-height: 1.2;
}
.drawer-menu {
  border-right: none;
}
.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background-color: #001529;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    padding: 0 12px;
    height: 50px !important;
  }
  .fold-btn.menu-btn {
    min-width: 44px;
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin: -4px 0 -4px -8px;
    padding: 4px 8px;
  }
  .breadcrumb {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 140px;
  }
  .user-info {
    min-height: 44px;
    align-items: center;
    padding: 4px 0;
  }
  .tags-container {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    padding: 6px 12px 0;
  }
  .tags-container :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
    overflow-y: hidden;
  }
  .tags-container :deep(.el-tabs__nav-scroll) {
    overflow: visible;
  }
  .main {
    padding: 10px 12px;
  }
}
</style>
