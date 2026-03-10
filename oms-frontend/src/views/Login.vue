<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="login-header">
          <h2>OMS 订单管理系统</h2>
          <p>请登录您的账号</p>
        </div>
      </template>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <el-link type="info" underline="never">忘记密码？</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const loginFormRef = ref()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  console.log('handleLogin called')
  console.log('Login form:', loginForm)
  
  loading.value = true
  try {
    console.log('Attempting login with username:', loginForm.username)
    // 使用相对路径，与代理/外网访问一致
    const res = await axios.post('/api/auth/login', loginForm, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    console.log('Login response:', res)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('username', res.data.username)
    if (res.data.realName) {
      localStorage.setItem('realName', res.data.realName)
    }
    if (res.data.companyTitle) {
      localStorage.setItem('companyTitle', res.data.companyTitle)
    }
    if (res.data.role) {
      localStorage.setItem('role', res.data.role)
    }
    if (res.data.permissions != null) {
      localStorage.setItem('permissions', typeof res.data.permissions === 'string' ? res.data.permissions : (res.data.permissions || []).join(','))
    }
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error: any) {
    console.error('Login error:', error)
    console.error('Error response:', error.response)
    console.error('Error message:', error.message)
    ElMessage.error(error.response?.data?.message || error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f5f7fa;
  background-image: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  border-radius: 12px;
}
.login-header {
  text-align: center;
}
.login-header h2 {
  margin: 0;
  color: #303133;
}
.login-header p {
  color: #909399;
  margin: 10px 0 0;
}
.login-button {
  width: 100%;
  height: 40px;
  font-size: 16px;
  margin-top: 10px;
}
.login-footer {
  text-align: center;
  margin-top: 20px;
}
</style>
