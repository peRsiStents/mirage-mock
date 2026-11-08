import { ElMessageBox } from 'element-plus'

/**
 * 确认弹窗帮助函数：确认返回 true；取消/关闭/X 返回 false（内部吞掉 rejection，
 * 不会产生未处理的 Promise 拒绝）。
 *
 * 用法：if (!(await confirmAction(`删除「${row.name}」？`))) return
 * @param {string} message 提示文案
 * @param {object} [opts] 可含 title 及其余 ElMessageBox.confirm options，默认 warning 风格
 */
export async function confirmAction(message, opts) {
  const { title, ...rest } = opts || {}
  try {
    await ElMessageBox.confirm(message, title || '警告', { type: 'warning', ...rest })
    return true
  } catch (e) {
    // 'cancel' / 'close'：用户主动取消，不算错误
    return false
  }
}
