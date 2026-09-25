import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorData, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Empty, Loading, Modal, PageHeader, StatusBadge } from '../../components/ui'
import { formatDate, todayIso } from '../../utils/format'
import type { RoomType } from './RoomTypesPage'

interface Room {
  id: number
  roomNumber: string
  floor: number
  roomTypeId: number
  roomTypeName: string
  note: string | null
  active: boolean
  status: string
  statusLabel: string
  maintenanceReason: string | null
  maintenanceFrom: string | null
  maintenanceTo: string | null
}
interface Form {
  id?: number
  roomNumber: string
  floor: number
  roomTypeId: number
  note: string
  active: boolean
}
interface AffectedBooking {
  id: number
  code: string
  customerName: string
  checkInDate: string
  checkOutDate: string
  status: string
}
interface History {
  id: number
  fromStatus: string
  toStatus: string
  note: string | null
  maintenanceFrom: string | null
  maintenanceTo: string | null
  changedBy: string | null
  changedAt: string
}

const STATUSES = [
  { value: 'VACANT_CLEAN', label: 'Trống sạch' },
  { value: 'VACANT_DIRTY', label: 'Trống bẩn' },
  { value: 'OCCUPIED', label: 'Đang ở' },
  { value: 'MAINTENANCE', label: 'Bảo trì' },
]

/** S1-07 phòng vật lý, S1-10 trạng thái phòng. */
export default function RoomsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const canManage = can('CATALOG_MANAGE')
  const canStatus = can('ROOM_STATUS_UPDATE')
  const [rows, setRows] = useState<Room[] | null>(null)
  const [types, setTypes] = useState<RoomType[]>([])
  const [filter, setFilter] = useState({ roomTypeId: '', floor: '', status: '' })
  const [form, setForm] = useState<Form | null>(null)
  const [affected, setAffected] = useState<{ message: string; bookings: AffectedBooking[] } | null>(null)
  const [statusFor, setStatusFor] = useState<Room | null>(null)
  const [statusForm, setStatusForm] = useState({ status: 'VACANT_CLEAN', reason: '', maintenanceFrom: todayIso(), maintenanceTo: todayIso(), note: '' })
  const [historyFor, setHistoryFor] = useState<{ room: Room; items: History[] } | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const { data } = await api.get('/rooms', {
      params: { roomTypeId: filter.roomTypeId || undefined, floor: filter.floor || undefined, status: filter.status || undefined },
    })
    setRows(data)
  }, [filter])
  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
  }, [load, toast])
  useEffect(() => {
    if (can('CATALOG_VIEW')) api.get('/room-types').then((r) => setTypes(r.data)).catch(() => {})
  }, [can])

  async function save(e: FormEvent | null, confirm = false) {
    e?.preventDefault()
    if (!form) return
    setError('')
    try {
      if (form.id) await api.put(`/rooms/${form.id}`, form, { params: { confirm } })
      else await api.post('/rooms', form)
      toast('success', 'Đã lưu phòng')
      setForm(null)
      setAffected(null)
      load()
    } catch (err) {
      const data = errorData<{ code?: string; message: string; bookings?: AffectedBooking[] }>(err)
      if (data?.code === 'FUTURE_BOOKINGS' && data.bookings) {
        setAffected({ message: data.message, bookings: data.bookings })
      } else {
        setError(errorMessage(err))
      }
    }
  }

  async function saveStatus(e: FormEvent) {
    e.preventDefault()
    if (!statusFor) return
    setError('')
    try {
      await api.post(`/rooms/${statusFor.id}/status`, {
        status: statusForm.status,
        reason: statusForm.reason || null,
        maintenanceFrom: statusForm.status === 'MAINTENANCE' ? statusForm.maintenanceFrom : null,
        maintenanceTo: statusForm.status === 'MAINTENANCE' ? statusForm.maintenanceTo : null,
        note: statusForm.note || null,
      })
      toast('success', `Đã cập nhật trạng thái phòng ${statusFor.roomNumber}`)
      setStatusFor(null)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function openHistory(r: Room) {
    try {
      const { data } = await api.get(`/rooms/${r.id}/status-history`)
      setHistoryFor({ room: r, items: data })
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  const floors = [...new Set((rows ?? []).map((r) => r.floor))].sort((a, b) => a - b)

  return (
    <>
      <PageHeader
        title="Phòng & trạng thái"
        actions={canManage && (
          <button className="btn btn-brand" disabled={types.length === 0} onClick={() => {
            setError('')
            setForm({ roomNumber: '', floor: 1, roomTypeId: types[0]?.id, note: '', active: true })
          }}>
            <i className="bi bi-plus-lg me-1" /> Thêm phòng
          </button>
        )}
      />
      <div className="row g-2 mb-3">
        <div className="col-12 col-md-4">
          <select className="form-select" value={filter.roomTypeId} onChange={(e) => setFilter({ ...filter, roomTypeId: e.target.value })}>
            <option value="">Tất cả loại phòng</option>
            {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <input type="number" className="form-control" placeholder="Tầng" value={filter.floor} list="floors"
            onChange={(e) => setFilter({ ...filter, floor: e.target.value })} />
          <datalist id="floors">{floors.map((f) => <option key={f} value={f} />)}</datalist>
        </div>
        <div className="col-6 col-md-3">
          <select className="form-select" value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })}>
            <option value="">Tất cả trạng thái</option>
            {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
      </div>
      <div className="card-table">
        {!rows ? <Loading /> : rows.length === 0 ? <Empty text="Không có phòng nào khớp bộ lọc" /> : (
          <table className="table align-middle">
            <thead><tr><th>Số phòng</th><th>Tầng</th><th>Loại phòng</th><th>Trạng thái</th><th>Ghi chú</th><th /></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className={r.active ? '' : 'text-muted'}>
                  <td className="fw-semibold">{r.roomNumber}{!r.active && <span className="badge text-bg-secondary ms-1">Ngừng HĐ</span>}</td>
                  <td>{r.floor}</td>
                  <td>{r.roomTypeName}</td>
                  <td>
                    <StatusBadge status={r.status} label={r.statusLabel} />
                    {r.status === 'MAINTENANCE' && (
                      <div className="small text-muted">{r.maintenanceReason} ({formatDate(r.maintenanceFrom)}–{formatDate(r.maintenanceTo)})</div>
                    )}
                  </td>
                  <td className="small">{r.note}</td>
                  <td className="text-end text-nowrap">
                    {canStatus && (
                      <button className="btn btn-sm btn-outline-primary me-1" onClick={() => {
                        setError('')
                        setStatusForm({ status: r.status === 'VACANT_DIRTY' ? 'VACANT_CLEAN' : 'VACANT_DIRTY', reason: '', maintenanceFrom: todayIso(), maintenanceTo: todayIso(), note: '' })
                        setStatusFor(r)
                      }}>Đổi trạng thái</button>
                    )}
                    <button className="btn btn-sm btn-outline-secondary me-1" onClick={() => openHistory(r)}>Lịch sử</button>
                    {canManage && (
                      <button className="btn btn-sm btn-outline-secondary" onClick={() => {
                        setError('')
                        setForm({ id: r.id, roomNumber: r.roomNumber, floor: r.floor, roomTypeId: r.roomTypeId, note: r.note ?? '', active: r.active })
                      }}>Sửa</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal title={form?.id ? 'Sửa phòng' : 'Thêm phòng'} show={!!form} onClose={() => { setForm(null); setAffected(null) }}
        footer={affected ? (
          <>
            <button className="btn btn-light" onClick={() => setAffected(null)}>Quay lại</button>
            <button className="btn btn-warning" onClick={() => save(null, true)}>Vẫn lưu thay đổi</button>
          </>
        ) : (
          <><button className="btn btn-light" onClick={() => setForm(null)}>Huỷ</button><button className="btn btn-brand" form="room-form">Lưu</button></>
        )}>
        {form && (affected ? (
          <div>
            <div className="alert alert-warning">{affected.message}</div>
            <ul className="list-group">
              {affected.bookings.map((b) => (
                <li key={b.id} className="list-group-item small">
                  <b>{b.code}</b> · {b.customerName} · {formatDate(b.checkInDate)} → {formatDate(b.checkOutDate)} · {b.status}
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <form id="room-form" onSubmit={(e) => save(e)} noValidate>
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <div className="row g-2">
              <div className="col-6"><label className="form-label">Số phòng *</label>
                <input className="form-control" value={form.roomNumber} placeholder="201" onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} /></div>
              <div className="col-6"><label className="form-label">Tầng *</label>
                <input type="number" className="form-control" value={form.floor} onChange={(e) => setForm({ ...form, floor: Number(e.target.value) })} /></div>
              <div className="col-12"><label className="form-label">Loại phòng *</label>
                <select className="form-select" value={form.roomTypeId} onChange={(e) => setForm({ ...form, roomTypeId: Number(e.target.value) })}>
                  {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select></div>
              <div className="col-12"><label className="form-label">Ghi chú</label>
                <textarea className="form-control" rows={2} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></div>
              <div className="col-12 form-check form-switch ms-2">
                <input id="ractive" type="checkbox" className="form-check-input" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                <label htmlFor="ractive" className="form-check-label">Đang hoạt động</label>
              </div>
            </div>
          </form>
        ))}
      </Modal>

      <Modal title={`Đổi trạng thái phòng ${statusFor?.roomNumber ?? ''}`} show={!!statusFor} onClose={() => setStatusFor(null)}
        footer={<><button className="btn btn-light" onClick={() => setStatusFor(null)}>Huỷ</button><button className="btn btn-brand" form="status-form">Lưu</button></>}>
        {statusFor && (
          <form id="status-form" onSubmit={saveStatus} noValidate>
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <p className="small">Trạng thái hiện tại: <StatusBadge status={statusFor.status} label={statusFor.statusLabel} /></p>
            <label className="form-label">Trạng thái mới</label>
            <select className="form-select mb-2" value={statusForm.status} onChange={(e) => setStatusForm({ ...statusForm, status: e.target.value })}>
              {STATUSES.filter((s) => s.value !== 'OCCUPIED').map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
            {statusForm.status === 'MAINTENANCE' ? (
              <>
                <label className="form-label">Lý do bảo trì *</label>
                <input className="form-control mb-2" value={statusForm.reason} placeholder="Hỏng điều hoà" onChange={(e) => setStatusForm({ ...statusForm, reason: e.target.value })} />
                <div className="row g-2">
                  <div className="col-6"><label className="form-label">Từ ngày *</label>
                    <input type="date" className="form-control" value={statusForm.maintenanceFrom} onChange={(e) => setStatusForm({ ...statusForm, maintenanceFrom: e.target.value })} /></div>
                  <div className="col-6"><label className="form-label">Đến ngày *</label>
                    <input type="date" className="form-control" value={statusForm.maintenanceTo} onChange={(e) => setStatusForm({ ...statusForm, maintenanceTo: e.target.value })} /></div>
                </div>
              </>
            ) : (
              <>
                <label className="form-label">Ghi chú</label>
                <input className="form-control" value={statusForm.note} onChange={(e) => setStatusForm({ ...statusForm, note: e.target.value })} />
              </>
            )}
          </form>
        )}
      </Modal>

      <Modal title={`Lịch sử trạng thái phòng ${historyFor?.room.roomNumber ?? ''}`} show={!!historyFor} onClose={() => setHistoryFor(null)} size="lg">
        {historyFor && (historyFor.items.length === 0 ? <Empty text="Chưa có thay đổi nào" /> : (
          <table className="table table-sm">
            <thead><tr><th>Thời điểm</th><th>Từ</th><th>Sang</th><th>Ghi chú</th><th>Người thao tác</th></tr></thead>
            <tbody>
              {historyFor.items.map((h) => (
                <tr key={h.id}>
                  <td className="text-nowrap">{h.changedAt}</td><td>{h.fromStatus}</td><td>{h.toStatus}</td>
                  <td className="small">{h.note}{h.maintenanceFrom && ` (${formatDate(h.maintenanceFrom)}–${formatDate(h.maintenanceTo)})`}</td>
                  <td>{h.changedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
      </Modal>
    </>
  )
}
