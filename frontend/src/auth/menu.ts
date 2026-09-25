/**
 * S1-04: menu sinh theo quyền — nơi duy nhất khai báo mục menu và quyền cần có.
 * Máy chủ vẫn kiểm tra quyền ở từng API (trả 403), menu chỉ để người dùng không vào nhầm.
 */
export interface MenuItem {
  to: string
  label: string
  icon: string
  anyOf: string[]
  group: string
}

export const MENU: MenuItem[] = [
  { group: 'Vận hành', to: '/admin/housekeeping', label: 'Phòng cần dọn', icon: 'bi-brush', anyOf: ['HOUSEKEEPING'] },
  { group: 'Vận hành', to: '/admin/rooms', label: 'Phòng & trạng thái', icon: 'bi-door-open', anyOf: ['ROOM_STATUS_VIEW', 'CATALOG_VIEW'] },

  { group: 'Danh mục', to: '/admin/room-types', label: 'Loại phòng', icon: 'bi-grid', anyOf: ['CATALOG_VIEW'] },
  { group: 'Danh mục', to: '/admin/amenities', label: 'Tiện nghi', icon: 'bi-stars', anyOf: ['CATALOG_VIEW'] },
  { group: 'Danh mục', to: '/admin/settings', label: 'Tham số vận hành', icon: 'bi-sliders', anyOf: ['SETTINGS_MANAGE', 'PRICING_VIEW'] },

  { group: 'Quản trị', to: '/admin/users', label: 'Tài khoản', icon: 'bi-people', anyOf: ['USER_VIEW'] },
  { group: 'Quản trị', to: '/admin/audit-logs', label: 'Nhật ký truy cập', icon: 'bi-journal-text', anyOf: ['AUDIT_VIEW'] },
]

export function visibleMenu(can: (...p: string[]) => boolean): MenuItem[] {
  return MENU.filter((m) => can(...m.anyOf))
}
