<template>
  <div class="subject-account-group-page">
    <el-alert type="warning" :closable="false" show-icon class="page-alert">
      仅用于“重复账号共享同一主体数据”的场景。普通同公司不同业务员不要归组，否则会重新打通订单可见范围。
    </el-alert>

    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>手工归组绑定</span>
          <el-button type="primary" :loading="binding" @click="handleBind">确认归组</el-button>
        </div>
      </template>

      <el-form :model="bindForm" label-width="110px" class="bind-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="账号 A">
              <el-input v-model.trim="bindForm.leftUsername" placeholder="例如 qdkc" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="账号 B">
              <el-input v-model.trim="bindForm.rightUsername" placeholder="例如 qdkc-cs" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="主账号">
              <el-select v-model="bindForm.primaryUsername" placeholder="请选择主账号" clearable style="width: 100%">
                <el-option
                  v-for="item in bindPrimaryOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="form-tip">
        主账号拥有关键写操作权限，次账号主要用于查看和辅助处理。
      </div>
    </el-card>

    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>按账号查询归组结果</span>
          <div class="inline-actions">
            <el-input
              v-model.trim="lookupUsername"
              placeholder="请输入用户名"
              clearable
              style="width: 220px"
              @keyup.enter="handleLookup"
            />
            <el-button type="primary" :loading="lookupLoading" @click="handleLookup">查询</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!lookupResult" description="输入用户名后可查看当前手工归组结果" />
      <div v-else class="lookup-result">
        <div class="lookup-grid">
          <div><span class="label">查询账号：</span>{{ lookupResult.queriedUsername || '-' }}</div>
          <div><span class="label">是否手工归组：</span>
            <el-tag :type="lookupResult.manualBound ? 'success' : 'info'">
              {{ lookupResult.manualBound ? '是' : '否' }}
            </el-tag>
          </div>
          <div><span class="label">归组 ID：</span>{{ lookupResult.groupId ?? '-' }}</div>
          <div><span class="label">组标识：</span>{{ lookupResult.groupKey || '-' }}</div>
          <div><span class="label">主账号：</span>{{ lookupResult.primaryUsername || '-' }}</div>
          <div><span class="label">当前是否主账号：</span>{{ lookupResult.primaryActor ? '是' : '否' }}</div>
        </div>
        <div class="member-line">
          <span class="label">组内账号：</span>
          <el-tag v-for="item in lookupResult.usernames || []" :key="item" class="member-tag">
            {{ item }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>手工归组列表</span>
          <div class="inline-actions">
            <el-input
              v-model.trim="listKeyword"
              placeholder="搜索用户名 / 主账号 / 抬头"
              clearable
              style="width: 260px"
              @keyup.enter="fetchGroups"
            />
            <el-button type="primary" @click="fetchGroups">查询</el-button>
            <el-button @click="resetListSearch">重置</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border stripe size="small" v-loading="tableLoading">
        <el-table-column prop="primaryUsername" label="主账号" min-width="140" />
        <el-table-column label="组内账号" min-width="240">
          <template #default="{ row }">
            <el-tag v-for="item in row.usernames || []" :key="item" class="member-tag">
              {{ item }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="主体抬头" min-width="220" />
        <el-table-column prop="contactPerson" label="联系人" min-width="120" />
        <el-table-column prop="contactPhone" label="手机号" min-width="140" />
        <el-table-column prop="groupKey" label="归组标识" min-width="260" />
        <el-table-column prop="updateTime" label="更新时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(username: string) => handleRemoveMember(row, username)">
              <el-button link type="danger">
                移除次账号
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in removableMembers(row)"
                    :key="item"
                    :command="item"
                  >
                    移除 {{ item }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!removableMembers(row).length" disabled>
                    无可移除次账号
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchGroups"
          @current-change="fetchGroups"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

interface LookupResult {
  groupId: number | null
  groupKey: string
  primaryUsername: string
  primaryActor: boolean
  usernames: string[]
  userIds: number[]
  manualBound: boolean
  queriedUsername: string
}

interface GroupRow {
  id: number
  groupKey: string
  groupName: string
  title: string
  contactPerson: string
  contactPhone: string
  primaryUsername: string
  usernames: string[]
  createTime: string
  updateTime: string
}

const binding = ref(false)
const lookupLoading = ref(false)
const tableLoading = ref(false)
const removing = ref(false)

const bindForm = ref({
  leftUsername: '',
  rightUsername: '',
  primaryUsername: ''
})

const lookupUsername = ref('')
const lookupResult = ref<LookupResult | null>(null)

const listKeyword = ref('')
const tableData = ref<GroupRow[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const bindPrimaryOptions = computed(() => {
  return [bindForm.value.leftUsername, bindForm.value.rightUsername].filter((item, index, arr) => {
    return !!item && arr.indexOf(item) === index
  })
})

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const removableMembers = (row: GroupRow) => {
  return (row.usernames || []).filter(item => item && item !== row.primaryUsername)
}

const fetchGroups = async () => {
  tableLoading.value = true
  try {
    const res: any = await request.get('/subject-account-groups/manual-groups', {
      params: {
        keyword: listKeyword.value || undefined,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    tableData.value = Array.isArray(res?.content) ? res.content : []
    total.value = Number(res?.totalElements || 0)
  } finally {
    tableLoading.value = false
  }
}

const handleLookup = async () => {
  if (!lookupUsername.value) {
    ElMessage.warning('请先输入用户名')
    return
  }
  lookupLoading.value = true
  try {
    const res: any = await request.get('/subject-account-groups/lookup', {
      params: { username: lookupUsername.value }
    })
    lookupResult.value = res || null
  } finally {
    lookupLoading.value = false
  }
}

const handleBind = async () => {
  if (!bindForm.value.leftUsername || !bindForm.value.rightUsername) {
    ElMessage.warning('请先填写两个账号')
    return
  }
  if (bindForm.value.leftUsername === bindForm.value.rightUsername) {
    ElMessage.warning('两个账号不能相同')
    return
  }
  if (!bindForm.value.primaryUsername) {
    ElMessage.warning('请选择主账号')
    return
  }
  binding.value = true
  try {
    await request.post('/subject-account-groups/bind', {
      leftUsername: bindForm.value.leftUsername,
      rightUsername: bindForm.value.rightUsername,
      primaryUsername: bindForm.value.primaryUsername
    })
    ElMessage.success('手工归组成功')
    lookupUsername.value = bindForm.value.primaryUsername
    await Promise.all([handleLookup(), fetchGroups()])
  } finally {
    binding.value = false
  }
}

const handleRemoveMember = async (row: GroupRow, username: string) => {
  if (!username || removing.value) {
    return
  }
  removing.value = true
  try {
    await request.delete('/subject-account-groups/member', {
      params: { username }
    })
    ElMessage.success(`已移除账号 ${username}`)
    if (lookupResult.value && (lookupResult.value.usernames || []).includes(username)) {
      lookupUsername.value = row.primaryUsername || ''
      if (lookupUsername.value) {
        await handleLookup()
      } else {
        lookupResult.value = null
      }
    }
    await fetchGroups()
  } finally {
    removing.value = false
  }
}

const resetListSearch = async () => {
  listKeyword.value = ''
  currentPage.value = 1
  await fetchGroups()
}

onMounted(() => {
  fetchGroups()
})
</script>

<style scoped>
.subject-account-group-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-alert,
.page-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.inline-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bind-form {
  margin-bottom: 8px;
}

.form-tip {
  color: #606266;
  font-size: 13px;
}

.lookup-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lookup-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px 16px;
}

.label {
  color: #909399;
}

.member-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.member-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .pagination-container {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
