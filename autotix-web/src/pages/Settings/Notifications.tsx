import { useEffect, useState } from 'react';
import {
  Table, Button, Switch, Space, Modal, Form, Input, Select, Tag,
  message, Popconfirm, Typography,
} from 'antd';
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  listRoutes, createRoute, updateRoute, deleteRoute, testRoute,
  type NotificationRouteDTO, type CreateRoutePayload,
  type NotificationChannel, type NotificationEventKind,
} from '@/services/notification';

const { Text } = Typography;
const { Option } = Select;

interface RouteFormValues {
  name: string;
  eventKind: NotificationEventKind;
  channel: NotificationChannel;
  enabled: boolean;
  // EMAIL fields
  recipients?: string;          // comma-separated
  subjectTemplate?: string;
  // SLACK fields
  webhookUrl?: string;
  messageTemplate?: string;
}

function buildConfigJson(values: RouteFormValues): string {
  if (values.channel === 'EMAIL') {
    const to = (values.recipients || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    return JSON.stringify({
      to,
      subjectTemplate: values.subjectTemplate || '[Autotix] SLA breached on ticket {ticketId}',
    });
  }
  return JSON.stringify({
    webhookUrl: values.webhookUrl || '',
    messageTemplate: values.messageTemplate || ':warning: SLA breached on ticket {ticketId}: {subject}',
  });
}

function parseConfigJson(route: NotificationRouteDTO): Partial<RouteFormValues> {
  try {
    const cfg = JSON.parse(route.configJson);
    if (route.channel === 'EMAIL') {
      return {
        recipients: (cfg.to || []).join(', '),
        subjectTemplate: cfg.subjectTemplate,
      };
    }
    return {
      webhookUrl: cfg.webhookUrl,
      messageTemplate: cfg.messageTemplate,
    };
  } catch {
    return {};
  }
}

export default function NotificationsPage() {
  const [routes, setRoutes] = useState<NotificationRouteDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRoute, setEditingRoute] = useState<NotificationRouteDTO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [form] = Form.useForm<RouteFormValues>();
  const selectedChannel = Form.useWatch('channel', form);

  async function fetchRoutes() {
    setLoading(true);
    try {
      const data = await listRoutes();
      setRoutes(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchRoutes(); }, []);

  function openCreate() {
    setEditingRoute(null);
    form.resetFields();
    form.setFieldsValue({ eventKind: 'SLA_BREACHED', channel: 'SLACK_WEBHOOK', enabled: true });
    setModalOpen(true);
  }

  function openEdit(route: NotificationRouteDTO) {
    setEditingRoute(route);
    const extra = parseConfigJson(route);
    form.setFieldsValue({
      name: route.name,
      eventKind: route.eventKind,
      channel: route.channel,
      enabled: route.enabled,
      ...extra,
    });
    setModalOpen(true);
  }

  async function handleSubmit() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload: CreateRoutePayload = {
        name: values.name,
        eventKind: values.eventKind,
        channel: values.channel,
        configJson: buildConfigJson(values),
        enabled: values.enabled ?? true,
      };
      if (editingRoute) {
        const updated = await updateRoute(editingRoute.id, payload);
        setRoutes((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
        message.success('Route updated');
      } else {
        const created = await createRoute(payload);
        setRoutes((prev) => [...prev, created]);
        message.success('Route created');
      }
      setModalOpen(false);
    } catch {
      message.error('Failed to save route');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggle(route: NotificationRouteDTO, enabled: boolean) {
    try {
      const updated = await updateRoute(route.id, {
        name: route.name,
        eventKind: route.eventKind,
        channel: route.channel,
        configJson: route.configJson,
        enabled,
      });
      setRoutes((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch {
      message.error('Failed to update route');
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteRoute(id);
      setRoutes((prev) => prev.filter((r) => r.id !== id));
      message.success('Route deleted');
    } catch {
      message.error('Failed to delete route');
    }
  }

  async function handleTest(id: number) {
    setTestingId(id);
    try {
      const result = await testRoute(id);
      if (result.success) {
        message.success(result.message || 'Test notification sent');
      } else {
        message.warning(result.message || 'Test dispatched but may have failed — check server logs');
      }
    } catch {
      message.error('Test failed — check server logs');
    } finally {
      setTestingId(null);
    }
  }

  const columns: ColumnsType<NotificationRouteDTO> = [
    {
      title: 'Name',
      dataIndex: 'name',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'Event',
      dataIndex: 'eventKind',
      render: (kind: string) => <Tag color="blue">{kind}</Tag>,
    },
    {
      title: 'Channel',
      dataIndex: 'channel',
      render: (ch: string) => (
        <Tag color={ch === 'SLACK_WEBHOOK' ? 'purple' : 'green'}>{ch}</Tag>
      ),
    },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      render: (enabled: boolean, record) => (
        <Switch
          checked={enabled}
          onChange={(val) => handleToggle(record, val)}
          size="small"
        />
      ),
    },
    {
      title: 'Actions',
      render: (_: unknown, record) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(record)}>Edit</Button>
          <Button
            size="small"
            icon={<ThunderboltOutlined />}
            loading={testingId === record.id}
            onClick={() => handleTest(record.id)}
          >
            Test
          </Button>
          <Popconfirm
            title="Delete this notification route?"
            onConfirm={() => handleDelete(record.id)}
            okText="Delete"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Notification Routes</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add Route
        </Button>
      </div>

      <Table
        rowKey="id"
        dataSource={routes}
        columns={columns}
        loading={loading}
        pagination={{ pageSize: 20 }}
        size="small"
      />

      <Modal
        title={editingRoute ? 'Edit Notification Route' : 'Add Notification Route'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        okText={editingRoute ? 'Save' : 'Create'}
        confirmLoading={submitting}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ eventKind: 'SLA_BREACHED', enabled: true }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="e.g. Ops Slack SLA Alert" />
          </Form.Item>

          <Form.Item name="eventKind" label="Event Kind" rules={[{ required: true }]}>
            <Select>
              <Option value="SLA_BREACHED">SLA_BREACHED</Option>
            </Select>
          </Form.Item>

          <Form.Item name="channel" label="Channel" rules={[{ required: true }]}>
            <Select>
              <Option value="SLACK_WEBHOOK">Slack Webhook</Option>
              <Option value="EMAIL">Email</Option>
            </Select>
          </Form.Item>

          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>

          {/* EMAIL-specific fields */}
          {selectedChannel === 'EMAIL' && (
            <>
              <Form.Item
                name="recipients"
                label="Recipients (comma-separated)"
                rules={[{ required: true, message: 'At least one recipient is required' }]}
              >
                <Input placeholder="ops@company.com, alerts@company.com" />
              </Form.Item>
              <Form.Item name="subjectTemplate" label="Subject Template">
                <Input placeholder="[Autotix] SLA breached on ticket {ticketId}" />
              </Form.Item>
            </>
          )}

          {/* SLACK_WEBHOOK-specific fields */}
          {selectedChannel === 'SLACK_WEBHOOK' && (
            <>
              <Form.Item
                name="webhookUrl"
                label="Webhook URL"
                rules={[{ required: true, message: 'Webhook URL is required' }]}
              >
                <Input placeholder="https://hooks.slack.com/services/..." />
              </Form.Item>
              <Form.Item name="messageTemplate" label="Message Template">
                <Input.TextArea
                  rows={2}
                  placeholder=":warning: SLA breached on ticket {ticketId}: {subject}"
                />
              </Form.Item>
            </>
          )}

          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Available placeholders: {'{ticketId}'}, {'{externalTicketId}'}, {'{subject}'},{' '}
            {'{customerIdentifier}'}, {'{priority}'}, {'{status}'}, {'{breachedAt}'}, {'{ticketUrl}'}
          </Typography.Text>
        </Form>
      </Modal>
    </div>
  );
}
