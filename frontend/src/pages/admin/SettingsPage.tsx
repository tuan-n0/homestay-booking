import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Loading, PageHeader } from '../../components/ui'
import { formatVnd } from '../../utils/format'

interface Tier {
  hoursBefore: number
  refundPercent: number
}
interface Policy {
  id: number
  checkInTime: string
  checkOutTime: string
  lateCheckoutFeePerHour: number
  extraPersonFee: number
  extraBedFee: number
  lateCheckoutFullNightFrom: string
  weekendDays: number[]
  tiers: Tier[]
  effectiveFrom: string
  createdBy: string | null
}
interface Settings {
  homestay: { name: string; address: string | null; phone: string | null; email: string | null }
  current: Policy
  history: Policy[]
}

const DAYS = ['', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật']
const hhmm = (t: string) => t.slice(0, 5)

/** Kiểm tra giống máy chủ: tối đa 3 mốc, giảm dần, không trùng. */
function tierProblem(tiers: Tier[]): string {
  if (tiers.length > 3) return 'Chỉ được khai báo tối đa 3 mốc'
  const sorted = [...tiers].sort((a, b) => b.hoursBefore - a.hoursBefore)
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i].hoursBefore === sorted[i - 1].hoursBefore) return `Hai mốc bị trùng ở ${sorted[i].hoursBefore} giờ`
    if (sorted[i].refundPercent >= sorted[i - 1].refundPercent)
      return `Mốc ${sorted[i].hoursBefore} giờ (${sorted[i].refundPercent}%) phải hoàn ít hơn mốc ${sorted[i - 1].hoursBefore} giờ (${sorted[i - 1].refundPercent}%)`
  }
  return ''
}

/** S1-09: thông tin homestay và tham số vận hành có lịch sử phiên bản. */
export default function SettingsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const canManage = can('SETTINGS_MANAGE')
  const [data, setData] = useState<Settings | null>(null)
  const [home, setHome] = useState({ name: '', address: '', phone: '', email: '' })
  const [policy, setPolicy] = useState<Omit<Policy, 'id' | 'effectiveFrom' | 'createdBy'> | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const { data } = await api.get<Settings>('/settings')
    setData(data)
    setHome({ name: data.homestay.name, address: data.homestay.address ?? '', phone: data.homestay.phone ?? '', email: data.homestay.email ?? '' })
    const c = data.current
    setPolicy({
      checkInTime: hhmm(c.checkInTime), checkOutTime: hhmm(c.checkOutTime),
      lateCheckoutFeePerHour: c.lateCheckoutFeePerHour, extraPersonFee: c.extraPersonFee, extraBedFee: c.extraBedFee,
      lateCheckoutFullNightFrom: hhmm(c.lateCheckoutFullNightFrom), weekendDays: c.weekendDays, tiers: c.tiers,
    })
  }, [])
  useEffect(() => {
    load().catch((e) => toast('danger', errorMessage(e)))
  }, [load, toast])

  async function saveHome(e: FormEvent) {
    e.preventDefault()
    try {
      await api.put('/settings/homestay', home)
      toast('success', 'Đã lưu thông tin homestay')
    } catch (err) {
      toast('danger', errorMessage(err))
    }
  }

  async function savePolicy(e: FormEvent) {
    e.preventDefault()
    if (!policy) return
    const p = tierProblem(policy.tiers)
    if (p) return setError(p)
    setError('')
    try {
      await api.post('/settings/policies', policy)
      toast('success', 'Đã tạo phiên bản tham số mới, áp dụng cho các booking tạo từ bây giờ')
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (!data || !policy) return <Loading />
  const setTier = (i: number, t: Partial<Tier>) =>
    setPolicy({ ...policy, tiers: policy.tiers.map((x, j) => (i === j ? { ...x, ...t } : x)) })

  return (
    <>
      <PageHeader title="Thông tin homestay & tham số vận hành" />
      <div className="row g-3">
        <div className="col-12 col-xl-5">
          <form className="card" onSubmit={saveHome}>
            <div className="card-header bg-white fw-semibold">Thông tin homestay</div>
            <fieldset className="card-body" disabled={!canManage}>
              <label className="form-label">Tên homestay *</label>
              <input className="form-control mb-2" value={home.name} onChange={(e) => setHome({ ...home, name: e.target.value })} />
              <label className="form-label">Địa chỉ</label>
              <input className="form-control mb-2" value={home.address} onChange={(e) => setHome({ ...home, address: e.target.value })} />
              <div className="row g-2">
                <div className="col-6"><label className="form-label">Điện thoại</label>
                  <input className="form-control" value={home.phone} onChange={(e) => setHome({ ...home, phone: e.target.value })} /></div>
                <div className="col-6"><label className="form-label">Email</label>
                  <input className="form-control" value={home.email} onChange={(e) => setHome({ ...home, email: e.target.value })} /></div>
              </div>
              {canManage && <button className="btn btn-brand mt-3">Lưu thông tin</button>}
            </fieldset>
          </form>
        </div>

        <div className="col-12 col-xl-7">
          <form className="card" onSubmit={savePolicy}>
            <div className="card-header bg-white fw-semibold">
              Tham số vận hành <span className="text-muted small fw-normal">— đang áp dụng từ {data.current.effectiveFrom}</span>
            </div>
            <fieldset className="card-body" disabled={!canManage}>
              {error && <div className="alert alert-danger py-2">{error}</div>}
              <div className="row g-2">
                <div className="col-6 col-md-3"><label className="form-label">Giờ nhận phòng</label>
                  <input type="time" className="form-control" value={policy.checkInTime} onChange={(e) => setPolicy({ ...policy, checkInTime: e.target.value })} /></div>
                <div className="col-6 col-md-3"><label className="form-label">Giờ trả phòng</label>
                  <input type="time" className="form-control" value={policy.checkOutTime} onChange={(e) => setPolicy({ ...policy, checkOutTime: e.target.value })} /></div>
                <div className="col-6 col-md-3"><label className="form-label">Phụ thu trả muộn/giờ</label>
                  <input type="number" min={0} step={1000} className="form-control" value={policy.lateCheckoutFeePerHour} onChange={(e) => setPolicy({ ...policy, lateCheckoutFeePerHour: Number(e.target.value) })} /></div>
                <div className="col-6 col-md-3"><label className="form-label">Quá giờ này tính 1 đêm</label>
                  <input type="time" className="form-control" value={policy.lateCheckoutFullNightFrom} onChange={(e) => setPolicy({ ...policy, lateCheckoutFullNightFrom: e.target.value })} /></div>
                <div className="col-6 col-md-3"><label className="form-label">Phụ thu thêm người/đêm</label>
                  <input type="number" min={0} step={1000} className="form-control" value={policy.extraPersonFee} onChange={(e) => setPolicy({ ...policy, extraPersonFee: Number(e.target.value) })} /></div>
                <div className="col-6 col-md-3"><label className="form-label">Phụ thu thêm giường/đêm</label>
                  <input type="number" min={0} step={1000} className="form-control" value={policy.extraBedFee} onChange={(e) => setPolicy({ ...policy, extraBedFee: Number(e.target.value) })} /></div>
                <div className="col-12 col-md-6"><label className="form-label">Đêm tính giá cuối tuần</label>
                  <div className="d-flex flex-wrap gap-2">
                    {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                      <label key={d} className="form-check-label small">
                        <input type="checkbox" className="form-check-input me-1" checked={policy.weekendDays.includes(d)}
                          onChange={(e) => setPolicy({ ...policy, weekendDays: e.target.checked ? [...policy.weekendDays, d] : policy.weekendDays.filter((x) => x !== d) })} />
                        {DAYS[d]}
                      </label>
                    ))}
                  </div></div>
              </div>

              <div className="mt-3 fw-semibold">Chính sách huỷ (tối đa 3 mốc)</div>
              <div className="small text-muted mb-2">Huỷ trước giờ nhận phòng ít nhất N giờ thì được hoàn X% tiền cọc. Mốc càng gần thì hoàn càng ít.</div>
              {policy.tiers.map((t, i) => (
                <div key={i} className="d-flex gap-2 align-items-center mb-2">
                  <span className="small">Trước</span>
                  <input type="number" min={1} className="form-control form-control-sm" style={{ width: 90 }} value={t.hoursBefore} onChange={(e) => setTier(i, { hoursBefore: Number(e.target.value) })} />
                  <span className="small">giờ → hoàn</span>
                  <input type="number" min={0} max={100} className="form-control form-control-sm" style={{ width: 80 }} value={t.refundPercent} onChange={(e) => setTier(i, { refundPercent: Number(e.target.value) })} />
                  <span className="small">%</span>
                  {canManage && <button type="button" className="btn btn-sm btn-link text-danger" onClick={() => setPolicy({ ...policy, tiers: policy.tiers.filter((_, j) => j !== i) })}>Xoá</button>}
                </div>
              ))}
              {tierProblem(policy.tiers) && <div className="small text-danger">{tierProblem(policy.tiers)}</div>}
              {canManage && policy.tiers.length < 3 && (
                <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setPolicy({ ...policy, tiers: [...policy.tiers, { hoursBefore: 12, refundPercent: 0 }] })}>+ Thêm mốc</button>
              )}
              {canManage && (
                <div className="mt-3">
                  <button className="btn btn-brand">Lưu thành phiên bản mới</button>
                  <div className="form-text">Thay đổi chỉ áp dụng cho các booking tạo sau thời điểm lưu.</div>
                </div>
              )}
            </fieldset>
          </form>
        </div>

        <div className="col-12">
          <div className="card-table">
            <div className="p-2 fw-semibold">Lịch sử thay đổi tham số</div>
            <table className="table table-sm align-middle">
              <thead><tr><th>Áp dụng từ</th><th>Người sửa</th><th>Nhận / trả</th><th>Trả muộn</th><th>Thêm người</th><th>Chính sách huỷ</th></tr></thead>
              <tbody>
                {data.history.map((h) => (
                  <tr key={h.id}>
                    <td className="text-nowrap">{h.effectiveFrom}</td>
                    <td>{h.createdBy}</td>
                    <td>{hhmm(h.checkInTime)} / {hhmm(h.checkOutTime)}</td>
                    <td>{formatVnd(h.lateCheckoutFeePerHour)}/giờ</td>
                    <td>{formatVnd(h.extraPersonFee)}</td>
                    <td className="small">{h.tiers.map((t) => `≥${t.hoursBefore}h: ${t.refundPercent}%`).join(' · ') || 'Không hoàn'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </>
  )
}
