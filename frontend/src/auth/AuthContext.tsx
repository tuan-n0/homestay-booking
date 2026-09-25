import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, publicApi, setSessionExpiredHandler, tokenStore } from '../api/http'

export interface Me {
  id: number
  email: string
  fullName: string
  role: string
  roleName: string
  permissions: string[]
  mustChangePassword: boolean
}

interface AuthState {
  user: Me | null
  loading: boolean
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  reload: () => Promise<void>
  can: (...permissions: string[]) => boolean
  clearLocal: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    if (!tokenStore.access) {
      setUser(null)
      return
    }
    try {
      const { data } = await api.get<Me>('/auth/me')
      setUser(data)
    } catch {
      setUser(null)
    }
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null))
    reload().finally(() => setLoading(false))
  }, [reload])

  const login = useCallback(async (email: string, password: string) => {
    const { data } = await publicApi.post('/auth/login', { email, password })
    tokenStore.save(data.accessToken, data.refreshToken)
    setUser(data.user)
    return data.user as Me
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout', { refreshToken: tokenStore.refresh })
    } catch {
      /* phiên có thể đã hết hạn — vẫn xoá ở máy */
    }
    tokenStore.clear()
    setUser(null)
  }, [])

  const clearLocal = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const can = useCallback(
    (...permissions: string[]) => !!user && permissions.some((p) => user.permissions.includes(p)),
    [user],
  )

  const value = useMemo(
    () => ({ user, loading, login, logout, reload, can, clearLocal }),
    [user, loading, login, logout, reload, can, clearLocal],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider')
  return ctx
}
