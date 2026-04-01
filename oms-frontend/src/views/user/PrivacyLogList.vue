<template>
  <div class="privacy-log-list mobile-list-layout">
  <template v-if="canAccess">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="操作人">
          <el-input v-model="filterForm.operatorName" placeholder="操作人姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="filterForm.targetType" placeholder="全部" clearable style="width: 140px">
            <el-option label="销售订单" value="SALES_ORDER" />
            <el-option label="合作方" value="PARTNER" />
            <el-option label="合同" value="CONTRACT" />
            <el-option label="用户" value="USER" />
            <el-option label="送货单" value="DELIVERY_NOTE" />
            <el-option label="商机" value="OPPORTUNITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filterForm.action" placeholder="全部" clearable style="width: 120px">
            <el-option label="复制" value="COPY" />
            <el-option label="下载" value="DOWNLOAD" />
            <el-option label="导出" value="EXPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.startTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
            style="width: 140px"
          />
          <span style="margin: 0 8px;">至</span>
          <el-date-picker
            v-model="filterForm.endTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-wrapper">
      <el-table :data="tableData" border stripe size="small" v-loading="loading" style="width: 100%">
        <el-table-column prop="createTime" label="时间" width="170" :formatter="formatDateTime" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="clientIp" label="IP" width="140" />
        <el-table-column prop="action" label="操作类型" width="90">
          <template #default="scope">
            <el-tag size="small">{{ actionLabel(scope.row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="业务类型" width="110">
          <template #default="scope">
            {{ targetTypeLabel(scope.row.targetType) }}
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="100" show-overflow-tooltip />
        <el-table-column prop="fieldOrDescription" label="涉及字段/描述" min-width="200" show-overflow-tooltip />
      </el-table>
      </div>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchLogs"
          @current-change="fetchLogs"
        />
      </div>
    </el-card>
  </template>
  <el-result v-else-if="!canAccess && inited" icon="warning" title="无权访问" sub-title="该页面仅限管理员访问" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const canAccess = ref(true)
const inited = ref(false)
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterForm = reactive({
  operatorName: '',
  targetType: '',
  action: '',
  startTime: '',
  endTime: ''
})

const actionLabel = (action: string) => {
  const m: Record<string, string> = { COPY: '复制', DOWNLOAD: '下载', EXPORT: '导出' }
  return m[action] || action
}

const targetTypeLabel = (type: string) => {
  const m: Record<string, string> = {
    SALES_ORDER: '销售订单',
    PARTNER: '合作方',
    CONTRACT: '合同',
    USER: '用户',
    DELIVERY_NOTE: '送货单',
    OPPORTUNITY: '商机'
  }
  return m[type] || type
}

const formatDateTime = (row: any, _col: any, val: string) => {
  if (!val) return '-'
  if (val.length >= 19) return val.replace('T', ' ').slice(0, 19)
  return val
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/privacy-logs', {
      params: {
        operatorName: filterForm.operatorName || undefined,
        targetType: filterForm.targetType || undefined,
        action: filterForm.action || undefined,
        startTime: filterForm.startTime || undefined,
        endTime: filterForm.endTime || undefined,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    }) as any
    if (res && Array.isArray(res.content)) {
      tableData.value = res.content
      total.value = res.totalElements ?? 0
      if (res.message) ElMessage.warning(res.message)
    } else {
      tableData.value = []
      total.value = 0
      if (res?.message) ElMessage.warning(res.message)
    }
  } catch (e: any) {
    if (e?.response?.status === 403) {
      canAccess.value = false
    } else {
      ElMessage.error('加载失败')
    }
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
    inited.value = true
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchLogs()
}

const resetSearch = () => {
  filterForm.operatorName = ''
  filterForm.targetType = ''
  filterForm.action = ''
  filterForm.startTime = ''
  filterForm.endTime = ''
  currentPage.value = 1
  fetchLogs()
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.privacy-log-list {
  padding: 20px;
}
.filter-card {
  margin-bottom: 20px;
}
.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
