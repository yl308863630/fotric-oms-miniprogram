<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <img src="/logo.png" alt="FOTRIC" class="login-logo" />
        <h2>飞础科(FOTRIC)OMS 订单管理系统</h2>
        <p>请登录您的账号</p>
      </div>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-position="top" class="login-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password size="large" />
        </el-form-item>
        <el-alert
          v-if="!captchaRequired"
          title="当前网络已在白名单中，可直接登录，无需填写验证码。"
          type="success"
          :closable="false"
          show-icon
          class="captcha-bypass-alert"
        />
        <el-form-item v-if="captchaRequired" label="验证码" prop="captchaValue">
          <div class="captcha-row">
            <div class="captcha-image-wrap" @click="captchaLoadFailed && loadCaptcha()">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" class="captcha-image" />
              <span v-else-if="captchaLoadFailed" class="captcha-failed">加载失败，点击刷新</span>
              <span v-else class="captcha-placeholder">加载中...</span>
            </div>
            <el-button type="default" class="captcha-refresh" @click="loadCaptcha" :loading="captchaLoading">刷新</el-button>
            <el-input v-model="loginForm.captchaValue" placeholder="请输入验证码" class="captcha-input" maxlength="4" show-word-limit size="large" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <el-link type="info" underline="never" @click="forgotPasswordVisible = true">忘记密码？</el-link>
      </div>
    </div>

    <el-dialog v-model="forgotPasswordVisible" title="忘记密码" width="400px" align-center>
      <p class="forgot-tip">忘记密码请联系系统管理员（<a href="mailto:yangwang@fotric.cn" class="forgot-email">yangwang@fotric.cn</a>）重置。管理员可在【用户管理-用户列表】中为您重置密码。</p>
      <template #footer>
        <el-button type="primary" @click="forgotPasswordVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()

onMounted(() => {
  document.title = '飞础科(FOTRIC)OMS 订单管理系统 - 登录'
  loadCaptcha()
})

const loginFormRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)
const captchaLoadFailed = ref(false)
const captchaRequired = ref(true)
const forgotPasswordVisible = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  captchaKey: '',
  captchaValue: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaValue: [{
    validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
      if (!captchaRequired.value || value) {
        callback()
        return
      }
      callback(new Error('请输入验证码'))
    },
    trigger: 'blur'
  }]
}

async function loadCaptcha() {
  captchaLoading.value = true
  captchaLoadFailed.value = false
  try {
    const res = await axios.get('/api/auth/captcha', {
      headers: { Accept: 'application/json' },
      responseType: 'json',
      timeout: 10000
    })
    captchaRequired.value = !res.data.skipCaptcha
    captchaKey.value = res.data.captchaKey || ''
    captchaImage.value = res.data.image || ''
    loginForm.captchaKey = captchaKey.value
    loginForm.captchaValue = ''
  } catch (e) {
    captchaRequired.value = true
    captchaLoadFailed.value = true
    ElMessage.error('验证码加载失败，请检查网络或联系管理员')
  } finally {
    captchaLoading.value = false
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }
  loginForm.captchaKey = captchaKey.value

  loading.value = true
  try {
    const res = await axios.post('/api/auth/login', {
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: loginForm.captchaKey,
      captchaValue: loginForm.captchaValue
    }, {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      responseType: 'json'
    })
    const data = res.data
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    if (data.realName) localStorage.setItem('realName', data.realName)
    if (data.companyTitle) localStorage.setItem('companyTitle', data.companyTitle)
    if (data.role) localStorage.setItem('role', data.role)
    if (data.permissions != null) {
      localStorage.setItem('permissions', typeof data.permissions === 'string' ? data.permissions : (data.permissions || []).join(','))
    }
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error: any) {
    const msg = error.response?.data?.message || error.message || '登录失败'
    ElMessage.error(msg)
    if (msg === '验证码错误') loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f8f9fa;
  padding: 24px;
}
.login-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  padding: 40px 36px;
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-logo {
  height: 44px;
  width: auto;
  margin-bottom: 16px;
  display: block;
  margin-left: auto;
  margin-right: auto;
}
.login-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
}
.login-header p {
  color: #666;
  margin: 8px 0 0;
  font-size: 14px;
}
.login-form {
  margin-top: 8px;
}
.login-form :deep(.el-form-item__label) {
  color: #333;
  font-weight: 500;
}
.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.captcha-bypass-alert {
  margin-bottom: 18px;
}
.captcha-image-wrap {
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  cursor: default;
}
.captcha-image-wrap:has(.captcha-failed) {
  cursor: pointer;
}
.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: fill;
}
.captcha-placeholder {
  font-size: 12px;
  color: #909399;
}
.captcha-failed {
  font-size: 11px;
  color: #f56c6c;
  cursor: pointer;
}
.captcha-refresh {
  flex-shrink: 0;
}
.captcha-input {
  flex: 1;
  min-width: 120px;
}
.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 8px;
  background-color: #c00;
  border-color: #c00;
}
.login-button:hover,
.login-button:focus {
  background-color: #a00;
  border-color: #a00;
}
.login-footer {
  text-align: center;
  margin-top: 20px;
}
.login-footer .el-link {
  color: #909399;
  font-size: 13px;
}
.forgot-tip {
  margin: 0;
  line-height: 1.6;
  color: #606266;
}
.forgot-tip .forgot-email {
  color: #409eff;
  text-decoration: none;
}
.forgot-tip .forgot-email:hover {
  text-decoration: underline;
}
@media (max-width: 768px) {
  .login-card {
    padding: 28px 20px;
    margin: 16px;
  }
  .captcha-row {
    flex-direction: column;
    align-items: stretch;
  }
  .captcha-input {
    min-width: 0;
  }
  .login-button {
    min-height: 44px;
  }
}
</style>
