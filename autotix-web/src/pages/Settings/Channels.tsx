import { useEffect, useState } from 'react';
import {
  Table, Button, Switch, Tag, Space, Modal, Form, Select, Input,
  message, Popconfirm, Typography, Tooltip,
} from 'antd';
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  listChannels, connectWithApiKey, disconnectChannel, setAutoReply, rotateWebhook,
  type ChannelDTO,
} from '@/services/channel';

const { Text } = Typography;

// Synced with backend PlatformType enum.
// "Functional" comment marks plugins with real implementations; the rest are scaffolds
// that throw UnsupportedOperationException on healthCheck and will fail to connect.
const PLATFORM_OPTIONS = [
  { label: 'CUSTOM (test / generic webhook)', value: 'CUSTOM' },        // functional
  { label: 'ZENDESK', value: 'ZENDESK' },                                // functional
  { label: 'ZENDESK_SUNSHINE (stub)', value: 'ZENDESK_SUNSHINE' },
  { label: 'FRESHDESK (stub)', value: 'FRESHDESK' },
  { label: 'FRESHCHAT (stub)', value: 'FRESHCHAT' },
  { label: 'GORGIAS (stub)', value: 'GORGIAS' },
  { label: 'INTERCOM (stub)', value: 'INTERCOM' },
  { label: 'LIVECHAT (stub)', value: 'LIVECHAT' },
  { label: 'SHOPIFY (stub)', value: 'SHOPIFY' },
  { label: 'AMAZON (stub)', value: 'AMAZON' },
  { label: 'GMAIL (stub)', value: 'GMAIL' },
  { label: 'OUTLOOK (stub)', value: 'OUTLOOK' },
  { label: 'LINE (stub)', value: 'LINE' },
  { label: 'WHATSAPP (stub)', value: 'WHATSAPP' },
  { label: 'WECOM (stub)', value: 'WECOM' },
  { label: 'WECHAT (stub)', value: 'WECHAT' },
  { label: 'TIKTOK (stub)', value: 'TIKTOK' },
];

interface CredentialRow {
  key: string;
  value: string;
}

export default function ChannelsPage() {
  const [channels, setChannels] = useState<ChannelDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [credentials, setCredentials] = useState<CredentialRow[]>([{ key: '', value: '' }]);
  const [form] = Form.useForm();

  async function fetchChannels() {
    setLoading(true);
    try {
      const data = await listChannels();
      setChannels(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchChannels(); }, []);

  async function handleAutoReply(channelId: string, enabled: boolean) {
    try {
      await setAutoReply(channelId, enabled);
      setChannels((prev) =>
        prev.map((c) => (c.id === channelId ? { ...c, autoReplyEnabled: enabled } : c)),
      );
    } catch {
      message.error('Failed to update auto-reply');
    }
  }

  async function handleRotate(channelId: string) {
    try {
      const result = await rotateWebhook(channelId) as { webhookToken?: string } | undefined;
      message.success(`New token: ${result?.webhookToken ?? '(see channel list)'}`);
      await fetchChannels();
    } catch {
      message.error('Failed to rotate webhook');
    }
  }

  async function handleDisconnect(channelId: string) {
    try {
      await disconnectChannel(channelId);
      message.success('Channel disconnected');
      await fetchChannels();
    } catch {
      message.error('Failed to disconnect channel');
    }
  }

  async function handleAdd() {
    const values = await form.validateFields();
    const creds: Record<string, string> = {};
    credentials.forEach((r) => { if (r.key) creds[r.key] = r.value; });
    setSubmitting(true);
    try {
      await connectWithApiKey({
        platform: values.platform,
        channelType: values.channelType,
        displayName: values.displayName,
        credentials: creds,
      });
      message.success('Channel connected');
      setModalOpen(false);
      form.resetFields();
      setCredentials([{ key: '', value: '' }]);
      await fetchChannels();
    } catch {
      message.error('Failed to connect channel');
    } finally {
      setSubmitting(false);
    }
  }

  const columns: ColumnsType<ChannelDTO> = [
    { title: 'Platform', dataIndex: 'platform', key: 'platform' },
    { title: 'Type', dataIndex: 'channelType', key: 'channelType' },
    { title: 'Name', dataIndex: 'displayName', key: 'displayName' },
    {
      title: 'Webhook Token',
      dataIndex: 'webhookToken',
      key: 'webhookToken',
      render: (token: string) => (
        <Space size="small">
          <Text code style={{ fontSize: 11 }}>{token?.slice(0, 12)}...</Text>
          <Tooltip title="Copy token">
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => { navigator.clipboard.writeText(token); message.success('Copied'); }}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: 'Auto Reply',
      key: 'autoReply',
      render: (_: unknown, r: ChannelDTO) => (
        <Switch
          checked={r.autoReplyEnabled}
          onChange={(v) => handleAutoReply(r.id, v)}
        />
      ),
    },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, r: ChannelDTO) => (
        <Space>
          <Tooltip title="Rotate webhook token">
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => handleRotate(r.id)}
            />
          </Tooltip>
          <Popconfirm
            title="Disconnect this channel?"
            onConfirm={() => handleDisconnect(r.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button size="small" danger>Disconnect</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Channels</Typography.Title>
        <Button type="primary" onClick={() => setModalOpen(true)}>Add Channel</Button>
      </div>

      <Table<ChannelDTO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={channels}
        pagination={false}
      />

      <Modal
        title="Add Channel"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        onOk={handleAdd}
        confirmLoading={submitting}
        okText="Connect"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="platform"
            label="Platform"
            rules={[{ required: true }]}
          >
            <Select options={PLATFORM_OPTIONS} placeholder="Select platform" />
          </Form.Item>
          <Form.Item
            name="channelType"
            label="Channel Type"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { label: 'Email', value: 'EMAIL' },
                { label: 'Chat', value: 'CHAT' },
              ]}
              placeholder="Select type"
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="Display Name"
            rules={[{ required: true }]}
          >
            <Input placeholder="e.g. Support Mailbox" />
          </Form.Item>
          <Form.Item label="Credentials">
            <Space direction="vertical" style={{ width: '100%' }}>
              {credentials.map((row, idx) => (
                <Space key={idx}>
                  <Input
                    placeholder="Key (e.g. apiKey)"
                    value={row.key}
                    onChange={(e) => {
                      const next = [...credentials];
                      next[idx] = { ...row, key: e.target.value };
                      setCredentials(next);
                    }}
                  />
                  <Input
                    placeholder="Value"
                    value={row.value}
                    onChange={(e) => {
                      const next = [...credentials];
                      next[idx] = { ...row, value: e.target.value };
                      setCredentials(next);
                    }}
                  />
                  {credentials.length > 1 && (
                    <Button
                      danger
                      size="small"
                      onClick={() => setCredentials(credentials.filter((_, i) => i !== idx))}
                    >
                      Remove
                    </Button>
                  )}
                </Space>
              ))}
              <Button
                size="small"
                onClick={() => setCredentials([...credentials, { key: '', value: '' }])}
              >
                Add Row
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
