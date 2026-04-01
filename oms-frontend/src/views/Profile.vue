<template>
  <div class="profile-page">
    <el-card class="info-card">
      <template #header>
        <span>个人信息</span>
      </template>
      <div v-loading="loading" class="info-body">
        <el-form v-if="me" ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px" style="max-width: 520px;">
          <el-form-item label="用户名">
            <el-input :model-value="me.username || '-'" disabled />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="profileForm.realName" placeholder="请输入真实姓名" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱（可选）" />
          </el-form-item>
          <el-form-item label="所属部门" prop="department">
            <el-input v-model="profileForm.department" placeholder="请输入所属部门（可选）" />
          </el-form-item>
          <el-form-item label="公司抬头">
            <el-input :model-value="me.companyTitle || '-'" disabled />
          </el-form-item>
          <el-form-item label="角色">
            <el-input :model-value="roleLabel(me.role)" disabled />
          </el-form-item>
          <el-form-item label="权限">
            <el-input :model-value="permissionsSummary" disabled />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileLoading" @click="submitProfile">保存资料</el-button>
            <el-button @click="syncProfileFormFromMe">重置</el-button>
          </el-form-item>
        </el-form>
        <p v-else-if="!loading && !me" class="empty-tip">无法获取当前用户信息，请重新登录。</p>
      </div>
    </el-card>

    <el-card class="password-card">
      <template #header>
        <span>修改密码</span>
      </template>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px" style="max-width: 400px;">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" placeholder="请输入当前密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="passwordLoading" @click="submitChangePassword">确认修改</el-button>
          <el-button @click="resetPasswordForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

const loading = ref(true)
const me = ref<Record<string, any> | null>(null)
const profileFormRef = ref<FormInstance>()
const profileLoading = ref(false)
const profileForm = reactive({
  realName: '',
  phone: '',
  email: '',
  department: ''
})

const profileRules: FormRules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const syncProfileFormFromMe = () => {
  profileForm.realName = me.value?.realName || ''
  profileForm.phone = me.value?.phone || ''
  profileForm.email = me.value?.email || ''
  profileForm.department = me.value?.department || ''
}

const fetchMe = async () => {
  loading.value = true
  try {
    const data = await request.get('/users/me') as any
    me.value = data
    syncProfileFormFromMe()
  } catch (e) {
    me.value = null
  } finally {
    loading.value = false
  }
}

const roleLabel = (role: string | undefined) => {
  if (!role) return '-'
  const map: Record<string, string> = {
    ROLE_ADMIN: '管理员',
    ROLE_ECOMMERCE: '电商/甲方',
    ROLE_XIAOAN: '小安智能',
    ROLE_FACTORY: '工厂',
    ROLE_DELIVERY: '交付方',
    ROLE_SHIPPING: '出货方',
    ROLE_WAREHOUSE: '仓库'
  }
  return map[role] || role
}

const permissionsSummary = computed(() => {
  const p = me.value?.permissions
  if (!p) return '-'
  if (typeof p === 'string') return p || '-'
  if (Array.isArray(p)) return p.join('、') || '-'
  return '-'
})

const submitProfile = async () => {
  if (!profileFormRef.value || !me.value) return
  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }
  profileLoading.value = true
  try {
    const updated = await request.patch('/users/me', {
      realName: profileForm.realName,
      phone: profileForm.phone,
      email: profileForm.email,
      department: profileForm.department
    }) as any
    me.value = updated
    syncProfileFormFromMe()
    if (updated?.realName) localStorage.setItem('realName', updated.realName)
    ElMessage.success('个人资料已更新')
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || '保存失败'
    ElMessage.error(msg)
  } finally {
    profileLoading.value = false
  }
}

const passwordFormRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirm = (_rule: any, value: string, callback: (err?: Error) => void) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }]
}

const submitChangePassword = async () => {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }
  passwordLoading.value = true
  try {
    await request.post('/auth/change-password', {
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功')
    resetPasswordForm()
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || '修改失败'
    ElMessage.error(msg)
  } finally {
    passwordLoading.value = false
  }
}

const resetPasswordForm = () => {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
}

onMounted(() => {
  fetchMe()
})
</script>

<style scoped>
.profile-page {
  padding: 0;
}
.info-card,
.password-card {
  margin-bottom: 20px;
}
.info-body {
  min-height: 80px;
}
.empty-tip {
  color: #909399;
  margin: 0;
}
</style>
