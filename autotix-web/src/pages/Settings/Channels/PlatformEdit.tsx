import { useEffect, useState } from 'react';
import {
  Alert, Breadcrumb, Button, Card, Form, Input, Space, Spin, Switch, Typography, message,
} from 'antd';
import { history, useParams } from 'umi';
import {
  listChannels, renameChannel, setAutoReply, rotateWebhook, setChannelSecret,
  type ChannelDTO,
} from '@/services/channel';

export default function PlatformEdit() {
  const { platform, channelId } = useParams<{ platform: string; channelId: string }>();
  const [channel, setChannel] = useState<ChannelDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingSecret, setSavingSecret] = useState(false);
  const [form] = Form.useForm<{ displayName: string }>();
  const [secretForm] = Form.useForm<{ secret: string }>();

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

  async function handleSaveSecret() {
    const values = await secretForm.validateFields();
    setSavingSecret(true);
    try {
      await setChannelSecret(channelId!, values.secret ?? '');
      message.success('Webhook secret saved');
      secretForm.resetFields();
    } catch {
      message.error('Failed to save secret');
    } finally {
      setSavingSecret(false);
    }
  }

  if (loading) return <Spin />;
  if (!channel) return <Alert type="error" message="Channel not found" />;

  const isLivechat = channel.platform === 'LIVECHAT';
  const isZendesk = channel.platform === 'ZENDESK';
  const isShopify = channel.platform === 'SHOPIFY';
  const isFreshdesk = channel.platform === 'FRESHDESK';
  const isLine = channel.platform === 'LINE';
  const isTelegram = channel.platform === 'TELEGRAM';
  const isWecom = channel.platform === 'WECOM';
  const host = window.location.origin;
  const snippet = `<script src="${host}/widget/autotix-widget.js" data-channel-token="${channel.webhookToken}" async></script>`;
  const testUrl = `/demo/livechat.html?token=${channel.webhookToken}`;
  const zendeskInboundUrl = `${host}/v2/webhook/ZENDESK/${channel.webhookToken}`;
  const shopifyInboundUrl = `${host}/v2/webhook/SHOPIFY/${channel.webhookToken}`;
  const freshdeskInboundUrl = `${host}/v2/webhook/FRESHDESK/${channel.webhookToken}`;
  const lineInboundUrl = `${host}/v2/webhook/LINE/${channel.webhookToken}`;
  const telegramInboundUrl = `${host}/v2/webhook/TELEGRAM/${channel.webhookToken}`;
  const wecomInboundUrl = `${host}/v2/webhook/WECOM/${channel.webhookToken}`;

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

      {isZendesk && (
        <Card title="Inbound Webhook" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            Create a Zendesk Webhook (Admin → Apps and integrations → Webhooks) pointing at this URL.
            Add a Signing Secret in Zendesk and paste it below to enable signature verification.
          </Typography.Paragraph>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {zendeskInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(zendeskInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
          <Form form={secretForm} layout="vertical">
            <Form.Item label="Webhook Signing Secret" name="secret">
              <Input.Password placeholder="Paste the signing secret from Zendesk Webhook settings" />
            </Form.Item>
            <Button type="primary" loading={savingSecret} onClick={handleSaveSecret}>Save Secret</Button>
          </Form>
        </Card>
      )}

      {isShopify && (
        <Card title="Inbound Webhook" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            In Shopify Admin → Settings → Notifications → Webhooks → Create webhook.
            Set Event to <strong>Order creation</strong> (and optionally Order cancellation, Customer creation),
            Format to <strong>JSON</strong>, and paste the URL below as the destination.
            After saving, copy the signing secret Shopify shows and paste it below.
          </Typography.Paragraph>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {shopifyInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(shopifyInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
          <Form form={secretForm} layout="vertical">
            <Form.Item label="Webhook Shared Secret" name="secret">
              <Input.Password placeholder="Paste the signing secret from Shopify Webhook settings" />
            </Form.Item>
            <Button type="primary" loading={savingSecret} onClick={handleSaveSecret}>Save Secret</Button>
          </Form>
        </Card>
      )}

      {isFreshdesk && (
        <Card title="Inbound Webhook" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            In Freshdesk Admin → Workflows → Automations, create a rule (e.g. New Ticket Created).
            Add action <strong>Trigger Webhook</strong>, set Content Type to <strong>JSON</strong>,
            and paste the URL below as the Request URL.
            Optionally add a Custom Header <strong>X-Autotix-Webhook-Token</strong> with a secret value,
            then save the same value below to enable token verification.
          </Typography.Paragraph>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {freshdeskInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(freshdeskInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
          <Form form={secretForm} layout="vertical">
            <Form.Item label="Webhook Token" name="secret">
              <Input.Password placeholder="(optional) value sent in X-Autotix-Webhook-Token header" />
            </Form.Item>
            <Button type="primary" loading={savingSecret} onClick={handleSaveSecret}>Save Token</Button>
          </Form>
        </Card>
      )}

      {isLine && (
        <Card title="Inbound Webhook" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            In LINE Developer Console → your Messaging API channel → Webhook settings,
            paste the URL below as the <strong>Webhook URL</strong>.
            Enable <strong>Use webhook</strong> and disable <strong>Auto-reply messages</strong>
            under Response settings (to prevent LINE's default bot from replying alongside Autotix).
            Signature verification is automatic — the channel_secret stored in your credentials is used.
          </Typography.Paragraph>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {lineInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(lineInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
        </Card>
      )}

      {isTelegram && (
        <Card title="Telegram Webhook Setup" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            1. Talk to <strong>@BotFather</strong> in Telegram → <code>/newbot</code> → follow prompts → copy the token into the channel credentials.
            <br />
            2. Copy the Inbound URL below — this is where Telegram will POST message updates.
            <br />
            3. Register the webhook by running the curl command below (replace placeholders with your actual values).
            <br />
            4. (Optional) Set a Webhook Secret Token in credentials and include it in the curl command; Autotix will verify it on every inbound call.
          </Typography.Paragraph>
          <div style={{ marginBottom: 8 }}>
            <strong style={{ fontSize: 12 }}>Inbound URL</strong>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {telegramInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(telegramInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
          <div style={{ marginBottom: 4 }}>
            <strong style={{ fontSize: 12 }}>Register webhook (run once after channel creation)</strong>
          </div>
          <pre style={{ background: '#F7F9FB', padding: 12, borderRadius: 6, fontSize: 11, overflow: 'auto', margin: 0, marginBottom: 8 }}>
{`curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \\
  -H "Content-Type: application/json" \\
  -d '{"url":"${telegramInboundUrl}","secret_token":"<OPTIONAL_SECRET>"}'`}
          </pre>
          <Button
            size="small"
            onClick={() => {
              const cmd = `curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \\\n  -H "Content-Type: application/json" \\\n  -d '{"url":"${telegramInboundUrl}","secret_token":"<OPTIONAL_SECRET>"}'`;
              navigator.clipboard.writeText(cmd);
              message.success('curl command copied');
            }}
          >
            Copy curl command
          </Button>
          <div style={{ marginTop: 16 }}>
            <Form form={secretForm} layout="vertical">
              <Form.Item label="Webhook Secret Token" name="secret" extra="(optional) set here and pass as secret_token in the curl command above — Autotix will verify it on every inbound update.">
                <Input.Password placeholder="1-256 chars A-Za-z0-9_-" />
              </Form.Item>
              <Button type="primary" loading={savingSecret} onClick={handleSaveSecret}>Save Secret Token</Button>
            </Form>
          </div>
        </Card>
      )}

      {isWecom && (
        <Card title="WeCom 微信客服 — Inbound Webhook" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            在企业微信管理后台 → 应用管理 → 微信客服 → API 接收消息配置 → URL，粘贴下方地址。
            Token 和 EncodingAESKey 已在频道凭证中保存，无需单独填写 Webhook Secret。
            保存后 WeCom 会立即发送 GET 验证请求，Autotix 将自动通过。
          </Typography.Paragraph>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <code style={{ background: '#F7F9FB', padding: '4px 8px', borderRadius: 4, fontSize: 12, flex: 1, wordBreak: 'break-all' }}>
              {wecomInboundUrl}
            </code>
            <Button
              size="small"
              icon={<span>⎘</span>}
              onClick={() => { navigator.clipboard.writeText(wecomInboundUrl); message.success('URL copied'); }}
            >
              Copy
            </Button>
          </div>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
            每次消息事件到达时，Autotix 自动调用 kf/sync_msg 拉取消息，并通过 kf/send_msg 发送回复。
          </Typography.Paragraph>
        </Card>
      )}

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
