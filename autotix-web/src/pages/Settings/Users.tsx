import { useEffect, useState } from 'react';
import {
  Table, Button, Select, Space, Modal, Form, Input, message, Tag, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  listUsers, createUser, changeUserRole, disableUser, type UserDTO,
} from '@/services/auth';

const ROLES = [
  { label: 'Admin', value: 'ADMIN' },
  { label: 'Agent', value: 'AGENT' },
  { label: 'Viewer', value: 'VIEWER' },
];

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'red',
  AGENT: 'blue',
  VIEWER: 'default',
};

interface CreateUserForm {
  email: string;
  displayName: string;
  password: string;
  role: string;
}

export default function UsersPage() {
  const [users, setUsers] = useState<UserDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<CreateUserForm>();

  async function fetchUsers() {
    setLoading(true);
    try {
      const data = await listUsers();
      setUsers(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchUsers(); }, []);

  async function handleRoleChange(userId: string, role: string) {
    try {
      await changeUserRole(userId, role);
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, role: role as UserDTO['role'] } : u)),
      );
      message.success('Role updated');
    } catch {
      message.error('Failed to update role');
    }
  }

  async function handleDisable(userId: string) {
    try {
      await disableUser(userId);
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, enabled: false } : u)),
      );
      message.success('User disabled');
    } catch {
      message.error('Failed to disable user');
    }
  }

  async function handleCreate() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await createUser(values);
      message.success('User created');
      setModalOpen(false);
      form.resetFields();
      await fetchUsers();
    } catch {
      message.error('Failed to create user');
    } finally {
      setSubmitting(false);
    }
  }

  const columns: ColumnsType<UserDTO> = [
    { title: 'Email', dataIndex: 'email', key: 'email' },
    { title: 'Display Name', dataIndex: 'displayName', key: 'displayName' },
    {
      title: 'Role',
      key: 'role',
      render: (_: unknown, r: UserDTO) => (
        <Select
          value={r.role}
          size="small"
          style={{ width: 100 }}
          options={ROLES}
          onChange={(v) => handleRoleChange(r.id, v)}
        />
      ),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Active' : 'Disabled'}</Tag>,
    },
    {
      title: 'Last Login',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (v?: string) => v ? new Date(v).toLocaleString() : '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, r: UserDTO) => (
        r.enabled ? (
          <Button size="small" danger onClick={() => handleDisable(r.id)}>Disable</Button>
        ) : null
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Users</Typography.Title>
        <Button type="primary" onClick={() => setModalOpen(true)}>Add User</Button>
      </div>

      <Table<UserDTO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={users}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="Add User"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        onOk={handleCreate}
        confirmLoading={submitting}
        okText="Create"
      >
        <Form<CreateUserForm> form={form} layout="vertical">
          <Form.Item
            name="email"
            label="Email"
            rules={[{ required: true }, { type: 'email' }]}
          >
            <Input placeholder="agent@example.com" />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="Display Name"
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[{ required: true, min: 8 }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="role"
            label="Role"
            rules={[{ required: true }]}
          >
            <Select options={ROLES} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
