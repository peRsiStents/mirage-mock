/**
 * 文本行级 Diff（用于对比两次接口响应 / 新旧报文）。
 *
 * 算法：先剥掉公共前后缀，剩下部分用 LCS 动态规划求编辑序列。
 * LCS 是 O(n*m) 内存，测试报文动辄几千行，所以先做前后缀裁剪 + 上限保护：
 * 超限时退化为「整块替换」，保证界面不会卡死。
 */

/** LCS 的规模上限（n*m 单元格数） */
const MAX_CELLS = 4000000

/**
 * @param {string} a 左侧文本
 * @param {string} b 右侧文本
 * @returns {{rows: Array<{type:'same'|'add'|'del', left:number|null, right:number|null, text:string}>,
 *            added:number, removed:number, same:number, tooLarge:boolean}}
 */
export function diffLines(a, b) {
  const A = splitLines(a)
  const B = splitLines(b)

  // 1) 公共前缀
  let p = 0
  while (p < A.length && p < B.length && A[p] === B[p]) p++
  // 2) 公共后缀
  let s = 0
  while (s < A.length - p && s < B.length - p && A[A.length - 1 - s] === B[B.length - 1 - s]) s++

  const midA = A.slice(p, A.length - s)
  const midB = B.slice(p, B.length - s)

  const rows = []
  for (let i = 0; i < p; i++) rows.push({ type: 'same', left: i + 1, right: i + 1, text: A[i] })

  let tooLarge = false
  if (midA.length * midB.length > MAX_CELLS) {
    tooLarge = true
    midA.forEach((t, i) => rows.push({ type: 'del', left: p + i + 1, right: null, text: t }))
    midB.forEach((t, i) => rows.push({ type: 'add', left: null, right: p + i + 1, text: t }))
  } else {
    rows.push(...lcsDiff(midA, midB, p))
  }

  for (let i = 0; i < s; i++) {
    const li = A.length - s + i
    const ri = B.length - s + i
    rows.push({ type: 'same', left: li + 1, right: ri + 1, text: A[li] })
  }

  const stat = { added: 0, removed: 0, same: 0 }
  for (const r of rows) {
    if (r.type === 'add') stat.added++
    else if (r.type === 'del') stat.removed++
    else stat.same++
  }
  return { rows, added: stat.added, removed: stat.removed, same: stat.same, tooLarge }
}

/** 中段（已剥离公共前后缀）的 LCS 编辑序列。 */
function lcsDiff(midA, midB, offset) {
  const n = midA.length
  const m = midB.length
  // dp[i][j] = midA[i..] 与 midB[j..] 的 LCS 长度
  const dp = new Array(n + 1)
  for (let i = 0; i <= n; i++) dp[i] = new Int32Array(m + 1)
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = midA[i] === midB[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  const rows = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (midA[i] === midB[j]) {
      rows.push({ type: 'same', left: offset + i + 1, right: offset + j + 1, text: midA[i] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      rows.push({ type: 'del', left: offset + i + 1, right: null, text: midA[i] })
      i++
    } else {
      rows.push({ type: 'add', left: null, right: offset + j + 1, text: midB[j] })
      j++
    }
  }
  while (i < n) {
    rows.push({ type: 'del', left: offset + i + 1, right: null, text: midA[i++] })
  }
  while (j < m) {
    rows.push({ type: 'add', left: null, right: offset + j + 1, text: midB[j++] })
  }
  return rows
}

/** 按行切分（兼容 \r\n），末尾空行不保留。 */
function splitLines(text) {
  if (text == null || text === '') return []
  const lines = String(text).replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
  if (lines.length && lines[lines.length - 1] === '') lines.pop()
  return lines
}
