import { useState } from 'react';
import { Card, Form, Input, Button, message } from 'antd';
import { history } from 'umi';
import { login, me } from '@/services/auth';
import { setTokens, getCurrentUser } from '@/utils/auth';

interface LoginForm {
  email: string;
  password: string;
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false);

  async function handleSubmit(values: LoginForm) {
    setLoading(true);
    try {
      const resp = await login(values.email, values.password);
      setTokens(resp);
      // Fetch full user info to populate localStorage
      try {
        const userInfo = await me();
        setTokens({ ...resp, user: userInfo });
      } catch {
        // ignore; token already set
      }
      history.push('/inbox');
    } catch (err: unknown) {
      const e = err as { message?: string };
      message.error(e?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card title="Autotix Login" style={{ width: 400 }}>
        <Form<LoginForm> layout="vertical" onFinish={handleSubmit} autoComplete="off">
          <Form.Item
            label="Email"
            name="email"
            rules={[
              { required: true, message: 'Please enter your email' },
              { type: 'email', message: 'Please enter a valid email' },
            ]}
          >
            <Input placeholder="agent@example.com" />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[{ required: true, message: 'Please enter your password' }]}
          >
            <Input.Password placeholder="Password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              Login
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
