import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Empty, Loading, Modal, PageHeader, Pager } from '../../components/ui'

interface UserRow {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: string
  roleName: string
  active: boolean
  mustChangePassword: boolean
  locked: boolean
  createdAt: string
}
interface Role {
  code: string
  name: string
}
interface Form {
  id?: number
  fullName: string
  email: string
  phone: string
  role: string
  active: boolean
}
const emptyForm: Form = { fullName: '', email: '', phone: '', role: 'RECEPTIONIST', active: true }

export default function UsersPage() {
  const { can } = useAuth()
  const toast = useToast()
  const canManage = can('USER_MANAGE')
  const [rows, setRows] = useState<UserRow[] | null>(null)
  const [roles, setRoles] = useState<Role[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [form, setForm] = useState<Form | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    const { data } = await api.get('/users', { params: { keyword, page } })
    setRows(data.content)
    setTotalPages(data.totalPages)
  }, [keyword, page])

  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
  }, [load, toast])
  useEffect(() => {
    api.get('/users/roles').then((r) => setRoles(r.data))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    setBusy(true)
    setError('')
    try {
      const body = { ...form, phone: form.phone || null }
      if (form.id) {
        await api.put(`/users/${form.id}`, body)
        toast('success', 'Đã cập nhật tài khoản')
      } else {
        await api.post('/users', body)
        toast('success', `Đã tạo tài khoản, mật khẩu tạm đã được gửi tới ${form.email}`)
      }
      setForm(null)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Tài khoản nhân viên"
        subtitle="Mỗi tài khoản gán đúng một vai trò"
        actions={
          canManage && (
            <button className="btn btn-brand" onClick={() => { setError(''); setForm({ ...emptyForm }) }}>
              <i className="bi bi-plus-lg me-1" /> Tạo tài khoản
            </button>
          )
        }
      />
      <div className="mb-3" style={{ maxWidth: 360 }}>
        <input className="form-control" placeholder="Tìm theo tên hoặc email…" value={keyword}
          onChange={(e) => { setPage(0); setKeyword(e.target.value) }} />
      </div>
      <div className="card-table">
        {!rows ? <Loading /> : rows.length === 0 ? <Empty text="Chưa có tài khoản nào" /> : (
          <table className="table table-hover align-middle">
            <thead>
              <tr>
                <th>Họ tên</th><th>Email</th><th>Điện thoại</th><th>Vai trò</th><th>Trạng thái</th><th>Ngày tạo</th><th />
              </tr>
            </thead>
            <tbody>
              {rows.map((u) => (
                <tr key={u.id}>
                  <td>{u.fullName}</td>
                  <td>{u.email}</td>
                  <td>{u.phone ?? '—'}</td>
                  <td>{u.roleName}</td>
                  <td>
                    {u.active ? <span className="badge text-bg-success">Hoạt động</span> : <span className="badge text-bg-secondary">Vô hiệu hoá</span>}
                    {u.locked && <span className="badge text-bg-danger ms-1">Đang khoá</span>}
                    {u.mustChangePassword && <span className="badge text-bg-warning ms-1">Chưa đổi MK tạm</span>}
                  </td>
                  <td className="small text-muted">{u.createdAt}</td>
                  <td className="text-end">
                    {canManage && (
                      <button className="btn btn-sm btn-outline-secondary" onClick={() => {
                        setError('')
                        setForm({ id: u.id, fullName: u.fullName, email: u.email, phone: u.phone ?? '', role: u.role, active: u.active })
                      }}>Sửa</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      <Modal title={form?.id ? 'Sửa tài khoản' : 'Tạo tài khoản'} show={!!form} onClose={() => setForm(null)}
        footer={<>
          <button className="btn btn-light" onClick={() => setForm(null)}>Huỷ</button>
          <button className="btn btn-brand" form="user-form" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button>
        </>}>
        {form && (
          <form id="user-form" onSubmit={save} noValidate>
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <div className="mb-2">
              <label className="form-label">Họ tên *</label>
              <input className="form-control" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>
            <div className="mb-2">
              <label className="form-label">Email *</label>
              <input type="email" className="form-control" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="mb-2">
              <label className="form-label">Số điện thoại</label>
              <input className="form-control" value={form.phone} placeholder="0912345678" onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div className="mb-2">
              <label className="form-label">Vai trò *</label>
              <select className="form-select" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                {roles.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
              </select>
            </div>
            <div className="form-check form-switch mt-3">
              <input id="active" className="form-check-input" type="checkbox" checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })} />
              <label className="form-check-label" htmlFor="active">Đang hoạt động</label>
            </div>
            {!form.id && <div className="form-text mt-2">Mật khẩu tạm sẽ được gửi qua email, người dùng bắt buộc đổi ở lần đăng nhập đầu.</div>}
            {form.id && !form.active && <div className="form-text text-danger mt-2">Vô hiệu hoá sẽ đăng xuất người này ngay lập tức.</div>}
          </form>
        )}
      </Modal>
    </>
  )
}
