import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage, publicApi } from '../../api/http'
import { useToast } from '../../components/Toast'
import { passwordProblem } from './passwordRule'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const toast = useToast()
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const problem = passwordProblem(pw, confirm)
    if (problem) return setError(problem)
    setError('')
    setBusy(true)
    try {
      const { data } = await publicApi.post('/auth/reset-password', { token, newPassword: pw })
      toast('success', data.message)
      navigate('/login')
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
          <h1 className="h4 mb-3">Đặt lại mật khẩu</h1>
          {!token && <div className="alert alert-warning">Liên kết không hợp lệ. Vui lòng mở đúng liên kết trong email.</div>}
          {error && <div className="alert alert-danger py-2">{error}</div>}
          <form onSubmit={submit} noValidate>
            <div className="mb-3">
              <label className="form-label" htmlFor="pw">Mật khẩu mới</label>
              <input id="pw" type="password" className="form-control" autoComplete="new-password" value={pw}
                onChange={(e) => setPw(e.target.value)} />
              <div className="form-text">Tối thiểu 8 ký tự, gồm cả chữ và số.</div>
            </div>
            <div className="mb-3">
              <label className="form-label" htmlFor="confirm">Nhập lại mật khẩu mới</label>
              <input id="confirm" type="password" className="form-control" autoComplete="new-password" value={confirm}
                onChange={(e) => setConfirm(e.target.value)} />
            </div>
            <button className="btn btn-brand w-100 touch-btn" disabled={busy || !token}>
              {busy ? 'Đang lưu…' : 'Đặt lại mật khẩu'}
            </button>
          </form>
          <div className="text-center mt-3">
            <Link to="/forgot-password" className="small">Yêu cầu liên kết mới</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
