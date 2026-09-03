import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'

const TOKEN_KEY = 'cms_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const mustChangePassword = computed(() => user.value?.mustChangePassword || false)

  function setToken(t) {
    token.value = t
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  }

  async function login(username, password) {
    const res = await authApi.login(username, password)
    setToken(res.token)
    user.value = res
    return res
  }

  async function fetchMe() {
    try {
      user.value = await authApi.me()
    } catch (e) {
      // token 失效
      setToken('')
      user.value = null
    }
  }

  async function changePassword(oldPassword, newPassword) {
    await authApi.changePassword(oldPassword, newPassword)
    user.value.mustChangePassword = false
  }

  function logout() {
    setToken('')
    user.value = null
  }

  return { token, user, isLoggedIn, role, mustChangePassword, login, fetchMe, changePassword, logout }
})
