import request from './request';
import type {
  Result,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  SendVerificationCodeRequest,
  RefreshTokenRequest,
} from '../types';

export const authApi = {
  login: (data: LoginRequest) =>
    request.post<Result<LoginResponse>>('/auth/login', data),

  register: (data: RegisterRequest) =>
    request.post<Result<null>>('/auth/register', data),

  sendVerificationCode: (data: SendVerificationCodeRequest) =>
    request.post<Result<null>>('/auth/send-verification-code', data),

  refresh: (data: RefreshTokenRequest) =>
    request.post<Result<LoginResponse>>('/auth/refresh', data),

  testAuth: () =>
    request.get<Result<{ userId: number; message: string }>>('/auth/test'),
};
