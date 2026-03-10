<template>
  <div class="rebate-rule-list">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="规则名称">
          <el-input v-model="filterForm.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="启用" />
            <el-option label="禁用" value="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-ops">
        <el-button type="primary">新增返利规则</el-button>
        <el-button type="success" plain>批量导入</el-button>
        <el-popover placement="bottom" width="500" trigger="click">
          <template #reference>
            <el-button>编辑表头</el-button>
          </template>
          <div class="column-settings">
            <div class="column-header">
              <span>列设置</span>
              <el-button size="small" @click="moveToTop">置顶选中</el-button>
            </div>
            <el-scrollbar height="300px">
              <draggable
                v-model="orderedColumns"
                item-key="label"
                handle=".drag-handle"
                @end="onDragEnd"
                ghost-class="drag-ghost"
                chosen-class="drag-chosen"
              >
                <template #item="{ element }">
                  <div class="column-item" :class="{ 'hidden-column': !element.visible }">
                    <div class="drag-handle">≡</div>
                    <el-checkbox
                      v-model="element.visible"
                      :label="element.label"
                      @change="onColumnVisibilityChange(element.label, $event)"
                    >
                      {{ element.title }}
                    </el-checkbox>
                    <el-button
                      link
                      size="small"
                      @click="moveToTopByLabel(element.label)"
                      title="置顶"
                    >
                      ↑
                    </el-button>
                  </div>
                </template>
              </draggable>
            </el-scrollbar>
            <div class="settings-actions">
              <el-button size="small" @click="resetColumns">恢复默认</el-button>
              <el-button type="primary" size="small" @click="saveColumns">保存</el-button>
            </div>
          </div>
        </el-popover>
      </div>
      <el-table :data="tableData" style="width: 100%" border stripe size="small">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link :type="scope.row.status === '启用' ? 'danger' : 'success'" @click="handleToggleStatus(scope.row)">
              {{ scope.row.status === '启用' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template v-for="column in orderedColumns" :key="column.label">
          <el-table-column
            v-if="column.visible && column.label !== '操作'"
            :prop="column.label"
            :label="column.title"
            :width="column.width"
            :min-width="column.minWidth"
            :fixed="column.fixed"
            show-overflow-tooltip
          >
            <template #default="scope" v-if="column.label === 'rebateRate'">
              {{ scope.row[column.label] }}%
            </template>
            <template #default="scope" v-else-if="column.label === 'threshold'">
              ¥{{ scope.row[column.label] }}
            </template>
            <template #default="scope" v-else-if="column.label === 'status'">
              <el-tag :type="scope.row[column.label] === '启用' ? 'success' : 'danger'">{{ scope.row[column.label] }}</el-tag>
            </template>
            <template #default="scope" v-else>
              {{ scope.row[column.label] }}
            </template>
          </el-table-column>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'

interface FilterForm {
  name: string
  status: string
}

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  visible: boolean
}

const filterForm = ref<FilterForm>({
  name: '',
  status: ''
})

const tableData = ref<any[]>([])

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'ruleName', title: '规则名称', width: 180, visible: true },
  { label: 'customerType', title: '客户类型', width: 120, visible: true },
  { label: 'productCategory', title: '商品类别', width: 120, visible: true },
  { label: 'rebateRate', title: '返利率', width: 100, visible: true },
  { label: 'threshold', title: '起始金额', width: 120, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'startTime', title: '开始时间', width: 160, visible: true },
  { label: 'endTime', title: '结束时间', width: 160, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true }
])

// 有序的列配置
const orderedColumns = ref<ColumnConfig[]>([...allColumns.value])

// 可见列数组（保持兼容性）
const visibleColumns = computed<string[]>({
  get: () => orderedColumns.value.filter(col => col.visible).map(col => col.label),
  set: (value) => {
    allColumns.value.forEach(col => {
      col.visible = value.includes(col.label)
    })
    // 保持原有顺序
    orderedColumns.value = allColumns.value.filter(col => value.includes(col.label))
      .concat(allColumns.value.filter(col => !value.includes(col.label)))
  }
})

const loadSettings = () => {
  const saved = localStorage.getItem('rebateRuleListColumns')
  if (saved) {
    try {
      const settings = JSON.parse(saved)
      if (settings.columnsOrder) {
        // 按照保存的顺序重新排列
        const savedOrder = settings.columnsOrder
        const newOrderedColumns = [...allColumns.value].sort((a, b) => {
          const aIndex = savedOrder.indexOf(a.label)
          const bIndex = savedOrder.indexOf(b.label)
          if (aIndex === -1 && bIndex === -1) return 0
          if (aIndex === -1) return 1
          if (bIndex === -1) return -1
          return aIndex - bIndex
        })
        orderedColumns.value = newOrderedColumns
      }
      if (settings.visible) {
        const visibleLabels = settings.visible
        orderedColumns.value.forEach(col => {
          col.visible = visibleLabels.includes(col.label)
        })
      }
    } catch {
      orderedColumns.value = [...allColumns.value]
    }
  }
}

const saveColumns = () => {
  const visible = visibleColumns.value
  const columnsOrder = orderedColumns.value.map(col => col.label)
  
  localStorage.setItem('rebateRuleListColumns', JSON.stringify({
    visible,
    columnsOrder
  }))
  ElMessage.success('表头设置已保存')
}

const resetColumns = () => {
  allColumns.value.forEach((col, index) => {
    col.visible = true
    col.label = allColumns.value[index].label
  })
  orderedColumns.value = [...allColumns.value]
}

const onColumnVisibilityChange = (label: string, checked: boolean) => {
  const column = orderedColumns.value.find(col => col.label === label)
  if (column) {
    column.visible = checked
  }
}

const moveToTop = () => {
  // 将所有选中的列移到最前面
  const checkedColumns = orderedColumns.value.filter(col => col.visible)
  const uncheckedColumns = orderedColumns.value.filter(col => !col.visible)
  orderedColumns.value = [...checkedColumns, ...uncheckedColumns]
}

const moveToTopByLabel = (label: string) => {
  const index = orderedColumns.value.findIndex(col => col.label === label)
  if (index > -1) {
    const [column] = orderedColumns.value.splice(index, 1)
    orderedColumns.value.unshift(column)
  }
}

const onDragEnd = () => {
  // 拖拽结束后可以做些事情，比如保存顺序
  console.log('列顺序已更新')
}

const fetchData = () => {
  // 模拟数据
  tableData.value = [
    { id: 1, ruleName: '年度大客户返利规则', customerType: 'VIP客户', productCategory: '热像仪', rebateRate: 5, threshold: 100000, status: '启用', startTime: '2024-01-01', endTime: '2024-12-31', createTime: '2023-12-15 10:30:00' },
    { id: 2, ruleName: '新客户促销返利', customerType: '新客户', productCategory: '全部', rebateRate: 3, threshold: 50000, status: '启用', startTime: '2024-01-01', endTime: '2024-06-30', createTime: '2023-12-20 14:20:00' },
    { id: 3, ruleName: '季度销量返利', customerType: '普通客户', productCategory: '测温仪', rebateRate: 2, threshold: 80000, status: '禁用', startTime: '2024-01-01', endTime: '2024-03-31', createTime: '2023-12-25 09:15:00' }
  ]
}

const handleSearch = () => {
  fetchData()
}

const resetSearch = () => {
  filterForm.value = { name: '', status: '' }
  fetchData()
}

const handleEdit = (row: any) => {
  console.log('编辑返利规则', row)
}

const handleToggleStatus = (row: any) => {
  const newStatus = row.status === '启用' ? '禁用' : '启用'
  ElMessageBox.confirm(`确定要${newStatus === '启用' ? '启用' : '禁用'}该返利规则吗？`, '提示', {
    type: 'warning'
  }).then(() => {
    row.status = newStatus
    ElMessage.success(`${newStatus}成功`)
  })
}

onMounted(() => {
  fetchData()
  loadSettings()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 15px;
}
.table-ops {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}
.column-settings {
  max-height: 450px;
  overflow-y: hidden;
}
.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}
.column-item {
  display: flex;
  align-items: center;
  padding: 5px 0;
  cursor: move;
  transition: background-color 0.2s;
}
.column-item:hover {
  background-color: #f5f5f5;
}
.column-item.hidden-column {
  opacity: 0.6;
}
.drag-handle {
  cursor: move;
  padding: 4px 8px;
  margin-right: 8px;
  color: #999;
  font-size: 14px;
  user-select: none;
}
.drag-handle:hover {
  color: #666;
}
.drag-ghost {
  opacity: 0.5;
  background: #c8ebfb;
}
.drag-chosen {
  background: #e6f7ff;
  border: 1px dashed #1890ff;
}
.settings-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>