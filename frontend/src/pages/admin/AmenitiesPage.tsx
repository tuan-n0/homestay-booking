import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Empty, Loading, Modal, PageHeader } from '../../components/ui'

export interface Amenity {
  id: number
  code: string
  name: string
  icon: string | null
  active: boolean
  usageCount: number
}
interface Form {
  id?: number
  code: string
  name: string
  icon: string
}

/** S1-08: danh mục tiện nghi — thêm, sửa, ngừng dùng; đang gắn thì không xoá được. */
export default function AmenitiesPage() {
  const { can } = useAuth()
  const toast = useToast()
  const canManage = can('CATALOG_MANAGE')
  const [rows, setRows] = useState<Amenity[] | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const { data } = await api.get('/amenities')
    setRows(data)
  }, [])
  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
  }, [load, toast])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    setError('')
    try {
      if (form.id) await api.put(`/amenities/${form.id}`, form)
      else await api.post('/amenities', form)
      toast('success', 'Đã lưu tiện nghi')
      setForm(null)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function toggle(a: Amenity) {
    try {
      await api.patch(`/amenities/${a.id}/active`, null, { params: { value: !a.active } })
      load()
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  async function remove(a: Amenity) {
    if (!confirm(`Xoá tiện nghi "${a.name}"?`)) return
    try {
      await api.delete(`/amenities/${a.id}`)
      toast('success', 'Đã xoá tiện nghi')
      load()
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Tiện nghi"
        subtitle="Biểu tượng dùng tên của Bootstrap Icons, ví dụ: snowflake, wifi, tv, cup-hot"
        actions={canManage && (
          <button className="btn btn-brand" onClick={() => { setError(''); setForm({ code: '', name: '', icon: '' }) }}>
            <i className="bi bi-plus-lg me-1" /> Thêm tiện nghi
          </button>
        )}
      />
      <div className="card-table">
        {!rows ? <Loading /> : rows.length === 0 ? <Empty text="Chưa có tiện nghi nào" /> : (
          <table className="table align-middle">
            <thead><tr><th>Biểu tượng</th><th>Mã</th><th>Tên</th><th>Đang gắn</th><th>Trạng thái</th><th /></tr></thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id} className={a.active ? '' : 'text-muted'}>
                  <td><i className={`bi bi-${a.icon ?? 'check2-circle'} fs-5`} /></td>
                  <td><code>{a.code}</code></td>
                  <td>{a.name}</td>
                  <td>{a.usageCount} loại phòng</td>
                  <td>{a.active ? <span className="badge text-bg-success">Đang dùng</span> : <span className="badge text-bg-secondary">Ngừng dùng</span>}</td>
                  <td className="text-end text-nowrap">
                    {canManage && (
                      <>
                        <button className="btn btn-sm btn-outline-secondary me-1" onClick={() => { setError(''); setForm({ id: a.id, code: a.code, name: a.name, icon: a.icon ?? '' }) }}>Sửa</button>
                        <button className="btn btn-sm btn-outline-warning me-1" onClick={() => toggle(a)}>{a.active ? 'Ngừng dùng' : 'Dùng lại'}</button>
                        <button className="btn btn-sm btn-outline-danger" onClick={() => remove(a)}>Xoá</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <Modal title={form?.id ? 'Sửa tiện nghi' : 'Thêm tiện nghi'} show={!!form} onClose={() => setForm(null)}
        footer={<><button className="btn btn-light" onClick={() => setForm(null)}>Huỷ</button><button className="btn btn-brand" form="am-form">Lưu</button></>}>
        {form && (
          <form id="am-form" onSubmit={save} noValidate>
            {error && <div className="alert alert-danger py-2">{error}</div>}
            <div className="mb-2"><label className="form-label">Mã *</label>
              <input className="form-control" value={form.code} placeholder="AIR_CON" onChange={(e) => setForm({ ...form, code: e.target.value })} /></div>
            <div className="mb-2"><label className="form-label">Tên *</label>
              <input className="form-control" value={form.name} placeholder="Điều hoà" onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
            <div className="mb-2"><label className="form-label">Biểu tượng</label>
              <div className="input-group">
                <span className="input-group-text"><i className={`bi bi-${form.icon || 'question'}`} /></span>
                <input className="form-control" value={form.icon} placeholder="snowflake" onChange={(e) => setForm({ ...form, icon: e.target.value })} />
              </div></div>
          </form>
        )}
      </Modal>
    </>
  )
}
