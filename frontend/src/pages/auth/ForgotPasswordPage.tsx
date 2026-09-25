import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage, publicApi } from '../../api/http'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setMessage('')
    setBusy(true)
    try {
      const { data } = await publicApi.post('/auth/forgot-password', { email })
      setMessage(data.message)
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
          <h1 className="h4 mb-1">Quên mật khẩu</h1>
          <p className="text-muted small">Nhập email đăng nhập, chúng tôi sẽ gửi liên kết đặt lại mật khẩu.</p>
          {message && <div className="alert alert-success py-2">{message}</div>}
          {error && <div className="alert alert-danger py-2">{error}</div>}
          <form onSubmit={submit} noValidate>
            <div className="mb-3">
              <label className="form-label" htmlFor="email">Email</label>
              <input id="email" type="email" className="form-control" required value={email}
                onChange={(e) => setEmail(e.target.value)} />
            </div>
            <button className="btn btn-brand w-100 touch-btn" disabled={busy || !email}>
              {busy ? 'Đang gửi…' : 'Gửi liên kết đặt lại'}
            </button>
          </form>
          <div className="text-center mt-3">
            <Link to="/login" className="small">‹ Quay lại đăng nhập</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
