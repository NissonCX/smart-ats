import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import themeConfig from './styles/theme';
import './styles/global.css';

import AuthGuard from './components/AuthGuard';
import AppLayout from './components/AppLayout';

// 懒加载页面
const LoginPage = lazy(() => import('./pages/login'));
const DashboardPage = lazy(() => import('./pages/dashboard'));
const JobsPage = lazy(() => import('./pages/jobs'));
const ResumesPage = lazy(() => import('./pages/resumes'));
const CandidatesPage = lazy(() => import('./pages/candidates'));
const SmartSearchPage = lazy(() => import('./pages/candidates/search'));
const ApplicationsPage = lazy(() => import('./pages/applications'));
const InterviewsPage = lazy(() => import('./pages/interviews'));
const SettingsPage = lazy(() => import('./pages/settings'));
const AuditLogPage = lazy(() => import('./pages/audit'));
const AnalyticsPage = lazy(() => import('./pages/analytics'));

const PageLoading = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
    <Spin size="large" />
  </div>
);

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <BrowserRouter>
        <Suspense fallback={<PageLoading />}>
          <Routes>
            {/* 公开路由 */}
            <Route path="/login" element={<LoginPage />} />

            {/* 需要认证的路由 */}
            <Route element={<AuthGuard><AppLayout /></AuthGuard>}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/jobs" element={<JobsPage />} />
              <Route path="/resumes" element={<ResumesPage />} />
              <Route path="/candidates" element={<CandidatesPage />} />
              <Route path="/candidates/search" element={<SmartSearchPage />} />
              <Route path="/applications" element={<ApplicationsPage />} />
              <Route path="/interviews" element={<InterviewsPage />} />
              <Route path="/audit-logs" element={<AuditLogPage />} />
              <Route path="/analytics" element={<AnalyticsPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>

            {/* 默认重定向 */}
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </ConfigProvider>
  );
}
