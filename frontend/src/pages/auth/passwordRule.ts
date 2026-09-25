/** S1-03: tối thiểu 8 ký tự, có cả chữ và số (khớp với kiểm tra phía máy chủ). */
export function passwordProblem(pw: string, confirm: string): string | null {
  if (pw.length < 8) return 'Mật khẩu mới tối thiểu 8 ký tự'
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return 'Mật khẩu mới phải gồm cả chữ và số'
  if (pw !== confirm) return 'Nhập lại mật khẩu không khớp'
  return null
}
