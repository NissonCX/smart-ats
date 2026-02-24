import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Space, Typography, Badge } from 'antd';
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
} from '@ant-design/icons';
import { useAuthStore } from '../store/auth';
import type { MenuProps } from 'antd';

const { Sider, Header, Content } = Layout;
const { Text } = Typography;

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
    key: '/candidates',
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
    key: '/settings',
    icon: <SettingOutlined />,
    label: '系统设置',
  },
];

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, logout } = useAuthStore();

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: `${userInfo?.username || '用户'}`,
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

  // 获取当前选中的菜单 key
  const selectedKey = '/' + location.pathname.split('/').filter(Boolean)[0] || '/dashboard';

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        theme="dark"
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
          }}
        >
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              color: '#fff',
              fontSize: 16,
              flexShrink: 0,
            }}
          >
            S
          </div>
          {!collapsed && (
            <Text
              strong
              style={{
                color: '#fff',
                fontSize: 18,
                marginLeft: 12,
                letterSpacing: -0.5,
              }}
            >
              SmartATS
            </Text>
          )}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={['/candidates']}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ borderRight: 0, marginTop: 8 }}
        />
      </Sider>

      <Layout style={{ marginLeft: collapsed ? 80 : 240, transition: 'margin-left 0.2s' }}>
        <Header
          style={{
            padding: '0 24px',
            background: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #e2e8f0',
            position: 'sticky',
            top: 0,
            zIndex: 99,
            height: 64,
          }}
        >
          <div
            onClick={() => setCollapsed(!collapsed)}
            style={{ fontSize: 18, cursor: 'pointer', color: '#64748b' }}
          >
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>

          <Space size={20}>
            <Badge count={0} size="small">
              <BellOutlined style={{ fontSize: 18, color: '#64748b', cursor: 'pointer' }} />
            </Badge>

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar
                  style={{ backgroundColor: '#4f46e5' }}
                  icon={<UserOutlined />}
                  size={36}
                />
                {!collapsed && (
                  <Text style={{ color: '#334155', fontWeight: 500 }}>
                    {userInfo?.username}
                  </Text>
                )}
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content
          style={{
            padding: 24,
            minHeight: 'calc(100vh - 64px)',
            overflow: 'auto',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
