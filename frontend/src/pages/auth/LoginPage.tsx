import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/admin" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const me = await login(email, password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(me.mustChangePassword ? '/admin/change-password' : from ?? '/admin', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container">
      <div className="card auth-card shadow-sm">
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <i className="bi bi-house-heart fs-1" style={{ color: 'var(--brand)' }} />
            <h1 className="h4 mt-2">Đăng nhập hệ thống</h1>
            <div className="text-muted small">Dành cho nhân viên homestay</div>
          </div>
          {error && <div className="alert alert-danger py-2">{error}</div>}
          <form onSubmit={submit} noValidate>
            <div className="mb-3">
              <label className="form-label" htmlFor="email">Email</label>
              <input id="email" type="email" className="form-control" autoComplete="username" required
                value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="mb-3">
              <label className="form-label" htmlFor="password">Mật khẩu</label>
              <input id="password" type="password" className="form-control" autoComplete="current-password" required
                value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <button className="btn btn-brand w-100 touch-btn" disabled={busy || !email || !password}>
              {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
            </button>
          </form>
          <div className="text-center mt-3">
            <Link to="/forgot-password" className="small">Quên mật khẩu?</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
