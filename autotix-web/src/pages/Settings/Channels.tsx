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
// (label, value, defaultChannelType, lockType)
// lockType=true → channelType field disabled (auto-set from platform);
// CUSTOM and EMAIL only ones where multiple types are conceptually valid.
type Plat = {
  label: string; value: string;
  defaultChannelType: 'EMAIL' | 'CHAT';
  allowedChannelTypes: Array<'EMAIL' | 'CHAT'>;
};
const PLATFORMS: Plat[] = [
  { label: 'EMAIL (IMAP/SMTP)',                value: 'EMAIL',            defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'CUSTOM (test / generic webhook)',  value: 'CUSTOM',           defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT', 'EMAIL'] },
  { label: 'ZENDESK',                          value: 'ZENDESK',          defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'ZENDESK_SUNSHINE (stub)',          value: 'ZENDESK_SUNSHINE', defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'FRESHDESK (stub)',                 value: 'FRESHDESK',        defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'FRESHCHAT (stub)',                 value: 'FRESHCHAT',        defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'GORGIAS (stub)',                   value: 'GORGIAS',          defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'INTERCOM (stub)',                  value: 'INTERCOM',         defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'LIVECHAT (native widget)',          value: 'LIVECHAT',         defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'SHOPIFY (stub)',                   value: 'SHOPIFY',          defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'AMAZON (stub)',                    value: 'AMAZON',           defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'GMAIL (stub)',                     value: 'GMAIL',            defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'OUTLOOK (stub)',                   value: 'OUTLOOK',          defaultChannelType: 'EMAIL', allowedChannelTypes: ['EMAIL'] },
  { label: 'LINE (stub)',                      value: 'LINE',             defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'WHATSAPP (stub)',                  value: 'WHATSAPP',         defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'WECOM (stub)',                     value: 'WECOM',            defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'WECHAT (stub)',                    value: 'WECHAT',           defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
  { label: 'TIKTOK (stub)',                    value: 'TIKTOK',           defaultChannelType: 'CHAT',  allowedChannelTypes: ['CHAT'] },
];
const PLATFORM_OPTIONS = PLATFORMS.map(p => ({ label: p.label, value: p.value }));

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
      render: (token: string, r: ChannelDTO) => (
        <Space size="small">
          <Text code style={{ fontSize: 11 }}>{token?.slice(0, 12)}...</Text>
          <Tooltip title="Copy token">
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => { navigator.clipboard.writeText(token); message.success('Copied'); }}
            />
          </Tooltip>
          {r.platform === 'LIVECHAT' && (
            <Tooltip title="Copy embed snippet">
              <Button
                size="small"
                onClick={() => {
                  const host = window.location.origin;
                  const snippet = `<script src="${host}/widget/autotix-widget.js" data-channel-token="${token}" async></script>`;
                  navigator.clipboard.writeText(snippet);
                  message.success('Embed snippet copied!');
                }}
              >
                Copy snippet
              </Button>
            </Tooltip>
          )}
          {r.platform === 'LIVECHAT' && (
            <Tooltip title="Open demo page">
              <Button
                size="small"
                onClick={() => window.open(`/demo/livechat.html?token=${token}`, '_blank')}
              >
                Test
              </Button>
            </Tooltip>
          )}
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
            <Select
              options={PLATFORM_OPTIONS}
              placeholder="Select platform"
              showSearch
              optionFilterProp="label"
              onChange={(val) => {
                const p = PLATFORMS.find(x => x.value === val);
                if (p) form.setFieldsValue({ channelType: p.defaultChannelType });
              }}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.platform !== curr.platform}
          >
            {({ getFieldValue }) => {
              const selectedPlatform = getFieldValue('platform');
              const p = PLATFORMS.find(x => x.value === selectedPlatform);
              const opts = (p?.allowedChannelTypes ?? ['EMAIL', 'CHAT']).map(t => ({
                label: t === 'EMAIL' ? 'Email' : 'Chat', value: t,
              }));
              const lockType = p ? p.allowedChannelTypes.length === 1 : false;
              return (
                <Form.Item
                  name="channelType"
                  label="Channel Type"
                  rules={[{ required: true }]}
                  extra={lockType
                    ? `Auto-set by platform (${p?.value} only supports ${p?.allowedChannelTypes[0]}).`
                    : 'Pick the conversation style.'}
                >
                  <Select options={opts} placeholder="Select type" disabled={lockType} />
                </Form.Item>
              );
            }}
          </Form.Item>
          <Form.Item
            name="displayName"
            label="Display Name"
            rules={[{ required: true }]}
          >
            <Input placeholder="e.g. Support Mailbox" />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.platform !== curr.platform}
          >
            {({ getFieldValue }) => (getFieldValue('platform') === 'EMAIL' ? (
              <>
                <Typography.Title level={5} style={{ marginTop: 4 }}>IMAP (incoming)</Typography.Title>
                <Form.Item label="IMAP host" name={['credentials','imap_host']} rules={[{ required: true }]}>
                  <Input placeholder="mail (docker network name) or imap.gmail.com" />
                </Form.Item>
                <Space style={{ width: '100%' }} size="middle">
                  <Form.Item label="Port" name={['credentials','imap_port']} initialValue="3143" rules={[{ required: true }]}>
                    <Input style={{ width: 100 }} />
                  </Form.Item>
                  <Form.Item label="Use SSL" name={['credentials','imap_use_ssl']} initialValue="false">
                    <Select style={{ width: 100 }} options={[{label:'No',value:'false'},{label:'Yes',value:'true'}]} />
                  </Form.Item>
                </Space>
                <Form.Item label="IMAP user" name={['credentials','imap_user']} rules={[{ required: true }]}>
                  <Input placeholder="agent" />
                </Form.Item>
                <Form.Item label="IMAP password" name={['credentials','imap_password']} rules={[{ required: true }]}>
                  <Input.Password placeholder="secret" />
                </Form.Item>

                <Typography.Title level={5} style={{ marginTop: 4 }}>SMTP (outgoing)</Typography.Title>
                <Form.Item label="SMTP host" name={['credentials','smtp_host']} rules={[{ required: true }]}>
                  <Input placeholder="mail or smtp.gmail.com" />
                </Form.Item>
                <Space style={{ width: '100%' }} size="middle">
                  <Form.Item label="Port" name={['credentials','smtp_port']} initialValue="3025" rules={[{ required: true }]}>
                    <Input style={{ width: 100 }} />
                  </Form.Item>
                  <Form.Item label="Use TLS" name={['credentials','smtp_use_tls']} initialValue="false">
                    <Select style={{ width: 100 }} options={[{label:'No',value:'false'},{label:'Yes',value:'true'}]} />
                  </Form.Item>
                </Space>
                <Form.Item label="SMTP user" name={['credentials','smtp_user']} rules={[{ required: true }]}>
                  <Input placeholder="agent" />
                </Form.Item>
                <Form.Item label="SMTP password" name={['credentials','smtp_password']} rules={[{ required: true }]}>
                  <Input.Password placeholder="secret" />
                </Form.Item>
                <Form.Item label="From address" name={['credentials','from_address']} rules={[{ required: true, type: 'email' }]}>
                  <Input placeholder="agent@autotix.local" />
                </Form.Item>
              </>
            ) : (
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
            ))}
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
