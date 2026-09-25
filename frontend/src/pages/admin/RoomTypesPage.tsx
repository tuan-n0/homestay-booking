import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Empty, Loading, Modal, PageHeader } from '../../components/ui'
import { formatVnd } from '../../utils/format'
import type { Amenity } from './AmenitiesPage'

export interface RoomType {
  id: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  bedCount: number
  description: string | null
  active: boolean
  weekdayPrice: number | null
  weekendPrice: number | null
  roomCount: number
  amenities: Amenity[]
}
interface Form {
  id?: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  bedCount: number
  description: string
}
const emptyForm: Form = { code: '', name: '', standardCapacity: 2, maxCapacity: 2, bedCount: 1, description: '' }

/** S1-06 loại phòng, S1-08 gắn tiện nghi. */
export default function RoomTypesPage() {
  const { can } = useAuth()
  const toast = useToast()
  const canManage = can('CATALOG_MANAGE')
  const [rows, setRows] = useState<RoomType[] | null>(null)
  const [allAmenities, setAllAmenities] = useState<Amenity[]>([])
  const [form, setForm] = useState<Form | null>(null)
  const [amenityFor, setAmenityFor] = useState<RoomType | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const { data } = await api.get<RoomType[]>('/room-types')
    setRows(data)
    return data
  }, [])
  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
    api.get('/amenities').then((r) => setAllAmenities(r.data)).catch(() => {})
  }, [load, toast])

  function capacityProblem(f: Form) {
    return f.maxCapacity < f.standardCapacity
      ? `Sức chứa tối đa (${f.maxCapacity}) không được nhỏ hơn sức chứa tiêu chuẩn (${f.standardCapacity})`
      : ''
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const p = capacityProblem(form)
    if (p) return setError(p)
    setError('')
    try {
      if (form.id) await api.put(`/room-types/${form.id}`, form)
      else await api.post('/room-types', form)
      toast('success', 'Đã lưu loại phòng')
      setForm(null)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function toggle(t: RoomType) {
    try {
      await api.patch(`/room-types/${t.id}/active`, null, { params: { value: !t.active } })
      load()
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  async function remove(t: RoomType) {
    if (!confirm(`Xoá loại phòng "${t.name}"?`)) return
    try {
      await api.delete(`/room-types/${t.id}`)
      toast('success', 'Đã xoá loại phòng')
      load()
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  async function attach(t: RoomType, amenityId: number, on: boolean) {
    try {
      const { data } = on
        ? await api.post(`/room-types/${t.id}/amenities/${amenityId}`)
        : await api.delete(`/room-types/${t.id}/amenities/${amenityId}`)
      setAmenityFor(data)
      load()
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Loại phòng"
        actions={canManage && (
          <button className="btn btn-brand" onClick={() => { setError(''); setForm({ ...emptyForm }) }}>
            <i className="bi bi-plus-lg me-1" /> Thêm loại phòng
          </button>
        )}
      />
      {!rows ? <Loading /> : rows.length === 0 ? <Empty text="Chưa có loại phòng nào" /> : (
        <div className="row g-3">
          {rows.map((t) => (
            <div key={t.id} className="col-12 col-md-6 col-xl-4">
              <div className={`card h-100 ${t.active ? '' : 'opacity-75'}`}>
                <div className="card-body">
                  <div className="d-flex align-items-start">
                    <div className="me-auto">
                      <h2 className="h6 mb-0">{t.name}</h2>
                      <code className="small">{t.code}</code>
                    </div>
                    {t.active ? <span className="badge text-bg-success">Đang bán</span> : <span className="badge text-bg-secondary">Ngừng bán</span>}
                  </div>
                  <div className="small text-muted mt-2">
                    <i className="bi bi-people me-1" />{t.standardCapacity}–{t.maxCapacity} người ·
                    <i className="bi bi-hospital ms-2 me-1" />{t.bedCount} giường ·
                    <i className="bi bi-door-closed ms-2 me-1" />{t.roomCount} phòng
                  </div>
                  <div className="small mt-1">
                    Ngày thường: <b>{formatVnd(t.weekdayPrice)}</b> · Cuối tuần: <b>{formatVnd(t.weekendPrice)}</b>
                  </div>
                  {t.description && <p className="small mt-2 mb-2">{t.description}</p>}
                  <div className="d-flex flex-wrap gap-1 mt-2">
                    {t.amenities.map((a) => (
                      <span key={a.id} className="badge rounded-pill text-bg-light border">
                        <i className={`bi bi-${a.icon ?? 'check2'} me-1`} />{a.name}
                      </span>
                    ))}
                  </div>
                </div>
                {canManage && (
                  <div className="card-footer bg-white d-flex flex-wrap gap-1">
                    <button className="btn btn-sm btn-outline-secondary" onClick={() => {
                      setError('')
                      setForm({ id: t.id, code: t.code, name: t.name, standardCapacity: t.standardCapacity, maxCapacity: t.maxCapacity, bedCount: t.bedCount, description: t.description ?? '' })
                    }}>Sửa</button>
                    <button className="btn btn-sm btn-outline-secondary" onClick={() => setAmenityFor(t)}>Tiện nghi</button>
                    <button className="btn btn-sm btn-outline-warning" onClick={() => toggle(t)}>{t.active ? 'Ngừng bán' : 'Bán lại'}</button>
                    <button className="btn btn-sm btn-outline-danger ms-auto" onClick={() => remove(t)}>Xoá</button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal title={form?.id ? 'Sửa loại phòng' : 'Thêm loại phòng'} show={!!form} onClose={() => setForm(null)}
        footer={<><button className="btn btn-light" onClick={() => setForm(null)}>Huỷ</button><button className="btn btn-brand" form="rt-form">Lưu</button></>}>
        {form && (
          <form id="rt-form" onSubmit={save} noValidate>
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <div className="row g-2">
              <div className="col-4"><label className="form-label">Mã *</label>
                <input className="form-control" value={form.code} placeholder="DBL" onChange={(e) => setForm({ ...form, code: e.target.value })} /></div>
              <div className="col-8"><label className="form-label">Tên *</label>
                <input className="form-control" value={form.name} placeholder="Phòng đôi" onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
              <div className="col-4"><label className="form-label">Sức chứa tiêu chuẩn *</label>
                <input type="number" min={1} className="form-control" value={form.standardCapacity} onChange={(e) => setForm({ ...form, standardCapacity: Number(e.target.value) })} /></div>
              <div className="col-4"><label className="form-label">Sức chứa tối đa *</label>
                <input type="number" min={1} className={`form-control ${capacityProblem(form) ? 'is-invalid' : ''}`} value={form.maxCapacity} onChange={(e) => setForm({ ...form, maxCapacity: Number(e.target.value) })} /></div>
              <div className="col-4"><label className="form-label">Số giường *</label>
                <input type="number" min={1} className="form-control" value={form.bedCount} onChange={(e) => setForm({ ...form, bedCount: Number(e.target.value) })} /></div>
              {capacityProblem(form) && <div className="col-12 small text-danger">{capacityProblem(form)}</div>}
              <div className="col-12"><label className="form-label">Mô tả</label>
                <textarea className="form-control" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></div>
            </div>
          </form>
        )}
      </Modal>

      <Modal title={`Tiện nghi — ${amenityFor?.name ?? ''}`} show={!!amenityFor} onClose={() => setAmenityFor(null)}>
        {amenityFor && (
          <div className="list-group">
            {allAmenities.filter((a) => a.active).map((a) => {
              const on = amenityFor.amenities.some((x) => x.id === a.id)
              return (
                <label key={a.id} className="list-group-item d-flex align-items-center gap-2">
                  <input type="checkbox" className="form-check-input m-0" checked={on} onChange={() => attach(amenityFor, a.id, !on)} />
                  <i className={`bi bi-${a.icon ?? 'check2'}`} /> {a.name}
                </label>
              )
            })}
            {allAmenities.filter((a) => a.active).length === 0 && <Empty text="Chưa có tiện nghi đang dùng" />}
          </div>
        )}
      </Modal>
    </>
  )
}
