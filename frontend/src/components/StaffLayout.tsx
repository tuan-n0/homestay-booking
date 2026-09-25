import { useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { visibleMenu } from '../auth/menu'

export default function StaffLayout() {
  const { user, loading, logout, can } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  if (loading) return <div className="p-5 text-center text-muted">Đang tải…</div>
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (user.mustChangePassword && location.pathname !== '/admin/change-password') {
    return <Navigate to="/admin/change-password" replace />
  }

  const items = visibleMenu(can)
  const groups = [...new Set(items.map((i) => i.group))]

  const nav = (
    <nav className="nav flex-column">
      {groups.map((g) => (
        <div key={g} className="mb-2">
          <div className="sidebar-group">{g}</div>
          {items
            .filter((i) => i.group === g)
            .map((i) => (
              <NavLink key={i.to} to={i.to} className="nav-link sidebar-link" onClick={() => setOpen(false)}>
                <i className={`bi ${i.icon} me-2`} />
                {i.label}
              </NavLink>
            ))}
        </div>
      ))}
    </nav>
  )

  return (
    <div className="staff-shell">
      <header className="staff-topbar d-flex align-items-center px-3">
        <button className="btn btn-link text-white d-lg-none me-2 p-0" aria-label="Mở menu" onClick={() => setOpen(!open)}>
          <i className="bi bi-list fs-3" />
        </button>
        <Link to="/admin" className="text-white text-decoration-none fw-semibold">
          <i className="bi bi-house-heart me-2" />
          Homestay
        </Link>
        <div className="ms-auto dropdown">
          <details className="user-menu">
            <summary className="text-white">
              <i className="bi bi-person-circle me-1" />
              <span className="d-none d-sm-inline">{user.fullName}</span>
            </summary>
            <div className="user-menu-panel shadow">
              <div className="px-3 py-2 small text-muted">
                {user.email}
                <br />
                {user.roleName}
              </div>
              <Link className="dropdown-item" to="/admin/change-password">
                Đổi mật khẩu
              </Link>
              <button
                className="dropdown-item text-danger"
                onClick={async () => {
                  await logout()
                  navigate('/login')
                }}
              >
                Đăng xuất
              </button>
            </div>
          </details>
        </div>
      </header>
      <div className="d-flex">
        <aside className={`staff-sidebar ${open ? 'open' : ''}`}>{nav}</aside>
        {open && <div className="sidebar-backdrop d-lg-none" onClick={() => setOpen(false)} />}
        <main className="staff-main flex-grow-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
