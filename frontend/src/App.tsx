import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { MENU } from './auth/menu'
import RequirePermission from './components/RequirePermission'
import StaffLayout from './components/StaffLayout'
import ChangePasswordPage from './pages/auth/ChangePasswordPage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import LoginPage from './pages/auth/LoginPage'
import ResetPasswordPage from './pages/auth/ResetPasswordPage'
import AmenitiesPage from './pages/admin/AmenitiesPage'
import AuditLogsPage from './pages/admin/AuditLogsPage'
import RoomTypesPage from './pages/admin/RoomTypesPage'
import RoomsPage from './pages/admin/RoomsPage'
import SettingsPage from './pages/admin/SettingsPage'
import UsersPage from './pages/admin/UsersPage'
import { visibleMenu } from './auth/menu'

/** Vào /admin thì chuyển tới mục menu đầu tiên của vai trò. */
function StaffHome() {
  const { can } = useAuth()
  const first = visibleMenu(can)[0]
  return first ? <Navigate to={first.to} replace /> : <div className="p-4 text-muted">Tài khoản chưa được cấp quyền nào.</div>
}

function guard(path: string, element: React.ReactNode) {
  const item = MENU.find((m) => m.to === path)
  return item ? <RequirePermission anyOf={item.anyOf}>{element}</RequirePermission> : element
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/admin" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route path="/admin" element={<StaffLayout />}>
        <Route index element={<StaffHome />} />
        <Route path="change-password" element={<ChangePasswordPage />} />
        <Route path="users" element={guard('/admin/users', <UsersPage />)} />
        <Route path="audit-logs" element={guard('/admin/audit-logs', <AuditLogsPage />)} />
        <Route path="room-types" element={guard('/admin/room-types', <RoomTypesPage />)} />
        <Route path="amenities" element={guard('/admin/amenities', <AmenitiesPage />)} />
        <Route path="rooms" element={guard('/admin/rooms', <RoomsPage />)} />
        <Route path="settings" element={guard('/admin/settings', <SettingsPage />)} />
        <Route path="*" element={<div className="p-4 text-muted">Không tìm thấy trang.</div>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
