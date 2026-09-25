// Keep aligned with CreateUserRequest; displayName accepts Chinese, username does not.
export const USERNAME_RULE = /^[A-Za-z0-9_.-]{3,64}$/
export const USERNAME_HINT = '用户名须为 3–64 位英文字母、数字、下划线、点或短横线；中文姓名请填在“姓名”中'

export function fieldErrors(details: unknown): Record<string, string> {
  if (!details || typeof details !== 'object' || Array.isArray(details)) return {}
  return Object.fromEntries(Object.entries(details).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
}
