import { useEffect, useState } from 'react';
import { Outlet, history, useLocation } from 'umi';
import { Layout, Menu, Button, Avatar, Space, Typography, theme } from 'antd';
import {
  InboxOutlined,
  BarChartOutlined,
  SettingOutlined,
  CustomerServiceOutlined,
  UserOutlined,
  LogoutOutlined,
  ApiOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { clearTokens, getAccessToken, getCurrentUser } from '@/utils/auth';

const { Sider, Header, Content } = Layout;
const { Text } = Typography;

export default function AppLayout() {
  const location = useLocation();
  const user = getCurrentUser();
  const { token: designToken } = theme.useToken();

  useEffect(() => {
    if (!getAccessToken()) {
      history.replace('/login');
    }
  }, []);

  const isAdmin = user?.role === 'ADMIN';

  function handleLogout() {
    clearTokens();
    history.push('/login');
  }

  const pathKey = location.pathname;

  const menuItems = [
    {
      key: '/desk',
      icon: <CustomerServiceOutlined />,
      label: 'Desk',
      onClick: () => history.push('/desk'),
    },
    {
      key: '/inbox',
      icon: <InboxOutlined />,
      label: 'Inbox',
      onClick: () => history.push('/inbox'),
    },
    {
      key: '/reports',
      icon: <BarChartOutlined />,
      label: 'Reports',
      onClick: () => history.push('/reports'),
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: 'Settings',
      children: [
        {
          key: '/settings/channels',
          icon: <ApiOutlined />,
          label: 'Channels',
          onClick: () => history.push('/settings/channels'),
        },
        {
          key: '/settings/ai',
          icon: <ThunderboltOutlined />,
          label: 'AI Config',
          onClick: () => history.push('/settings/ai'),
        },
        ...(isAdmin
          ? [
              {
                key: '/settings/automation',
                icon: <ThunderboltOutlined />,
                label: 'Automation',
                onClick: () => history.push('/settings/automation'),
              },
              {
                key: '/settings/users',
                icon: <TeamOutlined />,
                label: 'Users',
                onClick: () => history.push('/settings/users'),
              },
              {
                key: '/settings/sla',
                icon: <ClockCircleOutlined />,
                label: 'SLA Policies',
                onClick: () => history.push('/settings/sla'),
              },
            ]
          : []),
      ],
    },
  ];

  const selectedKeys = [pathKey];
  const openKeys = pathKey.startsWith('/settings') ? ['/settings'] : [];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark">
        <div
          style={{
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: 18,
            letterSpacing: 1,
          }}
        >
          Autotix
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: designToken.colorBgContainer,
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            borderBottom: `1px solid ${designToken.colorBorderSecondary}`,
          }}
        >
          <Space>
            <Avatar icon={<UserOutlined />} />
            <Text>{user?.displayName || user?.email || 'User'}</Text>
            <Button
              icon={<LogoutOutlined />}
              type="text"
              onClick={handleLogout}
            >
              Logout
            </Button>
          </Space>
        </Header>
        <Content style={{ margin: 24, background: designToken.colorBgContainer, borderRadius: designToken.borderRadius, padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
