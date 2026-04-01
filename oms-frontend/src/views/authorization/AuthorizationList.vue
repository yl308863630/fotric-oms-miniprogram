<template>
  <div v-if="canAccess">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;">
          <div style="font-weight:600;">平台授权统计</div>
          <div style="display:flex;gap:8px;align-items:center;">
            <el-button @click="mappingVisible=true">列映射</el-button>
            <el-button @click="openTemplateManager">模板管理</el-button>
            <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange" accept=".xlsx,.xls">
              <el-button type="primary" :loading="importing">上传授权统计表</el-button>
            </el-upload>
            <el-button @click="goScanLogs">扫码追踪</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" style="margin-bottom:12px;">
        <el-form-item label="关键字">
          <el-input v-model="keyword" clearable placeholder="编码/平台/签发方/被授权方/店铺主体/型号/项目" @keyup.enter="fetchList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部">
            <el-option label="草稿" value="草稿" />
            <el-option label="已生成" value="已生成" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border v-loading="loading">
        <el-table-column prop="authorizationCode" label="授权编码" width="180" />
        <el-table-column prop="platformName" label="平台" width="130" />
        <el-table-column prop="grantorName" label="授权签发方（甲方）" min-width="180" />
        <el-table-column prop="granteeName" label="被授权方" min-width="160" />
        <el-table-column prop="authorizedSubject" label="平台店铺主体" min-width="160" />
        <el-table-column prop="productModel" label="具体产品型号" min-width="160" />
        <el-table-column prop="projectName" label="具体项目" min-width="160" />
        <el-table-column label="有效期" width="220">
          <template #default="{ row }">
            {{ row.validFrom || '-' }} ~ {{ row.validTo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="文档" width="210">
          <template #default="{ row }">
            <el-button v-if="row.finalPdfUrl" link type="primary" @click="preview(row.finalPdfUrl)">预览</el-button>
            <el-button v-if="row.finalPdfUrl" link type="success" @click="download(row.finalPdfUrl)">下载</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :loading="generatingId===row.id" @click="generate(row)">生成文档</el-button>
            <el-button v-if="row.qrUrl" link type="info" @click="showQr(row)">二维码</el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="editVisible" title="编辑授权记录" width="720px">
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="授权编码"><el-input v-model="editForm.authorizationCode" /></el-form-item>
        <el-form-item label="平台"><el-input v-model="editForm.platformName" /></el-form-item>
        <el-form-item label="授权签发方（甲方）"><el-input v-model="editForm.grantorName" /></el-form-item>
        <el-form-item label="被授权方"><el-input v-model="editForm.granteeName" /></el-form-item>
        <el-form-item label="平台店铺主体"><el-input v-model="editForm.authorizedSubject" /></el-form-item>
        <el-form-item label="填写说明">
          <span style="color:#909399;">签发方=授权给出去的一方；店铺主体=平台实际开店展示资质的主体，可与被授权方不同。</span>
        </el-form-item>
        <el-form-item label="具体产品型号"><el-input v-model="editForm.productModel" /></el-form-item>
        <el-form-item label="具体项目"><el-input v-model="editForm.projectName" /></el-form-item>
        <el-form-item label="选择模板">
          <el-select v-model="selectedTemplateId" clearable filterable placeholder="从模板库选择" @change="handleTemplateSelectChange">
            <el-option v-for="t in templateRows" :key="t.id" :label="t.templateName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="editForm.validFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="失效日期"><el-date-picker v-model="editForm.validTo" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="模板URL"><el-input v-model="editForm.templateUrl" placeholder="/uploads/...docx" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrVisible" title="授权验真二维码" width="420px" @closed="onQrDialogClosed">
      <div v-loading="qrLoading" style="text-align:center;min-height:120px;">
        <img v-if="qrImageUrl" :src="qrImageUrl" alt="授权验真二维码" style="max-width:320px;max-height:320px;" />
      </div>
    </el-dialog>

    <el-dialog v-model="mappingVisible" title="导入列映射配置" width="720px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px;"
        title="默认已内置常见列名，这里用于你的表头不一致时手工指定“字段对应的表头名称”" />
      <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px;flex-wrap:wrap;">
        <el-select v-model="selectedMappingId" clearable placeholder="选择已保存模板" style="width:260px;">
          <el-option v-for="m in mappingTemplates" :key="m.id" :label="`${m.mappingName}${m.isDefault ? '（默认）' : ''}`" :value="m.id" />
        </el-select>
        <el-button @click="loadSelectedMapping" :disabled="!selectedMappingId">加载模板</el-button>
        <el-button @click="setDefaultMapping" :disabled="!selectedMappingId">设为默认</el-button>
        <el-button type="danger" @click="deleteSelectedMapping" :disabled="!selectedMappingId">删除模板</el-button>
      </div>
      <el-form :model="mappingForm" label-width="160px">
        <el-form-item label="模板名称"><el-input v-model="mappingName" placeholder="如：宏伟授权表模板" /></el-form-item>
        <el-form-item label="授权编码列"><el-input v-model="mappingForm.authorizationCode" placeholder="如：授权编码" /></el-form-item>
        <el-form-item label="平台列"><el-input v-model="mappingForm.platformName" placeholder="如：电商平台" /></el-form-item>
        <el-form-item label="签发方列"><el-input v-model="mappingForm.grantorName" placeholder="如：授权方/甲方" /></el-form-item>
        <el-form-item label="被授权方列"><el-input v-model="mappingForm.granteeName" placeholder="如：被授权方" /></el-form-item>
        <el-form-item label="平台店铺主体列"><el-input v-model="mappingForm.authorizedSubject" placeholder="如：授权主体/店铺主体/授权经销商店名" /></el-form-item>
        <el-form-item label="产品型号列"><el-input v-model="mappingForm.productModel" placeholder="如：具体产品型号" /></el-form-item>
        <el-form-item label="项目列"><el-input v-model="mappingForm.projectName" placeholder="如：具体项目" /></el-form-item>
        <el-form-item label="生效日期列"><el-input v-model="mappingForm.validFrom" placeholder="如：生效日期" /></el-form-item>
        <el-form-item label="失效日期列"><el-input v-model="mappingForm.validTo" placeholder="如：失效日期" /></el-form-item>
        <el-form-item label="模板URL列"><el-input v-model="mappingForm.templateUrl" placeholder="如：模板URL" /></el-form-item>
        <el-form-item label="备注列"><el-input v-model="mappingForm.remark" placeholder="如：备注" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :disabled="!selectedMappingId" @click="updateSelectedMapping">保存修改</el-button>
        <el-button type="primary" plain @click="saveMappingTemplate">另存为模板</el-button>
        <el-button @click="resetMapping">重置默认</el-button>
        <el-button type="primary" @click="mappingVisible=false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateManageVisible" title="授权模板管理" width="920px">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:10px;">
        <el-input v-model="templateKeyword" placeholder="模板名称关键词" clearable style="width:280px;" />
        <div style="display:flex;gap:8px;">
          <el-button @click="fetchTemplateOptions">查询</el-button>
          <el-button type="primary" @click="openTemplateCreate">上传模板</el-button>
        </div>
      </div>
      <el-table :data="templateRows" border v-loading="templateLoading" max-height="380">
        <el-table-column prop="templateName" label="模板名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="useTemplate(row)">选择</el-button>
            <el-button link type="primary" @click="openTemplateEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="templateFormVisible" :title="templateForm.id ? '编辑授权模板' : '上传授权模板'" width="620px">
      <el-form :model="templateForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" type="textarea" :rows="3" placeholder="请输入模板描述" />
        </el-form-item>
        <el-form-item label="模板文件" v-if="!templateForm.id">
          <el-upload
            drag
            action="/api/upload"
            :headers="getUploadHeaders()"
            :before-upload="beforeTemplateUpload"
            :on-success="handleTemplateUploadSuccess"
            :on-error="handleTemplateUploadError"
            :file-list="templateFileList"
            :limit="1"
            :auto-upload="true"
            accept=".doc,.docx"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .doc/.docx，且不超过10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="模板URL" required>
          <el-input v-model="templateForm.templateUrl" placeholder="/uploads/...docx" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateFormVisible=false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
  <div v-else class="no-access">
    <el-result icon="warning" title="无权访问" sub-title="请联系管理员开通平台授权模块权限" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { apiBase } from '../../utils/apiBase'

const router = useRouter()
const loading = ref(false)
const importing = ref(false)
const generatingId = ref<number | null>(null)
const rows = ref<any[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref('')

const editVisible = ref(false)
const qrVisible = ref(false)
const mappingVisible = ref(false)
const templateManageVisible = ref(false)
const templateFormVisible = ref(false)
const templateLoading = ref(false)
const templateKeyword = ref('')
const templateRows = ref<any[]>([])
const templateFileList = ref<any[]>([])
/** 带 JWT 拉取后的 blob URL；勿用 img 直链 /api/files/preview（浏览器请求不带 Authorization 会 403） */
const qrImageUrl = ref('')
const qrLoading = ref(false)
const mappingTemplates = ref<any[]>([])
const selectedMappingId = ref<number | null>(null)
const selectedTemplateId = ref<number | null>(null)
const mappingName = ref('')
const defaultMapping = {
  authorizationCode: '',
  platformName: '',
  grantorName: '',
  granteeName: '',
  authorizedSubject: '',
  productModel: '',
  projectName: '',
  validFrom: '',
  validTo: '',
  templateUrl: '',
  remark: ''
}
const mappingForm = reactive<any>({ ...defaultMapping })
const editForm = reactive<any>({
  id: null,
  authorizationCode: '',
  platformName: '',
  grantorName: '',
  granteeName: '',
  authorizedSubject: '',
  productModel: '',
  projectName: '',
  validFrom: '',
  validTo: '',
  templateUrl: '',
  remark: ''
})
const templateForm = reactive<any>({
  id: null,
  templateName: '',
  description: '',
  templateUrl: '',
  fileName: '',
  fileSize: null
})

const permissionSet = computed(() => {
  const raw = localStorage.getItem('permissions') || ''
  return new Set(raw.split(',').map(s => s.trim()).filter(Boolean))
})
const isAdmin = computed(() => localStorage.getItem('username') === 'admin')
const canAccess = computed(() => isAdmin.value || permissionSet.value.has('authorization'))

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/authorizations', {
      params: { page: page.value, size: size.value, keyword: keyword.value, status: status.value }
    })
    rows.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

const fetchMappingTemplates = async () => {
  const list: any = await request.get('/authorizations/import-mappings')
  mappingTemplates.value = Array.isArray(list) ? list : []
  const dft = mappingTemplates.value.find((x: any) => x.isDefault)
  if (dft?.mapping) Object.assign(mappingForm, defaultMapping, dft.mapping)
}

const onFileChange = async (file: any) => {
  const raw = file?.raw
  if (!raw) return
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', raw)
    const normalizedMapping: Record<string, string> = {}
    Object.keys(mappingForm).forEach((k) => {
      const v = (mappingForm as any)[k]
      if (typeof v === 'string' && v.trim()) normalizedMapping[k] = v.trim()
    })
    if (Object.keys(normalizedMapping).length > 0) {
      fd.append('mappingJson', JSON.stringify(normalizedMapping))
    }
    const res: any = await request.post('/authorizations/import-xlsx', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success(`导入完成：${res.importedRows || 0}/${res.totalRows || 0}`)
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const openEdit = (row: any) => {
  Object.assign(editForm, row)
  const matched = templateRows.value.find((t: any) => t.templateUrl === row.templateUrl)
  selectedTemplateId.value = matched?.id ?? null
  editVisible.value = true
}
const saveEdit = async () => {
  await request.put(`/authorizations/${editForm.id}`, editForm)
  ElMessage.success('保存成功')
  editVisible.value = false
  fetchList()
}
const generate = async (row: any) => {
  generatingId.value = row.id
  try {
    await request.post(`/authorizations/${row.id}/generate`, {})
    ElMessage.success('文档生成成功')
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '文档生成失败')
  } finally {
    generatingId.value = null
  }
}
const preview = async (path: string) => {
  const blob: Blob = await request.get('/files/preview', {
    params: { path },
    responseType: 'blob'
  } as any)
  const objectUrl = window.URL.createObjectURL(blob)
  window.open(objectUrl, '_blank')
  setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000)
}
const download = async (path: string) => {
  const blob: Blob = await request.get('/files/preview', {
    params: { path },
    responseType: 'blob'
  } as any)
  const objectUrl = window.URL.createObjectURL(blob)
  const filename = path.split('/').pop() || 'authorization.pdf'
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(objectUrl)
}
const revokeQrBlobIfAny = () => {
  if (qrImageUrl.value && qrImageUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(qrImageUrl.value)
  }
  qrImageUrl.value = ''
}

const onQrDialogClosed = () => {
  revokeQrBlobIfAny()
  qrLoading.value = false
}

const showQr = async (row: any) => {
  const path = (row.qrUrl || '').trim()
  if (!path) {
    ElMessage.warning('暂无二维码文件，请先生成文档')
    return
  }
  revokeQrBlobIfAny()
  qrVisible.value = true
  qrLoading.value = true
  try {
    const blob: Blob = await request.get('/files/preview', {
      params: { path },
      responseType: 'blob'
    } as any)
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let msg = '加载二维码失败'
      try {
        const j = JSON.parse(text)
        if (j.message) msg = j.message
      } catch {
        /* ignore */
      }
      ElMessage.error(msg)
      qrVisible.value = false
      return
    }
    qrImageUrl.value = URL.createObjectURL(blob)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载二维码失败（请确认已登录且有平台授权权限）')
    qrVisible.value = false
  } finally {
    qrLoading.value = false
  }
}
const goScanLogs = () => router.push('/authorization/scan-logs')
const resetMapping = () => Object.assign(mappingForm, defaultMapping)
const getUploadHeaders = () => {
  const token = localStorage.getItem('token')
  return { Authorization: token ? `Bearer ${token}` : '' }
}
const beforeTemplateUpload = (file: File) => {
  const isDoc = file.name.endsWith('.doc') || file.name.endsWith('.docx')
  if (!isDoc) {
    ElMessage.error('只能上传 .doc/.docx 文件')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}
const handleTemplateUploadSuccess = (response: any, file: File) => {
  if (!(response?.success || response?.url)) {
    ElMessage.error(response?.message || '上传失败')
    return
  }
  let url = response.url as string
  if (url && url.startsWith(apiBase())) {
    url = url.slice(apiBase().length)
  }
  templateForm.templateUrl = url
  templateForm.fileName = file.name
  templateForm.fileSize = file.size
  if (!templateForm.templateName) {
    templateForm.templateName = file.name.replace(/\.(doc|docx)$/i, '')
  }
  ElMessage.success('模板文件上传成功')
}
const handleTemplateUploadError = () => ElMessage.error('模板文件上传失败')
const fetchTemplateOptions = async () => {
  templateLoading.value = true
  try {
    const res: any = await request.get('/authorization-templates', {
      params: { page: 0, size: 200, templateName: templateKeyword.value || '' }
    })
    templateRows.value = res?.content || []
  } finally {
    templateLoading.value = false
  }
}
const openTemplateManager = async () => {
  templateManageVisible.value = true
  await fetchTemplateOptions()
}
const resetTemplateForm = () => {
  Object.assign(templateForm, { id: null, templateName: '', description: '', templateUrl: '', fileName: '', fileSize: null })
  templateFileList.value = []
}
const openTemplateCreate = () => {
  resetTemplateForm()
  templateFormVisible.value = true
}
const openTemplateEdit = (row: any) => {
  resetTemplateForm()
  Object.assign(templateForm, row)
  templateFormVisible.value = true
}
const saveTemplate = async () => {
  if (!templateForm.templateName?.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  if (!templateForm.templateUrl?.trim()) {
    ElMessage.warning('请先上传模板或填写模板URL')
    return
  }
  const payload = {
    templateName: templateForm.templateName.trim(),
    description: templateForm.description || '',
    templateUrl: templateForm.templateUrl.trim(),
    fileName: templateForm.fileName || null,
    fileSize: templateForm.fileSize || null
  }
  if (templateForm.id) {
    await request.put(`/authorization-templates/${templateForm.id}`, payload)
  } else {
    await request.post('/authorization-templates', payload)
  }
  ElMessage.success('模板已保存')
  templateFormVisible.value = false
  await fetchTemplateOptions()
}
const deleteTemplate = async (row: any) => {
  await ElMessageBox.confirm('确认删除该授权模板？', '提示', { type: 'warning' })
  await request.delete(`/authorization-templates/${row.id}`)
  ElMessage.success('删除成功')
  await fetchTemplateOptions()
}
const handleTemplateSelectChange = (id: number | null) => {
  if (!id) return
  const t = templateRows.value.find((x: any) => x.id === id)
  if (t?.templateUrl) editForm.templateUrl = t.templateUrl
}
const useTemplate = (row: any) => {
  selectedTemplateId.value = row.id
  editForm.templateUrl = row.templateUrl
  editVisible.value = true
  templateManageVisible.value = false
}
const loadSelectedMapping = () => {
  const item = mappingTemplates.value.find((m: any) => m.id === selectedMappingId.value)
  if (!item) return
  Object.assign(mappingForm, defaultMapping, item.mapping || {})
  mappingName.value = item.mappingName || ''
}
const saveMappingTemplate = async () => {
  const name = mappingName.value.trim()
  if (!name) {
    ElMessage.warning('请先输入模板名称')
    return
  }
  const normalized: any = {}
  Object.keys(mappingForm).forEach((k) => {
    const v = (mappingForm as any)[k]
    if (typeof v === 'string' && v.trim()) normalized[k] = v.trim()
  })
  await request.post('/authorizations/import-mappings', {
    mappingName: name,
    mapping: normalized,
    setDefault: false
  })
  ElMessage.success('模板已保存')
  await fetchMappingTemplates()
}
const updateSelectedMapping = async () => {
  if (!selectedMappingId.value) {
    ElMessage.warning('请先选择一个已保存模板')
    return
  }
  const name = mappingName.value.trim()
  if (!name) {
    ElMessage.warning('请先输入模板名称')
    return
  }
  const normalized: any = {}
  Object.keys(mappingForm).forEach((k) => {
    const v = (mappingForm as any)[k]
    if (typeof v === 'string' && v.trim()) normalized[k] = v.trim()
  })
  await request.put(`/authorizations/import-mappings/${selectedMappingId.value}`, {
    mappingName: name,
    mapping: normalized,
    setDefault: false
  })
  ElMessage.success('模板修改已保存')
  await fetchMappingTemplates()
}
const setDefaultMapping = async () => {
  if (!selectedMappingId.value) return
  await request.post(`/authorizations/import-mappings/${selectedMappingId.value}/set-default`)
  ElMessage.success('已设为默认模板')
  await fetchMappingTemplates()
}
const deleteSelectedMapping = async () => {
  if (!selectedMappingId.value) return
  await request.delete(`/authorizations/import-mappings/${selectedMappingId.value}`)
  ElMessage.success('模板已删除')
  selectedMappingId.value = null
  mappingName.value = ''
  await fetchMappingTemplates()
}

const onPageChange = (p: number) => { page.value = p - 1; fetchList() }
const onSizeChange = (s: number) => { size.value = s; page.value = 0; fetchList() }

onMounted(() => {
  if (canAccess.value) {
    fetchList()
    fetchMappingTemplates()
    fetchTemplateOptions()
  }
})
</script>

<style scoped>
.no-access { padding: 32px; }
</style>
