<template>
  <div style="padding:24px;max-width:900px;margin:0 auto;">
    <el-card shadow="never">
      <template #header>
        <div style="font-weight:600;">飞础科(FOTRIC)授权验真系统</div>
      </template>
      <el-result v-if="loaded" :icon="result.valid ? 'success' : 'error'"
        :title="result.valid ? '验真通过' : '验真未通过'"
        :sub-title="result.message || '-'">
      </el-result>

      <el-descriptions v-if="loaded" :column="2" border>
        <el-descriptions-item label="授权ID">{{ result.authorizationRecordId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授权编码">{{ result.authorizationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ result.platformName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授权方">{{ result.grantorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="被授权方">{{ result.granteeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授权展示型号" :span="2">{{ result.productModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授权项目" :span="2">{{ result.projectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ result.validFrom || '-' }} ~ {{ result.validTo || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '../../utils/request'

const route = useRoute()
const loaded = ref(false)
const result = reactive<any>({
  valid: false,
  message: '',
  authorizationRecordId: null,
  authorizationCode: '',
  platformName: '',
  grantorName: '',
  granteeName: '',
  productModel: '',
  projectName: '',
  validFrom: '',
  validTo: ''
})

onMounted(async () => {
  const raw = route.query.token
  const token = Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
  if (!token) {
    result.valid = false
    result.message = '缺少 token 参数'
    loaded.value = true
    return
  }
  try {
    // 使用查询参数传 token，避免微信/代理对 path 编码、截断导致「令牌不匹配」
    const res: any = await request.get('/authorizations/verify', {
      params: { token, channel: 'web' },
      skipErrorMsg: true,
      skipErrorLog: true
    } as any)
    Object.assign(result, res || {})
  } catch (e: any) {
    result.valid = false
    result.message = e?.response?.data?.message || e?.message || '验真失败'
  } finally {
    loaded.value = true
  }
})
</script>
