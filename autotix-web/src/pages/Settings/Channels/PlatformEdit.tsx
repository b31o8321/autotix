import { useEffect, useState } from 'react';
import {
  Alert, Breadcrumb, Button, Card, Form, Input, Space, Spin, Switch, Typography, message,
} from 'antd';
import { history, useParams } from 'umi';
import {
  listChannels, renameChannel, setAutoReply, rotateWebhook,
  type ChannelDTO,
} from '@/services/channel';

export default function PlatformEdit() {
  const { platform, channelId } = useParams<{ platform: string; channelId: string }>();
  const [channel, setChannel] = useState<ChannelDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ displayName: string }>();

  async function load() {
    if (!platform || !channelId) return;
    setLoading(true);
    const all = await listChannels(platform);
    const found = all.find((c) => c.id === channelId);
    setChannel(found ?? null);
    if (found) form.setFieldsValue({ displayName: found.displayName });
    setLoading(false);
  }

  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [channelId, platform]);

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await renameChannel(channelId!, values.displayName);
      message.success('Saved');
      await load();
    } catch {
      message.error('Save failed');
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleAutoReply(v: boolean) {
    if (!channel) return;
    try {
      await setAutoReply(channel.id, v);
      message.success(v ? 'Auto-reply ON' : 'Auto-reply OFF');
      await load();
    } catch { message.error('Failed'); }
  }

  async function handleRotate() {
    if (!channel) return;
    try {
      const r = await rotateWebhook(channel.id) as { webhookToken?: string };
      message.success(`Token rotated${r?.webhookToken ? `: ${r.webhookToken.slice(0, 8)}…` : ''}`);
      await load();
    } catch { message.error('Failed'); }
  }

  if (loading) return <Spin />;
  if (!channel) return <Alert type="error" message="Channel not found" />;

  const isLivechat = channel.platform === 'LIVECHAT';
  const host = window.location.origin;
  const snippet = `<script src="${host}/widget/autotix-widget.js" data-channel-token="${channel.webhookToken}" async></script>`;
  const testUrl = `/demo/livechat.html?token=${channel.webhookToken}`;

  return (
    <Space direction="vertical" style={{ width: '100%', maxWidth: 720 }} size="middle">
      <Breadcrumb
        items={[
          { title: <a onClick={() => history.push('/settings/channels')}>Channels</a> },
          { title: <a onClick={() => history.push(`/settings/channels/${platform}`)}>{platform}</a> },
          { title: 'Edit' },
        ]}
      />
      <Typography.Title level={4} style={{ margin: 0 }}>Edit Channel</Typography.Title>

      <Card title="Basic" size="small">
        <Form form={form} layout="vertical">
          <Form.Item label="Display name" name="displayName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Space>
            <Button type="primary" loading={saving} onClick={handleSave}>Save</Button>
            <Button onClick={() => history.push(`/settings/channels/${platform}`)}>Back</Button>
          </Space>
        </Form>
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>Auto reply</span>
          <Switch checked={channel.autoReplyEnabled} onChange={handleToggleAutoReply} />
        </div>
        <div style={{ marginTop: 8, fontSize: 12, color: '#5A6B7D' }}>
          Webhook token: <code>{channel.webhookToken}</code>{' '}
          <Button size="small" onClick={handleRotate}>Rotate</Button>
        </div>
      </Card>

      {isLivechat && (
        <>
          <Card title="Embed snippet" size="small">
            <pre style={{ background: '#F7F9FB', padding: 12, borderRadius: 6, fontSize: 12, overflow: 'auto', margin: 0 }}>{snippet}</pre>
            <Space style={{ marginTop: 8 }}>
              <Button onClick={() => { navigator.clipboard.writeText(snippet); message.success('Copied'); }}>Copy</Button>
              <Button type="primary" onClick={() => window.open(testUrl, '_blank')}>Test in new tab</Button>
            </Space>
            <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 8, marginBottom: 0 }}>
              Paste the snippet into any HTML page. The widget loads automatically and connects via WebSocket.
            </Typography.Paragraph>
          </Card>
          <Card title="Live test (embedded)" size="small" bodyStyle={{ padding: 0 }}>
            <iframe src={testUrl} title="LiveChat demo" style={{ width: '100%', height: 600, border: 0, display: 'block' }} />
          </Card>
        </>
      )}
    </Space>
  );
}
