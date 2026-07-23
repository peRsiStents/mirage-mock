import { ElMessage } from 'element-plus'

/**
 * 复制文本到剪贴板，并给出 toast 提示。兼容 HTTP（非安全上下文）。
 *
 * navigator.clipboard 仅在安全上下文（HTTPS 或 localhost）可用；
 * 通过 http://<服务器IP> 访问时不可用，故回退到 textarea + document.execCommand('copy')。
 */
export async function copyText(text) {
  if (!text) return
  const ok = await doCopy(text)
  if (ok) {
    ElMessage.success('已复制：' + text)
  } else {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

async function doCopy(text) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      /* 落到兜底 */
    }
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.top = '-9999px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
