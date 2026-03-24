/**
 * HTTP client with auth header injection.
 */
const BASE_URL = ''

function getToken() {
  return localStorage.getItem('auth_token') || ''
}

function authHeaders() {
  const token = getToken()
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  return headers
}

export async function httpGet(path) {
  const resp = await fetch(BASE_URL + path, { headers: authHeaders() })
  if (resp.status === 401) {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
    window.location.hash = '#/login'
    throw new Error('未登录')
  }
  if (!resp.ok) {
    const body = await resp.json().catch(() => ({}))
    throw new Error(body.error || `请求失败: ${resp.status}`)
  }
  return resp.json()
}

export async function httpPost(path, data) {
  const resp = await fetch(BASE_URL + path, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  })
  if (resp.status === 401) {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
    window.location.hash = '#/login'
    throw new Error('未登录')
  }
  if (!resp.ok) {
    const body = await resp.json().catch(() => ({}))
    throw new Error(body.error || `请求失败: ${resp.status}`)
  }
  return resp.json()
}

export async function httpPut(path, data) {
  const resp = await fetch(BASE_URL + path, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data)
  })
  if (resp.status === 401) {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
    window.location.hash = '#/login'
    throw new Error('未登录')
  }
  if (!resp.ok) {
    const body = await resp.json().catch(() => ({}))
    throw new Error(body.error || `请求失败: ${resp.status}`)
  }
  return resp.json()
}

export async function httpDelete(path) {
  const resp = await fetch(BASE_URL + path, {
    method: 'DELETE',
    headers: authHeaders()
  })
  if (!resp.ok) {
    const body = await resp.json().catch(() => ({}))
    throw new Error(body.error || `请求失败: ${resp.status}`)
  }
  return resp.json()
}
