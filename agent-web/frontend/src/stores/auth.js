import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { httpPost, httpGet } from '../api/http.js'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('auth_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('auth_user') || 'null'))

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const userId = computed(() => user.value?.id || '')
  const username = computed(() => user.value?.username || user.value?.nickname || '')

  function setAuth(t, u) {
    token.value = t
    user.value = u
    localStorage.setItem('auth_token', t)
    localStorage.setItem('auth_user', JSON.stringify(u))
  }

  function clearAuth() {
    token.value = ''
    user.value = null
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
  }

  async function login(uname, password) {
    const data = await httpPost('/user/login', { username: uname, password })
    setAuth(data.token, data.user)
    return data.user
  }

  async function register(uname, password, nickname) {
    const data = await httpPost('/user/register', { username: uname, password, nickname })
    setAuth(data.token, data.user)
    return data.user
  }

  async function checkAuth() {
    if (!token.value) return false
    try {
      const u = await httpGet('/user/me')
      user.value = u
      localStorage.setItem('auth_user', JSON.stringify(u))
      return true
    } catch {
      clearAuth()
      return false
    }
  }

  async function logout() {
    try {
      await httpPost('/user/logout', {})
    } catch { /* ignore */ }
    clearAuth()
  }

  return { token, user, isLoggedIn, isAdmin, userId, username, login, register, checkAuth, logout, clearAuth }
})
