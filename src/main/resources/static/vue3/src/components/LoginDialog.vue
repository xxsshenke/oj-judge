<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { login as loginApi, loginBySms as loginBySmsApi, sendSmsCode } from '@/services/ojApi'
import { setAuth } from '@/services/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'success'])

/** 'password' | 'sms' */
const mode = ref('password')

const username = ref('')
const password = ref('')

const phone = ref('')
const smsCode = ref('')
const smsBusy = ref(false)

const loading = ref(false)
const errorMsg = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const PHONE_RE = /^1[3-9]\d{9}$/

// 每次打开清空敏感字段，但保留上次输入的用户名方便重试
watch(visible, (open) => {
  if (open) {
    errorMsg.value = ''
    password.value = ''
    smsCode.value = ''
  }
})

// 切 tab 时把错误提示清掉，否则「密码错误」会挂在手机号表单下面
watch(mode, () => {
  errorMsg.value = ''
})

function afterLogin(data) {
  setAuth(data.token, {
    username: data.username,
    nickname: data.nickname,
    role: data.role,
  })
  ElMessage.success('登录成功')
  visible.value = false
  password.value = ''
  smsCode.value = ''
  emit('success', data)
}

async function submitPassword() {
  if (loading.value) return
  errorMsg.value = ''

  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const data = await loginApi(username.value.trim(), password.value)
    afterLogin(data)
  } catch (e) {
    errorMsg.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

/**
 * 获取验证码。
 *
 * 后端当前是内部实现，直接把码返回给前端 —— 这里顺手自动填进输入框，
 * 用户不用手抄，演示也顺。接真实短信通道后删掉自动填充和弹窗即可。
 */
async function requestCode() {
  if (smsBusy.value) return
  errorMsg.value = ''

  const p = phone.value.trim()
  if (!PHONE_RE.test(p)) {
    errorMsg.value = '手机号格式不正确'
    return
  }

  smsBusy.value = true
  try {
    const data = await sendSmsCode(p)
    if (data?.smsCode) {
      smsCode.value = data.smsCode
      ElNotification({
        title: '验证码（后端内部实现）',
        message: `本次验证码：${data.smsCode}，5 分钟内有效`,
        type: 'success',
        duration: 10000,
      })
    }
  } catch (e) {
    errorMsg.value = e?.message || '获取验证码失败'
  } finally {
    smsBusy.value = false
  }
}

async function submitSms() {
  if (loading.value) return
  errorMsg.value = ''

  const p = phone.value.trim()
  if (!PHONE_RE.test(p)) {
    errorMsg.value = '手机号格式不正确'
    return
  }
  if (!/^\d{6}$/.test(smsCode.value)) {
    errorMsg.value = '请输入 6 位验证码'
    return
  }

  loading.value = true
  try {
    const data = await loginBySmsApi(p, smsCode.value)
    afterLogin(data)
  } catch (e) {
    errorMsg.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function submit() {
  return mode.value === 'password' ? submitPassword() : submitSms()
}

function close() {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="380px"
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

    <div class="login-tabs" role="tablist">
      <button
        type="button"
        role="tab"
        class="login-tab"
        :class="{ active: mode === 'password' }"
        :aria-selected="mode === 'password'"
        @click="mode = 'password'"
      >
        密码登录
      </button>
      <button
        type="button"
        role="tab"
        class="login-tab"
        :class="{ active: mode === 'sms' }"
        :aria-selected="mode === 'sms'"
        @click="mode = 'sms'"
      >
        手机号登录
      </button>
    </div>

    <div v-if="mode === 'password'" class="login-body">
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
    </div>

    <div v-else class="login-body">
      <label class="field">
        <span>手机号</span>
        <el-input
          v-model="phone"
          placeholder="11 位手机号"
          maxlength="11"
          autocomplete="tel"
          @keyup.enter="submit"
        />
      </label>

      <label class="field">
        <span>验证码</span>
        <div class="code-row">
          <el-input
            v-model="smsCode"
            placeholder="6 位数字"
            maxlength="6"
            autocomplete="one-time-code"
            @keyup.enter="submit"
          />
          <button class="code-btn" type="button" :disabled="smsBusy" @click="requestCode">
            {{ smsBusy ? '获取中…' : '获取验证码' }}
          </button>
        </div>
      </label>

      <p class="hint">未注册的手机号验证通过后会自动创建账号</p>
    </div>

    <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>

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

.login-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line, #e5e9ef);
}

.login-tab {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--ink-4, #94a3b8);
  font-size: 13px;
  cursor: pointer;
}

.login-tab:hover {
  color: var(--ink-3, #475569);
}

.login-tab.active {
  border-bottom-color: var(--accent, #334155);
  color: var(--accent, #334155);
  font-weight: 600;
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

.code-row {
  display: flex;
  gap: 8px;
}

.code-btn {
  flex: none;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--line, #d8dee6);
  border-radius: 6px;
  background: #fff;
  color: var(--ink-3, #475569);
  font-size: 12.5px;
  white-space: nowrap;
  cursor: pointer;
}

.code-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin: 0;
  font-size: 11.5px;
  color: var(--ink-4, #94a3b8);
}

.login-error {
  margin: 6px 0 0;
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
