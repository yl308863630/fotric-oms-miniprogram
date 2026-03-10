<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarCollapsed ? '60px' : '220px'" class="aside">
      <div class="logo">
        <el-icon size="30" color="#409EFF"><Platform /></el-icon>
        <span v-if="!sidebarCollapsed">OMS 集成平台</span>
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
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/product">
          <el-icon><Goods /></el-icon>
          <span>我的商品</span>
        </el-menu-item>
        <el-menu-item index="/opportunity">
          <el-icon><Promotion /></el-icon>
          <span>商机管理</span>
        </el-menu-item>
        <el-menu-item index="/sales">
          <el-icon><Sell /></el-icon>
          <span>销售管理</span>
        </el-menu-item>
        <el-menu-item index="/purchase">
          <el-icon><ShoppingCart /></el-icon>
          <span>采购管理</span>
        </el-menu-item>
        <el-sub-menu index="/settlement">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>客户结算</span>
          </template>
          <el-menu-item index="/settlement/invoice">对账单</el-menu-item>
          <el-menu-item index="/settlement/list">结算单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/cooperation">
          <template #title>
            <el-icon><Avatar /></el-icon>
            <span>合作管理</span>
          </template>
          <el-menu-item index="/cooperation/contract">合同管理</el-menu-item>
          <el-menu-item index="/cooperation/contract-template">合同模板管理</el-menu-item>
          <el-menu-item index="/cooperation/partner">客户合同</el-menu-item>
          <el-menu-item index="/cooperation/partner-info">用户信息维护</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/user" v-if="showUserMenu">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </template>
          <el-menu-item index="/user/list">用户列表</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="fold-btn" @click="toggleSidebar"><Fold /></el-icon>
          <el-breadcrumb separator="/">
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
  Money,
  Avatar,
  User,
  ArrowDown,
  Fold,
  Platform,
  Promotion
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const tagStore = useTagStore()

const activePath = computed({
  get: () => tagStore.activePath,
  set: (val) => tagStore.setActivePath(val)
})

const currentRouteTitle = computed(() => route.meta.title || '首页')

// 侧边栏折叠状态
const sidebarCollapsed = ref(false)

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

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
const showUserMenu = computed(() => {
  if (localStorage.getItem('username') === 'admin') return true
  const role = localStorage.getItem('role') || ''
  const perms = localStorage.getItem('permissions') || ''
  return role === 'ROLE_DELIVERY' && perms.split(',').map((s: string) => s.trim()).includes('user')
})

const handleCommand = (command: string) => {
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
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
  color: #fff;
  border-bottom: 1px solid #1f2d3d;
}
.logo img {
  width: 32px;
  margin-right: 10px;
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
</style>
