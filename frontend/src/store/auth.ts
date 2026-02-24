import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '../types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userInfo: UserInfo | null;
  isAuthenticated: boolean;

  setTokens: (access: string, refresh: string) => void;
  setUserInfo: (info: UserInfo) => void;
  login: (access: string, refresh: string, info: UserInfo) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userInfo: null,
      isAuthenticated: false,

      setTokens: (access, refresh) =>
        set({ accessToken: access, refreshToken: refresh }),

      setUserInfo: (info) =>
        set({ userInfo: info }),

      login: (access, refresh, info) =>
        set({
          accessToken: access,
          refreshToken: refresh,
          userInfo: info,
          isAuthenticated: true,
        }),

      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userInfo: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'smartats-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        userInfo: state.userInfo,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
