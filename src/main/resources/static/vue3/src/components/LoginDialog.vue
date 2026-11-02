<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { login as loginApi } from '@/services/ojApi'
import { setAuth } from '@/services/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'success'])

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 每次打开清空错误提示，但保留上次输入的用户名方便重试
watch(visible, (open) => {
  if (open) {
    errorMsg.value = ''
    password.value = ''
  }
})

async function submit() {
  if (loading.value) return
  errorMsg.value = ''

  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const data = await loginApi(username.value.trim(), password.value)
    setAuth(data.token, {
      username: data.username,
      nickname: data.nickname,
      role: data.role,
    })
    ElMessage.success('登录成功')
    visible.value = false
    password.value = ''
    emit('success', data)
  } catch (e) {
    errorMsg.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function close() {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="360px"
    :show-close="false"
    align-center
    class="login-dialog"
    append-to-body
  >
    <template #header>
      <div class="login-head">
        <span class="login-mark">OJ</span>
        <div>
          <strong>登录 Judge Desk</strong>
          <small>登录后才能提交代码</small>
        </div>
      </div>
    </template>

    <div class="login-body">
      <label class="field">
        <span>用户名</span>
        <el-input
          v-model="username"
          placeholder="admin"
          autocomplete="username"
          @keyup.enter="submit"
        />
      </label>

      <label class="field">
        <span>密码</span>
        <el-input
          v-model="password"
          type="password"
          placeholder="请输入密码"
          show-password
          autocomplete="current-password"
          @keyup.enter="submit"
        />
      </label>

      <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
    </div>

    <template #footer>
      <div class="login-foot">
        <button class="ghost-btn" type="button" @click="close">取消</button>
        <button class="primary-btn" type="button" :disabled="loading" @click="submit">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.login-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--accent-line, #cbd5e1);
  border-radius: 8px;
  background: var(--accent-soft, #eef2f7);
  color: var(--accent, #334155);
  font: 600 12px/1 var(--font-mono, monospace);
}

.login-head strong {
  display: block;
  font-size: 14px;
  color: var(--ink, #1f2937);
}

.login-head small {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-4, #94a3b8);
}

.login-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 2px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field > span {
  font-size: 12px;
  color: var(--ink-3, #475569);
}

.login-error {
  margin: 0;
  font-size: 12px;
  color: #d14343;
}

.login-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.ghost-btn,
.primary-btn {
  height: 32px;
  padding: 0 16px;
  border-radius: 6px;
  font-size: 12.5px;
  cursor: pointer;
}

.ghost-btn {
  border: 1px solid var(--line, #d8dee6);
  background: #fff;
  color: var(--ink-3, #475569);
}

.primary-btn {
  border: 1px solid var(--accent, #334155);
  background: var(--accent, #334155);
  color: #fff;
  font-weight: 600;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
