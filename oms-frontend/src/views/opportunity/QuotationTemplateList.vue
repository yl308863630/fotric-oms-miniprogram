<template>
  <div class="quotation-template-list mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="模板名称">
          <el-input v-model="filterForm.templateName" placeholder="请输入模板名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-ops">
        <el-button type="primary" @click="handleAdd">上传报价单模板</el-button>
      </div>
      <div class="table-wrapper">
      <el-table :data="tableData" style="width: 100%" border stripe size="small" v-loading="loading">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="200" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="handlePreview(scope.row)">预览</el-button>
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="warning" @click="handleDownload(scope.row)">下载</el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="templateName" label="模板名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="150" />
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="scope">
            {{ formatFileSize(scope.row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="updateTime" label="更新时间" width="160" />
      </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="showAddDialog"
      title="上传报价单模板"
      width="600px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :destroy-on-close="true"
    >
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="addForm.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="addForm.description" type="textarea" :rows="3" placeholder="请输入模板描述" />
        </el-form-item>
        <el-form-item label="模板文件" required>
          <el-upload
            drag
            action="/api/upload"
            :headers="getUploadHeaders()"
            :before-upload="beforeUpload"
            :on-success="handleFileUploadSuccess"
            :on-error="handleFileUploadError"
            :file-list="fileList"
            :limit="1"
            :auto-upload="true"
            :show-file-list="true"
            accept=".doc,.docx"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只能上传.doc或.docx格式的Word文档，文件大小不超过10MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveAdd" :disabled="!addForm.templateUrl">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑报价单模板" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="editForm.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入模板描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { apiBase } from '@/utils/apiBase'

interface QuotationTemplate {
  id: number
  templateName: string
  description?: string
  templateUrl: string
  fileName?: string
  fileSize?: number
  createTime?: string
  updateTime?: string
}

const filterForm = ref({
  templateName: ''
})

const tableData = ref<QuotationTemplate[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showAddDialog = ref(false)
const showEditDialog = ref(false)
const addForm = ref({
  templateName: '',
  description: '',
  templateUrl: ''
})
const editForm = ref<QuotationTemplate>({
  id: 0,
  templateName: '',
  description: '',
  templateUrl: ''
})
const fileList = ref<any[]>([])
const currentEditId = ref<number | null>(null)
const uploadedFile = ref<File | null>(null)

const fetchTemplates = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/quotation-templates', {
      params: {
        templateName: filterForm.value.templateName,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const templates = res.content || []
    templates.forEach((template: any) => {
      if (template.templateUrl && !template.templateUrl.startsWith('http')) {
        template.templateUrl = apiBase() + template.templateUrl
      }
    })
    tableData.value = templates
    total.value = res.totalElements || 0
  } catch (error) {
    console.error('Fetch quotation templates error:', error)
    ElMessage.error('获取报价单模板列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchTemplates()
}

const resetSearch = () => {
  filterForm.value = { templateName: '' }
  currentPage.value = 1
  fetchTemplates()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchTemplates()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchTemplates()
}

const handleAdd = () => {
  addForm.value = {
    templateName: '',
    description: '',
    templateUrl: ''
  }
  fileList.value = []
  uploadedFile.value = null
  showAddDialog.value = true
}

const handleEdit = (row: QuotationTemplate) => {
  editForm.value = { ...row }
  currentEditId.value = row.id
  showEditDialog.value = true
}

const handleSaveEdit = async () => {
  if (!editForm.value.templateName) {
    ElMessage.error('请输入模板名称')
    return
  }
  try {
    await request.put(`/quotation-templates/${currentEditId.value}`, editForm.value)
    ElMessage.success('更新成功')
    showEditDialog.value = false
    fetchTemplates()
  } catch (error) {
    console.error('Update template error:', error)
    ElMessage.error('更新失败')
  }
}

const handleDelete = (row: QuotationTemplate) => {
  ElMessageBox.confirm('确认删除该模板？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/quotation-templates/${row.id}`)
      ElMessage.success('删除成功')
      fetchTemplates()
    } catch (error) {
      console.error('Delete template error:', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const handlePreview = (row: QuotationTemplate) => {
  if (row.templateUrl) {
    window.open(row.templateUrl, '_blank')
  }
}

const handleDownload = (row: QuotationTemplate) => {
  if (row.templateUrl) {
    const link = document.createElement('a')
    link.href = row.templateUrl
    link.download = row.fileName || 'template.docx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }
}

const getUploadHeaders = () => {
  const token = localStorage.getItem('token')
  return {
    Authorization: token ? `Bearer ${token}` : ''
  }
}

const beforeUpload = (file: File) => {
  const isDoc = file.name.endsWith('.doc') || file.name.endsWith('.docx')
  if (!isDoc) {
    ElMessage.error('只能上传.doc或.docx格式的Word文档!')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过10MB!')
    return false
  }
  return true
}

const handleFileUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    let url = response.url || response.success
    if (!url.startsWith('http')) {
      url = apiBase() + url
    }
    addForm.value.templateUrl = url
    uploadedFile.value = file
    if (!addForm.value.templateName) {
      addForm.value.templateName = file.name.replace(/\.(doc|docx)$/, '')
    }
    ElMessage.success('文件上传成功，请点击确定保存模板')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleFileUploadError = () => {
  ElMessage.error('文件上传失败，请稍后重试')
}

const handleSaveAdd = async () => {
  if (!addForm.value.templateName) {
    ElMessage.error('请输入模板名称')
    return
  }
  if (!addForm.value.templateUrl) {
    ElMessage.error('请先上传模板文件')
    return
  }
  try {
    let relativeUrl = addForm.value.templateUrl
    const base = apiBase()
    if (base && relativeUrl.startsWith(base)) {
      relativeUrl = relativeUrl.slice(base.length)
    }
    const saveData = {
      templateName: addForm.value.templateName,
      description: addForm.value.description,
      templateUrl: relativeUrl,
      fileName: uploadedFile.value?.name,
      fileSize: uploadedFile.value?.size
    }
    await request.post('/quotation-templates', saveData)
    ElMessage.success('上传成功')
    showAddDialog.value = false
    fetchTemplates()
  } catch (error) {
    console.error('Save template error:', error)
    ElMessage.error('保存失败')
  }
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

onMounted(() => {
  fetchTemplates()
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
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
