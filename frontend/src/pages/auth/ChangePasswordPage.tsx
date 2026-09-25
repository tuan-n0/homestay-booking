import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { passwordProblem } from './passwordRule'

export default function ChangePasswordPage() {
  const { user, clearLocal } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [oldPw, setOldPw] = useState('')
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
      const { data } = await api.put('/auth/change-password', { oldPassword: oldPw, newPassword: pw })
      // Mọi phiên cũ (kể cả phiên này) đã bị huỷ → đăng nhập lại
      clearLocal()
      toast('success', data.message)
      navigate('/login', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card shadow-sm" style={{ maxWidth: 480 }}>
      <div className="card-body p-4">
        <h1 className="h4 mb-1">Đổi mật khẩu</h1>
        {user?.mustChangePassword ? (
          <div className="alert alert-warning py-2 small">
            Đây là lần đăng nhập đầu tiên bằng mật khẩu tạm. Bạn cần đổi mật khẩu trước khi sử dụng hệ thống.
          </div>
        ) : (
          <p className="text-muted small">Sau khi đổi, mọi phiên đăng nhập cũ trên các thiết bị khác sẽ bị đăng xuất.</p>
        )}
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <form onSubmit={submit} noValidate>
          <div className="mb-3">
            <label className="form-label" htmlFor="old">Mật khẩu hiện tại</label>
            <input id="old" type="password" className="form-control" autoComplete="current-password" value={oldPw}
              onChange={(e) => setOldPw(e.target.value)} />
          </div>
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
          <button className="btn btn-brand touch-btn px-4" disabled={busy || !oldPw}>
            {busy ? 'Đang lưu…' : 'Đổi mật khẩu'}
          </button>
        </form>
      </div>
    </div>
  )
}
