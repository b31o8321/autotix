import { useEffect } from 'react';
import { Outlet, history, useLocation } from 'umi';
import { Layout, Tooltip, Avatar, Dropdown, Typography } from 'antd';
import {
  MailOutlined,
  BarChartOutlined,
  SettingOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { clearTokens, getAccessToken, getCurrentUser } from '@/utils/auth';

const { Header, Content } = Layout;
const { Text } = Typography;

const PAGE_TITLES: Record<string, string> = {
  '/inbox': 'Inbox',
  '/reports': 'Reports',
  '/settings': 'Settings',
  '/settings/channels': 'Channels',
  '/settings/ai': 'AI Configuration',
  '/settings/users': 'Users',
  '/settings/automation': 'Automation',
  '/settings/sla': 'SLA Policies',
  '/settings/tags': 'Tags',
  '/settings/custom-fields': 'Custom Fields',
  '/settings/general': 'General',
};

const NAV_ITEMS = [
  { path: '/inbox', icon: <MailOutlined />, label: 'Inbox' },
  { path: '/reports', icon: <BarChartOutlined />, label: 'Reports' },
  { path: '/settings', icon: <SettingOutlined />, label: 'Settings' },
];

export default function AppLayout() {
  const location = useLocation();
  const user = getCurrentUser();

  useEffect(() => {
    if (!getAccessToken()) {
      history.replace('/login');
    }
  }, []);

  function handleLogout() {
    clearTokens();
    history.push('/login');
  }

  const pageTitle =
    PAGE_TITLES[location.pathname] ||
    (location.pathname.startsWith('/inbox') ? 'Inbox' : 'Autotix');

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh', display: 'flex', flexDirection: 'row' }}>
      {/* 48px dark icon-only sidebar */}
      <div
        style={{
          width: 48,
          minHeight: '100vh',
          background: '#0B1426',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          paddingTop: 12,
          gap: 4,
          flexShrink: 0,
        }}
      >
        {/* Logo */}
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: '#2962FF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: 14,
            marginBottom: 16,
            cursor: 'pointer',
          }}
          onClick={() => history.push('/inbox')}
        >
          AT
        </div>
        {NAV_ITEMS.map(({ path, icon, label }) => {
          const active =
            path === '/settings'
              ? location.pathname.startsWith('/settings')
              : location.pathname.startsWith(path);
          return (
            <Tooltip key={path} title={label} placement="right">
              <div
                onClick={() => history.push(path)}
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 6,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: active ? '#FFFFFF' : 'rgba(255,255,255,0.5)',
                  background: active ? 'rgba(41,98,255,0.25)' : 'transparent',
                  cursor: 'pointer',
                  fontSize: 16,
                  transition: 'all 0.15s',
                }}
              >
                {icon}
              </div>
            </Tooltip>
          );
        })}
      </div>

      {/* Main area */}
      <Layout style={{ flex: 1, minWidth: 0 }}>
        {/* 48px white header */}
        <Header
          style={{
            height: 48,
            lineHeight: '48px',
            background: '#FFFFFF',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #EEF2F6',
            boxShadow: 'none',
          }}
        >
          <Text style={{ fontWeight: 600, fontSize: 14, color: '#0B1426' }}>{pageTitle}</Text>
          <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                padding: '4px 8px',
                borderRadius: 6,
              }}
            >
              <Avatar size={24} icon={<UserOutlined />} style={{ background: '#2962FF' }} />
              <Text style={{ fontSize: 12, color: '#5A6B7D' }}>
                {user?.email || 'User'}
              </Text>
            </div>
          </Dropdown>
        </Header>

        <Content style={{ flex: 1, overflow: 'hidden', background: '#F7F9FB' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
