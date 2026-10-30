<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorState } from '@codemirror/state'
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
  dropCursor,
  rectangularSelection,
  crosshairCursor,
} from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { java } from '@codemirror/lang-java'
import { bracketMatching, indentOnInput, indentUnit, syntaxHighlighting, HighlightStyle } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete'
import { highlightSelectionMatches, searchKeymap } from '@codemirror/search'
import { tags as t } from '@lezer/highlight'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime, verdictText, verdictTone } from '@/constants/problem'

const props = defineProps({
  modelValue: { type: String, default: '' },
  submitting: { type: Boolean, default: false },
  judging: { type: Boolean, default: false },
  submission: { type: Object, default: null },
  sampleInput: { type: String, default: '' },
  fileName: { type: String, default: 'Main' },
})
const emit = defineEmits(['update:modelValue', 'submit', 'open-submissions'])

const editorHost = ref(null)
const fullscreen = ref(false)
const panelOpen = ref(false)
const activeTab = ref('result')
const customInput = ref('')
const codeCopyState = ref('')
let view = null

/* ---------- 主题：贴近 VS Code Dark+ ---------- */
const editorTheme = EditorView.theme(
  {
    '&': { height: '100%', backgroundColor: '#1e1e1e', color: '#d4d4d4', fontSize: '13.5px' },
    '.cm-scroller': {
      fontFamily: "'JetBrains Mono', 'Cascadia Mono', Consolas, 'Courier New', monospace",
      lineHeight: '1.7',
    },
    '.cm-content': { padding: '12px 0 48px', caretColor: '#aeafad' },
    '.cm-line': { padding: '0 16px 0 4px' },
    '.cm-gutters': { backgroundColor: '#1e1e1e', color: '#7d7d7d', border: 'none', paddingLeft: '8px' },
    '.cm-lineNumbers .cm-gutterElement': { padding: '0 10px 0 6px', minWidth: '34px' },
    '.cm-activeLine': { backgroundColor: 'rgba(255, 255, 255, 0.04)' },
    '.cm-activeLineGutter': { backgroundColor: 'transparent', color: '#c6c6c6' },
    '&.cm-focused': { outline: 'none' },
    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: '#264f78' },
    '.cm-cursor, .cm-dropCursor': { borderLeftColor: '#aeafad', borderLeftWidth: '2px' },
    '.cm-matchingBracket, &.cm-focused .cm-matchingBracket': {
      backgroundColor: 'rgba(110, 160, 220, 0.25)',
      outline: '1px solid #3b6ea5',
      color: 'inherit',
    },
    '.cm-selectionMatch': { backgroundColor: 'rgba(114, 119, 105, 0.45)' },
    '.cm-searchMatch': { backgroundColor: 'rgba(234, 92, 0, 0.35)', outline: '1px solid #b8752f' },
    '.cm-panels': { backgroundColor: '#252526', color: '#cccccc' },
    '.cm-panels input, .cm-panels button': { backgroundColor: '#3c3c3c', color: '#cccccc', border: '1px solid #555' },
    '.cm-tooltip': { backgroundColor: '#252526', border: '1px solid #454545', color: '#cccccc' },
  },
  { dark: true },
)

const editorHighlight = HighlightStyle.define([
  { tag: [t.keyword, t.controlKeyword, t.moduleKeyword, t.operatorKeyword, t.definitionKeyword], color: '#569cd6' },
  { tag: [t.typeName, t.className, t.namespace], color: '#4ec9b0' },
  { tag: [t.string, t.character], color: '#ce9178' },
  { tag: [t.number, t.bool, t.null, t.atom], color: '#b5cea8' },
  { tag: [t.comment, t.lineComment, t.blockComment, t.docComment], color: '#6a9955', fontStyle: 'italic' },
  { tag: [t.annotation], color: '#dcdcaa' },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: '#dcdcaa' },
  { tag: [t.variableName, t.propertyName, t.definition(t.variableName)], color: '#9cdcfe' },
  { tag: [t.operator, t.punctuation, t.separator, t.bracket], color: '#d4d4d4' },
  { tag: t.invalid, color: '#f44747' },
])

function buildExtensions() {
  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightActiveLine(),
    history(),
    drawSelection(),
    dropCursor(),
    EditorState.allowMultipleSelections.of(true),
    rectangularSelection(),
    crosshairCursor(),
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    highlightSelectionMatches(),
    indentUnit.of('    '),
    EditorState.tabSize.of(4),
    java(),
    editorTheme,
    syntaxHighlighting(editorHighlight),
    keymap.of([
      {
        key: 'Mod-Enter',
        preventDefault: true,
        run: () => {
          emit('submit')
          return true
        },
      },
      ...closeBracketsKeymap,
      ...defaultKeymap,
      ...searchKeymap,
      ...historyKeymap,
      indentWithTab,
    ]),
    EditorView.updateListener.of((update) => {
      if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
    }),
  ]
}

onMounted(() => {
  view = new EditorView({
    state: EditorState.create({ doc: props.modelValue, extensions: buildExtensions() }),
    parent: editorHost.value,
  })
  customInput.value = props.sampleInput
  window.addEventListener('keydown', onWindowKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
  document.body.classList.remove('editor-lock-scroll')
  view?.destroy()
  view = null
})

watch(
  () => props.modelValue,
  (value) => {
    if (!view) return
    if (value === view.state.doc.toString()) return
    view.setState(EditorState.create({ doc: value ?? '', extensions: buildExtensions() }))
  },
)

watch(
  () => props.sampleInput,
  (value) => {
    customInput.value = value || ''
  },
)

watch(
  () => props.judging,
  (value) => {
    if (value) {
      activeTab.value = 'result'
      panelOpen.value = true
    }
  },
)

watch(fullscreen, (value) => {
  document.body.classList.toggle('editor-lock-scroll', value)
})

function onWindowKeydown(event) {
  if (event.key === 'Escape' && fullscreen.value) fullscreen.value = false
}

function toggleTab(tab) {
  if (activeTab.value === tab && panelOpen.value) {
    panelOpen.value = false
    return
  }
  activeTab.value = tab
  panelOpen.value = true
}

function showResult() {
  activeTab.value = 'result'
  panelOpen.value = true
}

const pending = computed(() => {
  if (props.judging || props.submitting) return true
  return ['PENDING', 'JUDGING'].includes(props.submission?.status)
})

const currentVerdict = computed(() => props.submission?.verdict || '')

function useSampleInput() {
  customInput.value = props.sampleInput || ''
  if (!customInput.value) ElMessage.info('该题目暂未配置示例输入')
}

function clearInput() {
  customInput.value = ''
}

async function copyCode() {
  const text = props.modelValue || ''
  if (!text) {
    ElMessage.info('当前没有可复制的代码')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('代码已复制到剪贴板')
  } catch {
    const helper = document.createElement('textarea')
    helper.value = text
    helper.style.position = 'fixed'
    helper.style.opacity = '0'
    document.body.appendChild(helper)
    helper.select()
    document.execCommand('copy')
    helper.remove()
    ElMessage.success('代码已复制到剪贴板')
  }
}

function downloadCode() {
  const blob = new Blob([props.modelValue || ''], { type: 'text/x-java;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const safeName = (props.fileName || 'Main').replace(/[\\/:*?"<>|]/g, '_')
  link.href = url
  link.download = safeName.endsWith('.java') ? safeName : `${safeName}.java`
  link.click()
  URL.revokeObjectURL(url)
}

async function resetCode() {
  try {
    await ElMessageBox.confirm('重置后将恢复初始代码模板，当前编辑内容会丢失。', '重置代码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      type: 'warning',
    })
    emit('reset')
    ElMessage.success('已恢复初始模板')
  } catch {
    /* 用户取消 */
  }
}

function handleSelfTest() {
  activeTab.value = 'input'
  panelOpen.value = true
  ElMessage.warning('当前后端未提供运行接口，自测运行暂不可用；可先「保存并提交」查看判题结果。')
}

function onLanguageCommand(command) {
  if (command === 'java') return
  ElMessage.info('当前判题后端仅支持 Java 21')
}

function openSubmissions() {
  emit('open-submissions')
}

defineExpose({ showResult, focusEditor: () => view?.focus() })
</script>

<template>
  <section class="editor-card" :class="{ 'is-fullscreen': fullscreen }">
    <div class="editor-topbar">
      <div class="topbar-left">
        <el-dropdown trigger="click" @command="onLanguageCommand">
          <button class="language-select" type="button">
            <span>Java 21</span>
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="java">Java 21<span class="lang-current">当前</span></el-dropdown-item>
              <el-dropdown-item command="cpp" disabled>C++ 17 · 暂未支持</el-dropdown-item>
              <el-dropdown-item command="python" disabled>Python 3 · 暂未支持</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="mode-badge">ACM 模式</span>
      </div>

      <div class="topbar-tools">
        <button class="tool-button" type="button" title="提交记录" aria-label="提交记录" @click="openSubmissions">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M3 12a9 9 0 1 0 3-6.7" />
            <path d="M3 4v5h5" />
            <path d="M12 7v5l3 2" />
          </svg>
        </button>
        <button class="tool-button" type="button" title="重置代码" aria-label="重置代码" @click="resetCode">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M4 9a8 8 0 1 1-1 4" />
            <path d="M4 3v6h6" />
          </svg>
        </button>
        <button class="tool-button" type="button" title="下载代码" aria-label="下载代码" @click="downloadCode">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M12 3v12" />
            <path d="M7 10l5 5 5-5" />
            <path d="M4 20h16" />
          </svg>
        </button>
        <button class="tool-button" type="button" title="复制代码" aria-label="复制代码" @click="copyCode">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <rect x="9" y="9" width="11" height="11" rx="2" />
            <path d="M5 15V5a2 2 0 0 1 2-2h10" />
          </svg>
        </button>
        <button class="tool-button" type="button" :title="fullscreen ? '退出全屏' : '全屏编辑'" :aria-label="fullscreen ? '退出全屏' : '全屏编辑'" @click="fullscreen = !fullscreen">
          <svg v-if="!fullscreen" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M4 9V4h5" />
            <path d="M20 15v5h-5" />
            <path d="M4 4l6 6" />
            <path d="M20 20l-6-6" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M9 4v5H4" />
            <path d="M15 20v-5h5" />
            <path d="M9 9L4 4" />
            <path d="M15 15l5 5" />
          </svg>
        </button>
      </div>
    </div>

    <div class="editor-surface">
      <div ref="editorHost" class="editor-host"></div>
    </div>

    <div class="editor-dock">
      <div v-if="panelOpen" class="dock-panel">
        <div v-if="activeTab === 'result'" class="result-panel">
          <div v-if="pending" class="result-pending">
            <span class="spinner"></span>
            <div>
              <strong>正在评测</strong>
              <p>代码已进入判题队列，结果会自动刷新，无需离开页面。</p>
            </div>
          </div>
          <template v-else-if="submission">
            <div class="result-head">
              <span class="verdict-chip" :class="'tone-' + verdictTone(currentVerdict)">
                {{ verdictText(currentVerdict) }}
              </span>
              <span class="result-meta">耗时 {{ submission.timeCost == null ? '-' : submission.timeCost + ' ms' }}</span>
              <span class="result-meta">最近提交于 {{ formatTime(submission.createTime) }}</span>
              <button class="result-more" type="button" @click="openSubmissions">查看全部提交</button>
            </div>
            <p class="result-message" :class="{ 'is-error': submission.verdict !== 'AC' }">
              {{ submission.message || (submission.verdict === 'AC' ? '全部测试点通过。' : '暂无详细信息') }}
            </p>
          </template>
          <div v-else class="dock-empty">
            <span>还没有提交记录</span>
            <p>写好代码后点击「保存并提交」，这里会显示判题结果。</p>
          </div>
        </div>

        <div v-else class="selftest-panel">
          <div class="selftest-head">
            <span>自测输入（仅本地编辑，不会发往后端）</span>
            <div class="selftest-actions">
              <button type="button" @click="useSampleInput">填入示例</button>
              <button type="button" @click="clearInput">清空</button>
            </div>
          </div>
          <textarea v-model="customInput" spellcheck="false" placeholder="在这里粘贴或编写自定义输入数据…"></textarea>
        </div>
      </div>

      <div class="dock-bar">
        <div class="dock-tabs">
          <button type="button" :class="{ active: activeTab === 'result' && panelOpen }" @click="toggleTab('result')">运行结果</button>
          <button type="button" :class="{ active: activeTab === 'input' && panelOpen }" @click="toggleTab('input')">自测输入</button>
        </div>
        <div class="dock-actions">
          <button class="run-button" type="button" title="当前后端未提供运行接口" @click="handleSelfTest">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M8 5.5v13l11-6.5z" /></svg>
            自测运行
          </button>
          <button class="submit-button" type="button" title="代码草稿会自动保存在本地浏览器" :disabled="submitting" @click="emit('submit')">
            {{ submitting ? '提交中…' : '保存并提交' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.editor-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #1e1e1e;
}

.editor-card.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

:global(body.editor-lock-scroll) {
  overflow: hidden;
}

.editor-topbar {
  flex: 0 0 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 10px 0 12px;
  background: #252526;
  border-bottom: 1px solid #303030;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.language-select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: #dedede;
  font-size: 12.5px;
}

.language-select:hover {
  background: #333333;
}

.mode-badge {
  padding-left: 10px;
  border-left: 1px solid #3c3c3c;
  color: #9aa0a6;
  font-size: 12px;
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 2px;
}

.tool-button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: #9aa0a6;
  transition: background 0.15s ease, color 0.15s ease;
}

.tool-button:hover {
  background: #333333;
  color: #ffffff;
}

.editor-surface {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-host {
  height: 100%;
}

.editor-host :deep(.cm-editor) {
  height: 100%;
}

.editor-dock {
  flex: 0 0 auto;
  background: #252526;
  border-top: 1px solid #303030;
}

.dock-panel {
  max-height: 240px;
  overflow: hidden;
  border-bottom: 1px solid #303030;
}

.result-panel {
  max-height: 240px;
  padding: 14px 18px 16px;
  overflow: auto;
  color: #d4d4d4;
  font-size: 12.5px;
}

.result-pending {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 0;
  color: #d4d4d4;
}

.result-pending strong {
  display: block;
  margin-bottom: 6px;
  font-size: 13.5px;
}

.result-pending p {
  color: #9aa0a6;
  font-size: 12px;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid #3c3c3c;
  border-top-color: #3fb950;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.verdict-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: var(--r-sm);
  font-size: 12.5px;
  font-weight: 600;
}

.verdict-chip.tone-ac {
  background: rgba(63, 185, 80, 0.16);
  color: #6fd68a;
}

.verdict-chip.tone-bad {
  background: rgba(220, 38, 38, 0.16);
  color: #f08b8b;
}

.verdict-chip.tone-warn {
  background: rgba(217, 119, 6, 0.16);
  color: #f0bb6a;
}

.verdict-chip.tone-neutral {
  background: rgba(255, 255, 255, 0.08);
  color: #c9c9c9;
}

.result-meta {
  color: #9aa0a6;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.result-more {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: #7cc4ff;
  font-size: 12px;
}

.result-more:hover {
  text-decoration: underline;
}

.result-message {
  margin-top: 12px;
  color: #c9c9c9;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-message.is-error {
  color: #eeb5b5;
}

.dock-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 26px 0 28px;
  color: #9aa0a6;
  font-size: 12.5px;
}

.dock-empty p {
  color: #7d838b;
  font-size: 12px;
}

.selftest-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 184px;
  padding: 10px 14px 12px;
}

.selftest-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #9aa0a6;
  font-size: 12px;
}

.selftest-actions {
  display: flex;
  gap: 12px;
}

.selftest-actions button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #7cc4ff;
  font-size: 12px;
}

.selftest-actions button:hover {
  text-decoration: underline;
}

.selftest-panel textarea {
  flex: 1;
  min-height: 0;
  padding: 8px 10px;
  resize: none;
  border: 1px solid #3a3a3a;
  border-radius: var(--r-sm);
  outline: none;
  background: #1e1e1e;
  color: #d4d4d4;
  font: 12.5px/1.65 var(--font-mono);
}

.selftest-panel textarea:focus {
  border-color: #3d7a5a;
}

.dock-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px 0 4px;
}

.dock-tabs {
  display: flex;
  align-self: stretch;
}

.dock-tabs button {
  position: relative;
  padding: 0 14px;
  border: 0;
  background: transparent;
  color: #9aa0a6;
  font-size: 12.5px;
}

.dock-tabs button:hover {
  color: #e5e5e5;
}

.dock-tabs button.active {
  color: #ffffff;
}

.dock-tabs button.active::after {
  content: '';
  position: absolute;
  right: 10px;
  bottom: 0;
  left: 10px;
  height: 2px;
  background: #3fb950;
}

.dock-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.run-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 14px;
  border: 1px solid #35624a;
  border-radius: var(--r-sm);
  background: transparent;
  color: #8ad3a3;
  font-size: 12.5px;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.run-button:hover {
  border-color: #427a5b;
  background: rgba(63, 185, 80, 0.1);
}

.submit-button {
  height: 30px;
  padding: 0 18px;
  border: 1px solid var(--accent);
  border-radius: var(--r-sm);
  background: var(--accent);
  color: #ffffff;
  font-size: 12.5px;
  font-weight: 600;
  transition: background 0.15s ease;
}

.submit-button:hover:not(:disabled) {
  background: var(--accent-strong);
}

.submit-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tool-button:focus-visible,
.language-select:focus-visible,
.dock-tabs button:focus-visible,
.run-button:focus-visible,
.submit-button:focus-visible,
.result-more:focus-visible {
  outline: 2px solid #7cc4ff;
  outline-offset: 2px;
}

:deep(.cm-panels) {
  font-family: inherit;
}

:deep(.cm-searchMatch.cm-searchMatch-selected) {
  background: rgba(234, 92, 0, 0.55);
}
</style>
