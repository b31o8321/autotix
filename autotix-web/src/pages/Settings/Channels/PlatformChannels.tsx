import { useEffect, useState } from 'react';
import {
  Breadcrumb, Button, Popconfirm, Space, Switch, Table, Tag, Tooltip, Typography, message,
} from 'antd';
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import { history, useParams } from 'umi';
import type { ColumnsType } from 'antd/es/table';
import {
  listChannels, setAutoReply, rotateWebhook, disconnectChannel, type ChannelDTO,
} from '@/services/channel';

const { Text } = Typography;

export default function PlatformChannelsPage() {
  const { platform } = useParams<{ platform: string }>();
  const [channels, setChannels] = useState<ChannelDTO[]>([]);
  const [loading, setLoading] = useState(false);

  async function fetchChannels() {
    setLoading(true);
    try {
      const data = await listChannels(platform);
      setChannels(data);
    } catch {
      message.error('Failed to load channels');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchChannels(); }, [platform]);

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

  const columns: ColumnsType<ChannelDTO> = [
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
        <Switch checked={r.autoReplyEnabled} onChange={(v) => handleAutoReply(r.id, v)} />
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
          {r.platform === 'LIVECHAT' && (
            <Tooltip title="Open test widget in new tab">
              <Button
                size="small"
                type="primary"
                ghost
                onClick={() => window.open(`/demo/livechat.html?token=${r.webhookToken}`, '_blank')}
              >
                Test
              </Button>
            </Tooltip>
          )}
          <Tooltip title="Rotate webhook token">
            <Button size="small" icon={<ReloadOutlined />} onClick={() => handleRotate(r.id)} />
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
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Breadcrumb
        items={[
          { title: <a onClick={() => history.push('/settings/channels')}>Channels</a> },
          { title: platform },
        ]}
      />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{platform} Channels</Typography.Title>
        <Button
          type="primary"
          onClick={() => history.push(`/settings/channels/${platform}/new`)}
        >
          Add Channel
        </Button>
      </div>

      <Table<ChannelDTO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={channels}
        pagination={false}
      />
    </Space>
  );
}
