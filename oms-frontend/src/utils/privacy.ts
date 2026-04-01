/**
 * 隐私脱敏与复制记录
 * 展示时用脱敏函数；复制时调用 copyWithPrivacyLog 记录日志并写入剪贴板
 */
import request from './request'
import { ElMessage } from 'element-plus'

/** 姓名：保留首尾各1字，中间用* */
export function maskName(name: string | null | undefined): string {
  if (name == null || name === '') return '-'
  const s = String(name).trim()
  if (s.length <= 2) return s[0] + '*'
  return s[0] + '*'.repeat(Math.min(s.length - 2, 1)) + s[s.length - 1]
}

/** 手机号：保留前3后4，中间 **** */
export function maskPhone(phone: string | null | undefined): string {
  if (phone == null || phone === '') return '-'
  const s = String(phone).trim()
  if (s.length <= 7) return s.slice(0, 3) + '****'
  return s.slice(0, 3) + '****' + s.slice(-4)
}

/** 地址：保留到区/县或前约15字，后面用* */
export function maskAddress(address: string | null | undefined): string {
  if (address == null || address === '') return '-'
  const s = String(address).trim()
  const keep = 15
  if (s.length <= keep) return s
  return s.slice(0, keep) + '*'.repeat(Math.min(s.length - keep, 18))
}

/** 邮箱：保留首字与@后域名，中间用* */
export function maskEmail(email: string | null | undefined): string {
  if (email == null || email === '') return '-'
  const s = String(email).trim()
  const at = s.indexOf('@')
  if (at <= 0) return s.length > 0 ? s[0] + '***' : '-'
  const local = s.slice(0, at)
  const domain = s.slice(at)
  if (local.length <= 1) return local + '***' + domain
  return local[0] + '*'.repeat(Math.min(local.length - 1, 3)) + domain
}

export interface PrivacyLogPayload {
  targetType: string
  targetId: string
  action?: string
  field?: string
}

/**
 * 仅记录隐私访问日志（用于下载/预览等，不复制）
 * 返回 true 表示记录成功，false 表示请求失败或 success 为 false
 */
export async function logPrivacyAccess(payload: PrivacyLogPayload): Promise<boolean> {
  const { targetType, targetId, action = 'COPY', field } = payload
  try {
    const data: any = await request.post('/privacy/log', {
      targetType,
      targetId: String(targetId),
      action,
      field: field || undefined
    })
    return data && data.success === true
  } catch (e) {
    return false
  }
}

/** 日志中「涉及字段/描述」最大长度（与后端 field_or_description 一致） */
const MAX_DESCRIPTION_LENGTH = 500

function legacyCopyText(text: string): boolean {
  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'readonly')
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.left = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    return ok
  } catch {
    return false
  }
}

/**
 * 复制文本到剪贴板并尝试记录隐私访问日志（先复制再记日志，记日志失败也不影响复制）。
 * 日志中会保存实际复制到剪贴板的内容（截断至 500 字），便于在「隐私访问记录」中查看具体内容。
 */
export async function copyWithPrivacyLog(text: string, payload: PrivacyLogPayload): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else if (!legacyCopyText(text)) {
      throw new Error('clipboard unavailable')
    }
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    if (!legacyCopyText(text)) {
      ElMessage.error('复制失败')
      return false
    }
    ElMessage.success('已复制到剪贴板')
  }
  // 将剪贴板实际内容作为「涉及字段/描述」写入日志，便于管理员查看具体复制内容
  const description =
    text != null && String(text).trim().length > 0
      ? String(text).trim().slice(0, MAX_DESCRIPTION_LENGTH)
      : payload.field
  const ok = await logPrivacyAccess({ ...payload, field: description })
  if (!ok) {
    ElMessage.warning('已复制，但访问记录可能未保存')
  }
  return true
}
