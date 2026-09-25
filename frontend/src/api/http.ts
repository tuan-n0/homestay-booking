import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

const ACCESS_KEY = 'hs_access'
const REFRESH_KEY = 'hs_refresh'

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_KEY)
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY)
  },
  save(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

/** Gọi API công khai (khách) — không gửi token. */
export const publicApi = axios.create({ baseURL: '/api' })

/** Gọi API nội bộ — tự gắn access token và tự làm mới khi hết hạn. */
export const api = axios.create({ baseURL: '/api' })

let onSessionExpired: () => void = () => {}
export function setSessionExpiredHandler(fn: () => void) {
  onSessionExpired = fn
}

api.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refresh = tokenStore.refresh
  if (!refresh) return null
  try {
    const { data } = await publicApi.post('/auth/refresh', { refreshToken: refresh })
    tokenStore.save(data.accessToken, data.refreshToken)
    return data.accessToken as string
  } catch {
    return null
  }
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true
      refreshing ??= refreshAccessToken().finally(() => {
        refreshing = null
      })
      const token = await refreshing
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
      tokenStore.clear()
      onSessionExpired()
    }
    return Promise.reject(error)
  },
)

/** Lấy thông báo lỗi tiếng Việt từ phản hồi của máy chủ. */
export function errorMessage(err: unknown, fallback = 'Có lỗi xảy ra, vui lòng thử lại'): string {
  if (axios.isAxiosError(err)) {
    const msg = (err.response?.data as { message?: string } | undefined)?.message
    if (msg) return msg
    if (!err.response) return 'Không kết nối được máy chủ, vui lòng kiểm tra mạng'
  }
  return fallback
}

export function errorData<T = Record<string, unknown>>(err: unknown): T | undefined {
  return axios.isAxiosError(err) ? (err.response?.data as T) : undefined
}
