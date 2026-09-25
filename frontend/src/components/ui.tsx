import { useEffect, type ReactNode } from 'react'

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
      <div className="me-auto">
        <h1 className="h4 mb-0">{title}</h1>
        {subtitle && <div className="text-muted small">{subtitle}</div>}
      </div>
      {actions}
    </div>
  )
}

export function Modal({
  title,
  show,
  onClose,
  children,
  footer,
  size,
}: {
  title: string
  show: boolean
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'lg' | 'xl'
}) {
  useEffect(() => {
    if (!show) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [show, onClose])
  if (!show) return null
  return (
    <>
      <div className="modal d-block" tabIndex={-1} role="dialog" aria-modal="true">
        <div className={`modal-dialog modal-dialog-scrollable ${size ? `modal-${size}` : ''}`}>
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{title}</h5>
              <button type="button" className="btn-close" aria-label="Đóng" onClick={onClose} />
            </div>
            <div className="modal-body">{children}</div>
            {footer && <div className="modal-footer">{footer}</div>}
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" onClick={onClose} />
    </>
  )
}

export function Loading() {
  return (
    <div className="text-center text-muted py-4">
      <div className="spinner-border spinner-border-sm me-2" /> Đang tải…
    </div>
  )
}

export function Empty({ text }: { text: string }) {
  return (
    <div className="text-center text-muted py-5">
      <i className="bi bi-inbox fs-2 d-block mb-2" />
      {text}
    </div>
  )
}

export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="d-flex align-items-center gap-2 justify-content-end mt-2">
      <button className="btn btn-sm btn-outline-secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        ‹ Trước
      </button>
      <span className="small text-muted">
        Trang {page + 1}/{totalPages}
      </span>
      <button
        className="btn btn-sm btn-outline-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Sau ›
      </button>
    </div>
  )
}

export function StatusBadge({ status, label }: { status: string; label: string }) {
  const color: Record<string, string> = {
    VACANT_CLEAN: 'success',
    VACANT_DIRTY: 'warning',
    OCCUPIED: 'primary',
    MAINTENANCE: 'danger',
    PENDING: 'warning',
    CONFIRMED: 'info',
    CHECKED_IN: 'primary',
    CLOSED: 'secondary',
    CANCELLED: 'dark',
    EXPIRED: 'dark',
  }
  return <span className={`badge text-bg-${color[status] ?? 'secondary'}`}>{label}</span>
}
