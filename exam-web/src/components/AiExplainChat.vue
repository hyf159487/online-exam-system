<template>
  <!-- 收起态：一行入口按钮，点开才生成讲解，不打扰看卷子 -->
  <div v-if="!visible" class="ai-entry">
    <el-button type="primary" plain size="small" @click="open">🤖 AI 智能讲解</el-button>
    <span v-if="hint" class="ai-entry-hint">{{ hint }}</span>
  </div>

  <!-- 展开态：讲解 + 多轮追问 -->
  <div v-else class="ai-explain">
    <div class="ai-header">
      <span class="ai-title">🤖 AI 智能讲解</span>
      <span v-if="source" class="ai-tag" :class="source === 'llm' ? 'llm' : 'local'">
        {{ source === 'llm' ? '大模型生成' : '本地解析引擎' }}
      </span>
      <el-button
        class="ai-restart"
        type="primary"
        size="small"
        plain
        :disabled="loading"
        @click="restart"
      >
        {{ msgs.length ? '重新开始' : '生成讲解' }}
      </el-button>
      <el-button size="small" :disabled="loading" @click="visible = false">收起</el-button>
    </div>

    <div ref="bodyRef" class="chat-body">
      <div v-if="!msgs.length" class="chat-empty">正在生成讲解…</div>
      <template v-else>
        <div v-for="(m, i) in msgs" :key="i" class="msg" :class="m.role">
          <div class="msg-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
          <div
            class="msg-bubble"
            :class="{ markdown: m.role === 'assistant' }"
            v-html="m.role === 'assistant' ? renderMarkdown(m.content) : m.content"
          ></div>
        </div>
        <div v-if="loading" class="msg assistant">
          <div class="msg-role">AI</div>
          <div class="msg-bubble markdown typing">正在思考…</div>
        </div>
      </template>
    </div>

    <div class="chat-input">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        :disabled="loading"
        placeholder="继续追问，例如：B 选项错在哪？换个角度讲讲 / 出 2 道同类题"
        @keydown.enter.exact.prevent="send()"
      />
      <div class="chat-actions">
        <span class="chat-tip">Enter 发送 / Shift+Enter 换行</span>
        <el-button size="small" :disabled="loading || !msgs.length" @click="clear">清空对话</el-button>
        <el-button
          type="primary"
          size="small"
          :loading="loading"
          :disabled="!input.trim()"
          @click="send()"
        >发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

/**
 * AI 讲解对话面板（试卷回看 / 错题本 / 背题模式共用）
 *
 * 组件只管对话 UI、Markdown 渲染与历史上下文维护；「讲哪道题」「有没有权限看」
 * 全部由调用方通过两个 fetcher 决定，因此能适配任意题目来源：
 * - explainFetcher()：生成首轮讲解（开场白由后端统一维护，前端不重复写提示词）
 * - chatFetcher({ question, history })：带上下文追问
 * 未配置大模型 api-key 时后端自行降级为本地规则引擎，组件按 source 打标签展示。
 */
const props = defineProps({
  /** 生成首轮讲解：() => Promise<{ data: { content, source } }> */
  explainFetcher: { type: Function, required: true },
  /** 追问：({ question, history }) => Promise<{ data: { content, source } }> */
  chatFetcher: { type: Function, required: true },
  /** 收起态按钮旁的一句说明（可空） */
  hint: { type: String, default: '' },
})

const visible = ref(false)
const loading = ref(false)
const source = ref('')
const msgs = ref([])
const input = ref('')
const bodyRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 展开面板：首次展开自动生成讲解，收起后再展开则保留上次对话 */
function open() {
  visible.value = true
  if (!msgs.value.length) {
    start()
  }
}

/** 生成/重新生成首轮讲解 */
async function start() {
  loading.value = true
  source.value = ''
  msgs.value = []
  scrollToBottom()
  try {
    const res = await props.explainFetcher()
    msgs.value = [{ role: 'assistant', content: contentOf(res) }]
    source.value = res?.data?.source || ''
  } catch (err) {
    msgs.value = [{ role: 'assistant', content: '讲解生成失败：' + errorText(err) }]
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

/** 发送一轮提问（首轮之后的追问都带上历史上下文） */
async function send() {
  const question = input.value.trim()
  if (!question || loading.value) return
  msgs.value.push({ role: 'user', content: question })
  input.value = ''
  loading.value = true
  source.value = ''
  scrollToBottom()
  const history = msgs.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
  try {
    const res = await props.chatFetcher({ question, history })
    msgs.value.push({ role: 'assistant', content: contentOf(res) })
    source.value = res?.data?.source || ''
  } catch (err) {
    msgs.value.push({ role: 'assistant', content: '追问失败：' + errorText(err) })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function clear() {
  msgs.value = []
  input.value = ''
  source.value = ''
  scrollToBottom()
}

/** 头部按钮：无对话时=生成讲解；已有对话时=清空重来，避免新旧讲解混在一起 */
function restart() {
  if (!msgs.value.length) {
    start()
    return
  }
  clear()
  start()
}

function contentOf(res) {
  return res?.data?.content || '（AI 未返回讲解内容，请稍后重试）'
}

/** 失败提示由响应拦截器统一弹出，这里只取可读文案拼进气泡 */
function errorText(err) {
  return err?.msg || err?.message || '网络异常'
}

/** 简易 Markdown 渲染（标题/加粗/行内代码/列表/换行），内容来自后端 AI，先转义再渲染 */
function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/```([\s\S]*?)```/g, (m, code) => `<pre class="md-code">${code.trim()}</pre>`)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^###\s+(.+)$/gm, '<h4 class="md-h4">$1</h4>')
    .replace(/^##\s+(.+)$/gm, '<h3 class="md-h3">$1</h3>')
    .replace(/^#\s+(.+)$/gm, '<h2 class="md-h2">$1</h2>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li class="md-li">$1</li>')
    .replace(/(<li[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
    .replace(/\n/g, '<br>')
}
</script>

<style scoped>
/* ===== 收起态入口 ===== */
.ai-entry {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-entry-hint {
  font-size: 12px;
  color: #c0c4cc;
}

/* ===== 展开态面板 ===== */
.ai-explain {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.ai-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(90deg, #f5f7ff, #f0f4ff);
  border-bottom: 1px solid #ebeef5;
}
.ai-title {
  font-size: 14px;
  font-weight: 700;
  color: #5b7cfa;
}
.ai-tag {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  background: #f0f2f5;
  color: #909399;
}
.ai-tag.llm {
  background: #ecf5ff;
  color: #409eff;
}
.ai-tag.local {
  background: #fdf6ec;
  color: #e6a23c;
}
.ai-restart {
  margin-left: auto;
}

/* 对话区 */
.chat-body {
  max-height: 340px;
  overflow-y: auto;
  padding: 12px 14px;
  background: #fafbfc;
}
.chat-empty {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 18px 8px;
}
.msg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.msg.user {
  justify-content: flex-end;
}
.msg-role {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}
.msg.user .msg-role {
  background: #409eff;
  order: 2;
}
.msg.assistant .msg-role {
  background: #5b7cfa;
}
.msg-bubble {
  max-width: 82%;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  border: 1px solid #e4e7ed;
  color: #303133;
}
.msg.user .msg-bubble {
  background: #409eff;
  color: #fff;
  border: none;
}
.msg-bubble.typing {
  color: #909399;
}

/* 输入区 */
.chat-input {
  padding: 10px 14px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}
.chat-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.chat-tip {
  margin-right: auto;
  font-size: 12px;
  color: #c0c4cc;
}

/* Markdown 基础样式 */
.markdown {
  white-space: normal;
}
.markdown .md-h2 {
  font-size: 16px;
  margin: 6px 0 4px;
  color: #303133;
}
.markdown .md-h3 {
  font-size: 15px;
  margin: 6px 0 4px;
  color: #303133;
}
.markdown .md-h4 {
  font-size: 14px;
  margin: 6px 0 4px;
  color: #409eff;
}
.markdown .md-li {
  margin: 2px 0 2px 16px;
  list-style: disc;
}
.markdown code {
  background: #f2f4f7;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 13px;
  color: #c45656;
}
.markdown pre.md-code {
  background: #f6f8fa;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  margin: 6px 0;
}
.markdown strong {
  font-weight: 700;
}
</style>
