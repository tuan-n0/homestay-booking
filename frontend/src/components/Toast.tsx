import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type Kind = 'success' | 'danger' | 'warning' | 'info'
interface ToastItem {
  id: number
  kind: Kind
  text: string
}

const ToastContext = createContext<(kind: Kind, text: string) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const push = useCallback((kind: Kind, text: string) => {
    const id = Date.now() + Math.random()
    setItems((list) => [...list, { id, kind, text }])
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 5000)
  }, [])
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast-container position-fixed top-0 end-0 p-3" style={{ zIndex: 2000 }}>
        {items.map((t) => (
          <div key={t.id} className={`toast show align-items-center text-bg-${t.kind} border-0 mb-2`} role="alert">
            <div className="d-flex">
              <div className="toast-body">{t.text}</div>
              <button
                type="button"
                className="btn-close btn-close-white me-2 m-auto"
                aria-label="Đóng"
                onClick={() => setItems((list) => list.filter((x) => x.id !== t.id))}
              />
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  return useContext(ToastContext)
}
