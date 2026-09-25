const vnd = new Intl.NumberFormat('vi-VN')

/** Tiền VND có dấu phân cách nghìn, không phần thập phân: 1.250.000 ₫ */
export function formatVnd(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${vnd.format(Math.round(value))} ₫`
}

/** yyyy-MM-dd → dd/MM/yyyy */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${y}`
}

/** Ngày hôm nay theo giờ Việt Nam, dạng yyyy-MM-dd */
export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
}

export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function nightsBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)
}
