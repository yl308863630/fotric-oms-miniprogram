<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-weight:600;">授权扫码追踪</span>
          <div style="display:flex;gap:8px;">
            <el-button @click="exportLogs">导出Excel</el-button>
            <el-button @click="$router.push('/authorization/list')">返回授权列表</el-button>
          </div>
        </div>
      </template>
      <el-form :inline="true" style="margin-bottom:12px;">
        <el-form-item label="授权ID">
          <el-input v-model="authorizationRecordId" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="result" clearable>
            <el-option label="OK" value="OK" />
            <el-option label="HASH_MISMATCH" value="HASH_MISMATCH" />
            <el-option label="INVALID" value="INVALID" />
            <el-option label="TOKEN_MISMATCH" value="TOKEN_MISMATCH" />
            <el-option label="NO_FILE" value="NO_FILE" />
            <el-option label="FILE_MISSING" value="FILE_MISSING" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-input v-model="channel" placeholder="scan/web/app" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border v-loading="loading">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="authorizationRecordId" label="授权ID" width="100" />
        <el-table-column prop="authorizationCode" label="授权编码" width="170" />
        <el-table-column prop="verifyResult" label="结果" width="130" />
        <el-table-column prop="channel" label="渠道" width="100" />
        <el-table-column prop="clientIp" label="IP" width="140" />
        <el-table-column label="地理信息" min-width="180">
          <template #default="{ row }">
            {{ row.geoCountry || '-' }} / {{ row.geoRegion || '-' }} / {{ row.geoCity || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operatorUsername" label="操作用户" width="130" />
        <el-table-column prop="operatorRealName" label="操作人" width="120" />
      </el-table>

      <div style="margin-top:12px;display:flex;justify-content:flex-end;">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :current-page="page + 1"
          :page-size="size"
          :total="total"
          :page-sizes="[10,20,50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const rows = ref<any[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const authorizationRecordId = ref('')
const result = ref('')
const channel = ref('')

const fetchList = async () => {
  loading.value = true
  try {
    const rid = authorizationRecordId.value.trim()
    const res: any = await request.get('/authorizations/scan-logs', {
      params: {
        page: page.value,
        size: size.value,
        authorizationRecordId: rid ? Number(rid) : undefined,
        result: result.value || undefined,
        channel: channel.value || undefined
      }
    })
    rows.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

const exportLogs = async () => {
  try {
    const rid = authorizationRecordId.value.trim()
    const resp = await fetch(`/api/authorizations/scan-logs/export?` + new URLSearchParams({
      ...(rid ? { authorizationRecordId: rid } : {}),
      ...(result.value ? { result: result.value } : {}),
      ...(channel.value ? { channel: channel.value } : {})
    }), {
      headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
    })
    if (!resp.ok) throw new Error(`导出失败: HTTP ${resp.status}`)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `authorization_scan_logs_${new Date().toISOString().replace(/[:T]/g, '').slice(0, 14)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

const onPageChange = (p: number) => { page.value = p - 1; fetchList() }
const onSizeChange = (s: number) => { size.value = s; page.value = 0; fetchList() }

onMounted(fetchList)
</script>
