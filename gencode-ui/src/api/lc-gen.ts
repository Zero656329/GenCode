import { get, post, getToken } from '@/utils/request'
import { message } from 'ant-design-vue'

/**
 * 代码生成（契约见 docs/api-contract.md「低代码 /lc（五期 M5：数据管理与代码生成）」）
 * POST /lc/gen/download 返回 zip 二进制（application/octet-stream，非 R 包装），
 * 走原生 fetch + Authorization 携带 token，避免通用响应拦截器按 R<T> 解包 Blob。
 */

/** 可选表（GET /lc/gen/tables，来自平台主库 information_schema） */
export interface GenTable {
  tableName: string
  tableComment: string | null
}

/** 代码生成选项 */
export interface GenOptions {
  packageName: string
  moduleName: string
  author: string
  /** 业务名（必填），如 gencode_task 表对应 task */
  businessName: string
}

/** 代码生成入参（POST /lc/gen/preview 与 /lc/gen/download 同构） */
export interface GenPreviewBody {
  tableName: string
  options: GenOptions
}

/** 生成的单个文件（POST /lc/gen/preview） */
export interface GenFileVO {
  path: string
  content: string
}

/** 代码预览结果 */
export interface GenPreviewResult {
  files: GenFileVO[]
}

/** 可选表列表（keyword 模糊匹配表名/注释） */
export function getGenTables(keyword?: string): Promise<GenTable[]> {
  return get<GenTable[]>('/lc/gen/tables', { keyword: keyword || undefined })
}

/** 预览生成代码（Entity/Mapper/Service/Controller/XML + Vue 页面 + api.ts + SQL 菜单脚本） */
export function previewGen(body: GenPreviewBody): Promise<GenPreviewResult> {
  return post<GenPreviewResult>('/lc/gen/preview', body)
}

/**
 * 从 Content-Disposition 解析下载文件名（优先 RFC 5987 filename*，兼容 filename=""）
 */
function parseDownloadName(disposition: string | null): string | null {
  if (!disposition) return null
  const star = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(disposition)
  if (star && star[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^["']|["']$/g, ''))
    } catch {
      // 解码失败时退回普通 filename
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  return plain && plain[1] ? plain[1].trim() : null
}

/**
 * 下载生成代码 zip（原生 fetch，blob 不经 R 包装拦截器）。
 * - 401 与业务错误（JSON R 包装）仍按通用逻辑提示，保证体验一致；
 * - 成功后从 Content-Disposition 取文件名（兜底 fallbackName）触发浏览器下载。
 */
export async function downloadGenZip(
  body: GenPreviewBody,
  fallbackName = 'gencode-gen.zip'
): Promise<void> {
  const token = getToken()
  let res: Response
  try {
    res = await fetch('/api/lc/gen/download', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body)
    })
  } catch {
    message.error('网络异常，请检查网络或稍后重试')
    throw new Error('network error')
  }

  if (res.status === 401) {
    message.error('登录已过期，请重新登录')
    throw new Error('unauthorized')
  }
  if (!res.ok) {
    message.error(`下载失败（${res.status}）`)
    throw new Error(`download failed: ${res.status}`)
  }

  const contentType = res.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    // 200 但返回 R 包装：视为业务失败
    const r = (await res.json()) as R<unknown>
    message.error(r.msg || '下载失败')
    throw new Error(r.msg || 'download failed')
  }

  const blob = await res.blob()
  const filename =
    parseDownloadName(res.headers.get('content-disposition')) || fallbackName
  const url = URL.createObjectURL(blob)
  try {
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } finally {
    URL.revokeObjectURL(url)
  }
}

/**
 * 将 tables 响应归一化为 GenTable[]（兼容字符串数组与对象数组，字段名做常见别名兜底）。
 */
export function normalizeGenTables(raw: unknown): GenTable[] {
  if (!Array.isArray(raw)) return []
  const out: GenTable[] = []
  for (const it of raw) {
    if (typeof it === 'string') {
      if (it.trim()) out.push({ tableName: it.trim(), tableComment: null })
      continue
    }
    if (it && typeof it === 'object') {
      const o = it as Record<string, unknown>
      const name = o.tableName ?? o.TABLE_NAME ?? o.name
      if (typeof name === 'string' && name.trim()) {
        const comment = o.tableComment ?? o.TABLE_COMMENT ?? o.comment ?? o.remarks
        out.push({
          tableName: name.trim(),
          tableComment: typeof comment === 'string' && comment.trim() ? comment.trim() : null
        })
      }
    }
  }
  return out
}
