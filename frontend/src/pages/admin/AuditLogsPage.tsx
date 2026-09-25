import { useCallback, useEffect, useState } from 'react'
import { api, errorMessage } from '../../api/http'
import { useToast } from '../../components/Toast'
import { Empty, Loading, PageHeader, Pager } from '../../components/ui'

interface Log {
  id: number
  time: string
  email: string | null
  fullName: string | null
  ipAddress: string | null
  action: string
  targetType: string | null
  targetId: number | null
  detail: string | null
}
interface UserOpt {
  id: number
  fullName: string
  email: string
}

const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCESS: 'Đăng nhập',
  LOGIN_FAILED: 'Đăng nhập thất bại',
  ACCOUNT_LOCKED: 'Khoá tài khoản',
  LOGOUT: 'Đăng xuất',
  PASSWORD_CHANGED: 'Đổi mật khẩu',
  PASSWORD_RESET: 'Đặt lại mật khẩu',
  USER_CREATED: 'Tạo tài khoản',
  ROLE_CHANGED: 'Đổi vai trò',
  ACCOUNT_DEACTIVATED: 'Vô hiệu hoá tài khoản',
  ACCOUNT_ACTIVATED: 'Kích hoạt tài khoản',
  GUEST_ID_VIEWED: 'Xem giấy tờ của khách',
  SETTINGS_CHANGED: 'Đổi tham số',
  BOOKING_CONFIRMED: 'Xác nhận booking',
  BOOKING_UPDATED: 'Sửa booking',
  BOOKING_CANCELLED: 'Huỷ booking',
  PAYMENT_RECORDED: 'Ghi nhận tiền',
}

/** S1-05: nhật ký chỉ đọc, lọc theo ngày và tài khoản, 50 dòng mỗi trang. */
export default function AuditLogsPage() {
  const toast = useToast()
  const [rows, setRows] = useState<Log[] | null>(null)
  const [users, setUsers] = useState<UserOpt[]>([])
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [userId, setUserId] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal] = useState(0)

  const load = useCallback(async () => {
    setRows(null)
    const { data } = await api.get('/audit-logs', {
      params: { from: from || undefined, to: to || undefined, userId: userId || undefined, action: action || undefined, page },
    })
    setRows(data.content)
    setTotalPages(data.totalPages)
    setTotal(data.totalElements)
  }, [from, to, userId, action, page])

  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
  }, [load, toast])
  useEffect(() => {
    api.get('/users', { params: { page: 0 } }).then((r) => setUsers(r.data.content)).catch(() => {})
  }, [])

  return (
    <>
      <PageHeader title="Nhật ký truy cập" subtitle={`Giờ Việt Nam (Asia/Ho_Chi_Minh) · ${total} dòng`} />
      <div className="row g-2 mb-3">
        <div className="col-6 col-md-2">
          <label className="form-label small">Từ ngày</label>
          <input type="date" className="form-control" value={from} onChange={(e) => { setPage(0); setFrom(e.target.value) }} />
        </div>
        <div className="col-6 col-md-2">
          <label className="form-label small">Đến ngày</label>
          <input type="date" className="form-control" value={to} onChange={(e) => { setPage(0); setTo(e.target.value) }} />
        </div>
        <div className="col-12 col-md-3">
          <label className="form-label small">Tài khoản</label>
          <select className="form-select" value={userId} onChange={(e) => { setPage(0); setUserId(e.target.value) }}>
            <option value="">Tất cả</option>
            {users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.email})</option>)}
          </select>
        </div>
        <div className="col-12 col-md-3">
          <label className="form-label small">Hành động</label>
          <select className="form-select" value={action} onChange={(e) => { setPage(0); setAction(e.target.value) }}>
            <option value="">Tất cả</option>
            {Object.entries(ACTION_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
      </div>
      <div className="card-table">
        {!rows ? <Loading /> : rows.length === 0 ? <Empty text="Không có dòng nhật ký nào trong khoảng đã chọn" /> : (
          <table className="table table-sm align-middle">
            <thead>
              <tr><th>Thời điểm</th><th>Tài khoản</th><th>Địa chỉ IP</th><th>Hành động</th><th>Chi tiết</th></tr>
            </thead>
            <tbody>
              {rows.map((l) => (
                <tr key={l.id}>
                  <td className="text-nowrap">{l.time}</td>
                  <td>{l.fullName ? <>{l.fullName}<div className="small text-muted">{l.email}</div></> : l.email ?? '—'}</td>
                  <td className="small">{l.ipAddress ?? '—'}</td>
                  <td>
                    <span className={`badge ${l.action.includes('FAILED') || l.action.includes('LOCKED') ? 'text-bg-danger' : 'text-bg-light border'}`}>
                      {ACTION_LABEL[l.action] ?? l.action}
                    </span>
                  </td>
                  <td className="small text-muted">
                    {l.detail}
                    {l.targetType && <span className="ms-1">[{l.targetType} #{l.targetId}]</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />
    </>
  )
}
