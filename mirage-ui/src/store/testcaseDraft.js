import { defineStore } from 'pinia'

/**
 * 跨页面传递「测试用例草稿」：日志页「生成用例」写入 → 用例页 onMounted 取出并打开新建弹窗。
 * take() 取出后立即清空，保证只消费一次。
 */
export const useTestCaseDraftStore = defineStore('testcaseDraft', {
  state: () => ({ draft: null }),
  actions: {
    set(draft) {
      this.draft = draft
    },
    take() {
      const d = this.draft
      this.draft = null
      return d
    },
    clear() {
      this.draft = null
    }
  }
})
