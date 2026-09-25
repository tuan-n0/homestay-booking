import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'

/** Chặn vào thẳng đường dẫn không thuộc quyền (máy chủ vẫn trả 403 cho API). */
export default function RequirePermission({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const { can } = useAuth()
  if (!can(...anyOf)) {
    return (
      <div className="alert alert-warning m-4">
        <i className="bi bi-shield-lock me-2" />
        Bạn không có quyền truy cập màn hình này (403).
      </div>
    )
  }
  return <>{children}</>
}
