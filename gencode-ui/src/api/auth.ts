import { get, post } from '@/utils/request'

/** 获取图形验证码 */
export function getCaptcha(): Promise<CaptchaResult> {
  return get<CaptchaResult>('/auth/captcha')
}

/** 登录 */
export function login(body: LoginBody): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', body)
}

/** 当前登录用户信息 */
export function fetchMe(): Promise<LoginUser> {
  return get<LoginUser>('/auth/me')
}

/** 退出登录 */
export function logout(): Promise<null> {
  return post<null>('/auth/logout')
}
