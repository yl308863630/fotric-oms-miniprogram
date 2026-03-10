/**
 * 当前站点 origin，用于拼后端完整 URL（合同下载、模板、对账单等）。
 * 本机访问时为 http://localhost:3000，外网穿透时为外网域名，保证请求发到同一站点再由代理到后端。
 */
export function apiBase(): string {
  if (typeof window !== 'undefined') {
    return window.location.origin
  }
  return ''
}
