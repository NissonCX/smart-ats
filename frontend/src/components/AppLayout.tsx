import { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Space, Badge } from 'antd';
import {
  DashboardOutlined,
  FileTextOutlined,
  TeamOutlined,
  SolutionOutlined,
  CalendarOutlined,
  CloudUploadOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  SearchOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  AuditOutlined,
  FundOutlined,
} from '@ant-design/icons';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '../store/auth';
import type { MenuProps } from 'antd';

const { Sider, Header, Content } = Layout;

// Z-index management scale
const Z_INDEX = {
  sidebar: 50,
  header: 40,
  overlay: 45,
} as const;

// Check for reduced motion preference
const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const menuItems: MenuProps['items'] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: '工作台',
  },
  {
    key: '/jobs',
    icon: <FileTextOutlined />,
    label: '职位管理',
  },
  {
    key: '/resumes',
    icon: <CloudUploadOutlined />,
    label: '简历管理',
  },
  {
    key: 'candidates-group',
    icon: <TeamOutlined />,
    label: '候选人',
    children: [
      { key: '/candidates', label: '候选人列表' },
      { key: '/candidates/search', icon: <SearchOutlined />, label: '智能搜索' },
    ],
  },
  {
    key: '/applications',
    icon: <SolutionOutlined />,
    label: '职位申请',
  },
  {
    key: '/interviews',
    icon: <CalendarOutlined />,
    label: '面试管理',
  },
  {
    key: '/analytics',
    icon: <FundOutlined />,
    label: '招聘分析',
  },
  {
    key: '/audit-logs',
    icon: <AuditOutlined />,
    label: '审计日志',
  },
  {
    key: '/settings',
    icon: <SettingOutlined />,
    label: '系统设置',
  },
];

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, logout } = useAuthStore();

  // Detect mobile screen
  useEffect(() => {
    const checkMobile = () => setIsMobile(window.innerWidth < 1024);
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Auto-collapse sidebar on mobile
  useEffect(() => {
    if (isMobile) {
      setCollapsed(true);
    }
  }, [isMobile]);

  // Close mobile menu when route changes
  useEffect(() => {
    if (isMobile) {
      setMobileMenuOpen(false);
    }
  }, [location.pathname, isMobile]);

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
    if (isMobile) {
      setMobileMenuOpen(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: userInfo?.username || '用户',
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'role',
      label: `角色: ${userInfo?.role || '-'}`,
      disabled: true,
    },
    {
      key: 'ai-quota',
      label: `AI 额度: ${userInfo?.todayAiUsed || 0}/${userInfo?.dailyAiQuota || 0}`,
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout,
    },
  ];

  const selectedKey =
    '/' + location.pathname.split('/').filter(Boolean).slice(0, 1).join('/') || '/dashboard';

  // Toggle sidebar - for mobile use overlay instead of collapse
  const handleToggleSidebar = () => {
    if (isMobile) {
      setMobileMenuOpen(!mobileMenuOpen);
    } else {
      setCollapsed(!collapsed);
    }
  };

  return (
    <Layout className="h-screen overflow-hidden">
      {/* ========== Mobile Overlay ========== */}
      <AnimatePresence>
        {isMobile && mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: prefersReducedMotion() ? 0 : 0.2 }}
            className="fixed inset-0 bg-black/50 lg:hidden"
            style={{ zIndex: Z_INDEX.overlay }}
            onClick={() => setMobileMenuOpen(false)}
          />
        )}
      </AnimatePresence>

      {/* ========== Sidebar ========== */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        theme="dark"
        className="!fixed left-0 top-0 bottom-0 overflow-auto transition-transform duration-300"
        style={{
          zIndex: Z_INDEX.sidebar,
          background: '#111827',
          transform: isMobile
            ? mobileMenuOpen
              ? 'translateX(0)'
              : 'translateX(-100%)'
            : undefined,
        }}
      >
        {/* Logo */}
        <div
          className="h-16 flex items-center border-b border-white/[0.06]"
          style={{
            padding: collapsed ? '0' : '0 20px',
            justifyContent: collapsed ? 'center' : 'flex-start',
          }}
        >
          <div className="w-8 h-8 rounded-lg bg-white/10 flex items-center justify-center shrink-0">
            <span className="text-white font-bold text-sm">S</span>
          </div>
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={{ opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: 'auto' }}
                exit={{ opacity: 0, width: 0 }}
                transition={{ duration: prefersReducedMotion() ? 0 : 0.2 }}
                className="text-white text-lg font-semibold ml-3 tracking-tight overflow-hidden whitespace-nowrap"
              >
                SmartATS
              </motion.span>
            )}
          </AnimatePresence>
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={['candidates-group']}
          items={menuItems}
          onClick={handleMenuClick}
          className="!border-r-0 mt-2"
        />
      </Sider>

      {/* ========== Main ========== */}
      <Layout
        className="transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)]"
        style={{
          marginLeft: isMobile ? 0 : collapsed ? 80 : 240,
        }}
      >
        {/* Header — Glass */}
        <Header
          className="!sticky top-0 z-40 h-16 flex items-center justify-between px-4 sm:px-6 lg:px-8"
          style={{
            zIndex: Z_INDEX.header,
            borderBottom: '0.5px solid rgba(0, 0, 0, 0.06)',
            background: 'rgba(255, 255, 255, 0.8)',
            backdropFilter: 'blur(16px)',
            WebkitBackdropFilter: 'blur(16px)',
          }}
        >
          <button
            onClick={handleToggleSidebar}
            className="text-lg text-gray-600 hover:text-gray-900 transition-colors cursor-pointer bg-transparent border-none p-1 rounded hover:bg-gray-100"
            aria-label={collapsed ? '展开菜单' : '收起菜单'}
          >
            {isMobile ? (
              mobileMenuOpen ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />
            ) : collapsed ? (
              <MenuUnfoldOutlined />
            ) : (
              <MenuFoldOutlined />
            )}
          </button>

          <Space size={20}>
            <Badge count={0} size="small">
              <BellOutlined className="text-lg text-gray-600 hover:text-gray-900 cursor-pointer transition-colors" />
            </Badge>

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space className="cursor-pointer">
                <Avatar
                  className="!bg-gray-900"
                  icon={<UserOutlined />}
                  size={34}
                />
                <span className="text-gray-700 font-medium text-sm hidden md:inline">
                  {userInfo?.username}
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        {/* Content */}
        <Content className="p-4 sm:p-6 lg:p-8 min-h-[calc(100vh-64px)] overflow-auto">
          <div className="max-w-[1400px] mx-auto">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
