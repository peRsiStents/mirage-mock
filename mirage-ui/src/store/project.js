import { defineStore } from 'pinia'

export const useProjectStore = defineStore('project', {
  state: () => ({
    // id 以字符串保存：雪花 id 超过 JS Number.MAX_SAFE_INTEGER，转 Number 会丢精度
    id: localStorage.getItem('mirage_pid') || '',
    code: localStorage.getItem('mirage_pcode') || '',
    name: localStorage.getItem('mirage_pname') || ''
  }),
  actions: {
    select(p) {
      this.id = p.id
      this.code = p.code
      this.name = p.name
      localStorage.setItem('mirage_pid', String(p.id))
      localStorage.setItem('mirage_pcode', p.code)
      localStorage.setItem('mirage_pname', p.name || '')
    }
  }
})
