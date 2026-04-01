/**
 * 从用户粘贴的「标签：内容」多行文本中解析合作方/开票信息，用于用户信息维护弹窗智能填充。
 * 支持中英文冒号、常见别名；行号会合并到「开户行地址」字段（库表无独立行号列）。
 */
export interface ParsedPartnerSnippet {
  title?: string
  name?: string
  taxNumber?: string
  bankName?: string
  bankAccount?: string
  bankAddress?: string
  contactPerson?: string
  contactPhone?: string
  email?: string
}

function buildLineMap(raw: string): Map<string, string> {
  const map = new Map<string, string>()
  const lines = raw.replace(/\r\n/g, '\n').split('\n')
  for (const line of lines) {
    const t = line.trim()
    if (!t) continue
    const m = t.match(/^(.+?)[:：]\s*(.+)$/)
    if (!m) continue
    const keyRaw = m[1].trim()
    const val = m[2].trim()
    if (!keyRaw || !val) continue
    const keyNorm = keyRaw.replace(/\s/g, '')
    if (!map.has(keyRaw)) map.set(keyRaw, val)
    if (!map.has(keyNorm)) map.set(keyNorm, val)
  }
  return map
}

function pick(map: Map<string, string>, aliases: string[]): string | undefined {
  for (const a of aliases) {
    const c = a.replace(/\s/g, '')
    for (const [k, v] of map.entries()) {
      if (k.replace(/\s/g, '') === c) return v
    }
  }
  // 宽松：键名包含别名（如「开户银行全称」）
  for (const a of aliases) {
    const c = a.replace(/\s/g, '')
    if (!c) continue
    for (const [k, v] of map.entries()) {
      const kn = k.replace(/\s/g, '')
      if (kn.includes(c) && kn.length <= c.length + 8) return v
    }
  }
  return undefined
}

export function parsePartnerInfoFromPastedText(raw: string): ParsedPartnerSnippet {
  const text = raw.trim()
  if (!text) return {}

  const lineMap = buildLineMap(text)

  const title = pick(lineMap, [
    '公司名称',
    '单位名称',
    '企业名称',
    '抬头',
    '发票抬头',
    '公司全称'
  ])
  const taxNumber = pick(lineMap, [
    '税号',
    '纳税人识别号',
    '统一社会信用代码',
    '税务登记号',
    '信用代码'
  ])
  const bankName = pick(lineMap, ['开户银行', '开户行', '存款银行', '开户银行名称', '付款开户行'])
  let bankAccount = pick(lineMap, ['帐号', '账号', '银行账号', '银行账户', '银行帐户', '账号号码'])
  if (bankAccount) bankAccount = bankAccount.replace(/\s/g, '')
  const address = pick(lineMap, [
    '地址',
    '公司地址',
    '注册地址',
    '单位地址',
    '住所',
    '营业地址',
    '通讯地址'
  ])
  const contactPhone = pick(lineMap, ['电话', '联系电话', '手机', '移动电话', '固定电话', '座机', '传真'])
  const bankCode = pick(lineMap, ['行号', '联行号', '银行行号', '开户行行号', 'CNAPS', 'cnaps'])
  const contactPerson = pick(lineMap, ['联系人', '经办人', '负责人', '业务联系人'])
  const email = pick(lineMap, ['邮箱', '电子邮箱', 'E-mail', 'e-mail', 'Email'])

  let bankAddress = ''
  if (address) bankAddress = address
  if (bankCode) {
    const line = `联行号：${bankCode}`
    bankAddress = bankAddress ? `${bankAddress}\n${line}` : line
  }

  const out: ParsedPartnerSnippet = {}
  if (title) {
    out.title = title
    out.name = title
  }
  if (taxNumber) out.taxNumber = taxNumber
  if (bankName) out.bankName = bankName
  if (bankAccount) out.bankAccount = bankAccount
  if (bankAddress) out.bankAddress = bankAddress
  if (contactPhone) out.contactPhone = contactPhone
  if (contactPerson) out.contactPerson = contactPerson
  if (email) out.email = email

  return out
}
