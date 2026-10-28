/**
 * 组卷页自动弹窗「意图」传递
 *
 * 场景：试卷列表点「组卷」、或新增试卷保存后跳转组卷页时，希望按试卷的组卷方式
 * 自动打开对应弹窗：2 随机组卷 → 随机组卷弹窗，3 AI 智能组卷 → AI 智能组卷弹窗。
 *
 * 为什么不挂在 URL query 上：
 *   Layout.vue / StudentLayout.vue 的 <router-view> 绑定了 :key="route.fullPath"，
 *   query 一变化当前页面组件就会被销毁重建——弹窗刚打开就随组件一起消失，
 *   init 还会重跑一次（此时参数已被清掉，于是永远弹不出来）。
 *   所以这里用模块级内存 Map 传递，读取即消费：刷新页面不会重复弹出。
 */

/** paperId → 组卷方式（2 随机 / 3 AI 智能） */
const intents = new Map()

/**
 * 登记意图（跳转组卷页之前调用）
 * @param {number} paperId 试卷 id
 * @param {number|string} paperType 组卷方式：2随机 3AI智能（1手动不登记，只打开组卷面板）
 */
export function setComposeIntent(paperId, paperType) {
  const id = Number(paperId)
  const type = Number(paperType)
  if (!Number.isFinite(id) || (type !== 2 && type !== 3)) return
  intents.set(id, type)
}

/**
 * 读取并清除意图（组卷页 init 调用，一次性消费）
 * @param {number} paperId 试卷 id
 * @returns {number|null} 2 或 3；无意图时返回 null
 */
export function takeComposeIntent(paperId) {
  const id = Number(paperId)
  const type = intents.get(id)
  if (type === undefined) return null
  intents.delete(id)
  return type
}
