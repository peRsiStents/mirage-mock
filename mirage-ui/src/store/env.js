import { defineStore } from 'pinia'

// 全局「运行环境」选择：按项目持久化到 localStorage，跨页面切换/刷新保持，
// 省去每次运行用例/场景都重选环境。键为 projectId，值为 envId。
const STORAGE_KEY = 'mirage_run_env'

function loadMap() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  } catch (e) {
    return {}
  }
}

export const useEnvStore = defineStore('runEnv', {
  state: () => ({ map: loadMap() }),
  getters: {
    /** 取某项目已选环境 id（未选返回 null） */
    envId: (state) => (projectId) => {
      if (projectId == null) return null
      const v = state.map[String(projectId)]
      return v == null ? null : v
    }
  },
  actions: {
    /** 设置某项目的运行环境；传 null/空清除 */
    setEnvId(projectId, envId) {
      if (projectId == null) return
      const key = String(projectId)
      if (envId === null || envId === undefined || envId === '') {
        delete this.map[key]
      } else {
        this.map[key] = envId
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.map))
    }
  }
})
