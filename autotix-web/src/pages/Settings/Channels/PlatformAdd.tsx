import { useEffect, useState } from 'react';
import {
  Alert, Breadcrumb, Button, Card, Collapse, Form, Input, Select, Space, Spin, Typography, message,
} from 'antd';
import { history, useParams } from 'umi';
import { getPlatforms, type PlatformDescriptorDTO } from '@/services/platform';
import { connectWithApiKey, listChannels, startOAuth } from '@/services/channel';
import AuthFieldRenderer from '@/components/AuthFieldRenderer';
import EmailCredentialsForm from '@/components/EmailCredentialsForm';

export default function PlatformAddPage() {
  const { platform } = useParams<{ platform: string }>();
  const [descriptor, setDescriptor] = useState<PlatformDescriptorDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [oauthComingSoon, setOauthComingSoon] = useState(false);
  const [createdLivechat, setCreatedLivechat] = useState<{ id: string; token: string } | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    getPlatforms()
      .then((list) => {
        const found = list.find((p) => p.platform === platform);
        setDescriptor(found ?? null);
        if (found) {
          form.setFieldsValue({ channelType: found.defaultChannelType });
        }
      })
      .catch(() => message.error('Failed to load platform info'))
      .finally(() => setLoading(false));
  }, [platform]);

  async function handleSubmit() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const credentials: Record<string, string> = {};
      // Flatten form credentials to string map
      if (values.credentials) {
        Object.entries(values.credentials as Record<string, unknown>).forEach(([k, v]) => {
          if (v !== undefined && v !== null) credentials[k] = String(v);
        });
      }
      const resp = await connectWithApiKey({
        platform: platform!,
        channelType: values.channelType,
        displayName: values.displayName,
        credentials,
      });
      message.success('Channel connected');
      // For LIVECHAT: stay on this page and show the embed snippet + test link
      if (platform === 'LIVECHAT') {
        const channels = await listChannels(platform);
        const created = channels.find((c) => c.id === (resp as { channelId?: string })?.channelId)
          ?? channels[channels.length - 1];
        if (created) {
          setCreatedLivechat({ id: created.id, token: created.webhookToken });
          return; // don't navigate away
        }
      }
      history.push(`/settings/channels/${platform}`);
    } catch {
      message.error('Failed to connect channel');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleStartOAuth() {
    if (!descriptor) return;
    setSubmitting(true);
    try {
      const values = await form.validateFields(['channelType', 'displayName']);
      const result = await startOAuth(platform!, values.channelType, values.displayName) as {
        authorizeUrl?: string;
      };
      if (result?.authorizeUrl) {
        window.location.href = result.authorizeUrl;
      }
    } catch (err: unknown) {
      // 501 = OAuth not yet implemented — show friendly message
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 501) {
        setOauthComingSoon(true);
      } else {
        message.error('Failed to start OAuth');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>;
  }

  if (!descriptor) {
    return <Alert type="error" message={`Unknown platform: ${platform}`} />;
  }

  const channelTypeOptions = descriptor.allowedChannelTypes.map((t) => ({
    label: t === 'EMAIL' ? 'Email' : 'Chat',
    value: t,
  }));
  const lockType = descriptor.allowedChannelTypes.length === 1;

  if (createdLivechat) {
    const host = window.location.origin;
    const snippet = `<script src="${host}/widget/autotix-widget.js" data-channel-token="${createdLivechat.token}" async></script>`;
    const testUrl = `/demo/livechat.html?token=${createdLivechat.token}`;
    return (
      <Space direction="vertical" style={{ width: '100%', maxWidth: 720 }} size="middle">
        <Breadcrumb
          items={[
            { title: <a onClick={() => history.push('/settings/channels')}>Channels</a> },
            { title: <a onClick={() => history.push(`/settings/channels/${platform}`)}>{platform}</a> },
            { title: 'New' },
          ]}
        />
        <Alert
          type="success"
          showIcon
          message="LiveChat channel created"
          description="Embed the snippet on any page, or click Test to open the live demo with this token preloaded."
        />
        <Card title="Embed snippet" size="small">
          <pre style={{ background:'#F7F9FB', padding:12, borderRadius:6, fontSize:12, overflow:'auto', margin:0 }}>{snippet}</pre>
          <Space style={{ marginTop:8 }}>
            <Button onClick={() => { navigator.clipboard.writeText(snippet); message.success('Copied'); }}>Copy</Button>
            <Button type="primary" onClick={() => window.open(testUrl, '_blank')}>Test in new tab</Button>
            <Button onClick={() => history.push(`/settings/channels/${platform}`)}>Back to list</Button>
          </Space>
        </Card>
        <Card title="Live test (embedded)" size="small" bodyStyle={{ padding:0 }}>
          <iframe
            src={testUrl}
            title="LiveChat demo"
            style={{ width:'100%', height:600, border:0, display:'block' }}
          />
        </Card>
      </Space>
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%', maxWidth: 560 }} size="middle">
      <Breadcrumb
        items={[
          { title: <a onClick={() => history.push('/settings/channels')}>Channels</a> },
          { title: <a onClick={() => history.push(`/settings/channels/${platform}`)}>{platform}</a> },
          { title: 'New' },
        ]}
      />
      <Typography.Title level={4} style={{ margin: 0 }}>
        Add {descriptor.displayName} Channel
      </Typography.Title>

      {descriptor.setupGuide && (
        <SetupGuideAlert platform={descriptor.displayName} guide={descriptor.setupGuide} />
      )}

      {oauthComingSoon && (
        <Alert
          type="info"
          showIcon
          message="OAuth coming soon"
          description={
            `OAuth for ${descriptor.displayName} is not yet implemented. ` +
            'Please use the App Credentials form below to enter your credentials manually instead.'
          }
          closable
          onClose={() => setOauthComingSoon(false)}
        />
      )}

      <Form form={form} layout="vertical">
        <Form.Item
          name="displayName"
          label="Display Name"
          rules={[{ required: true, message: 'Display name is required' }]}
        >
          <Input placeholder="e.g. Support Mailbox" />
        </Form.Item>

        <Form.Item
          name="channelType"
          label="Channel Type"
          rules={[{ required: true }]}
          extra={
            lockType
              ? `Auto-set (${descriptor.platform} only supports ${descriptor.allowedChannelTypes[0]})`
              : undefined
          }
        >
          <Select options={channelTypeOptions} disabled={lockType} />
        </Form.Item>

        {renderCredentialsForm(descriptor)}

        <Form.Item style={{ marginTop: 24 }}>
          {descriptor.authMethod === 'OAUTH2' ? (
            <Space>
              <Button
                type="primary"
                loading={submitting}
                onClick={handleStartOAuth}
              >
                Start OAuth
              </Button>
              <Button onClick={() => history.push(`/settings/channels/${platform}`)}>
                Cancel
              </Button>
            </Space>
          ) : (
            <Space>
              <Button
                type="primary"
                loading={submitting}
                onClick={handleSubmit}
              >
                Connect
              </Button>
              <Button onClick={() => history.push(`/settings/channels/${platform}`)}>
                Cancel
              </Button>
            </Space>
          )}
        </Form.Item>
      </Form>
    </Space>
  );
}

function renderCredentialsForm(descriptor: PlatformDescriptorDTO) {
  switch (descriptor.authMethod) {
    case 'NONE':
      // CUSTOM platform — no credentials needed
      return null;

    case 'EMAIL_BASIC':
      return <EmailCredentialsForm />;

    case 'OAUTH2':
      // No platform currently uses OAUTH2 — kept as a dead branch for future use.
      return (
        <Alert
          type="info"
          showIcon
          message="OAuth coming in v2"
          description={`OAuth for ${descriptor.displayName} is not yet available in self-hosted mode. Please use API token credentials instead.`}
        />
      );

    case 'API_KEY':
    case 'APP_CREDENTIALS':
    default:
      return <AuthFieldRenderer fields={descriptor.authFields} />;
  }
}

/**
 * Renders the platform setup guide as an expandable Alert.
 * Lines starting with a digit+period are rendered as a numbered list.
 * Bare URLs are rendered as clickable links.
 * Collapsed by default when the guide is longer than 200 characters.
 */
function SetupGuideAlert({ platform, guide }: { platform: string; guide: string }) {
  const COLLAPSE_THRESHOLD = 200;
  const shouldCollapse = guide.length > COLLAPSE_THRESHOLD;

  const lines = guide.split('\n');

  const renderedLines = lines.map((line, idx) => {
    // Split line by URL-like patterns and make them clickable
    const urlPattern = /(https?:\/\/[^\s]+)/g;
    const parts = line.split(urlPattern);
    const content = parts.map((part, i) =>
      urlPattern.test(part)
        ? <a key={i} href={part} target="_blank" rel="noopener noreferrer">{part}</a>
        : part
    );
    return (
      <Typography.Paragraph key={idx} style={{ marginBottom: 4 }}>
        {content}
      </Typography.Paragraph>
    );
  });

  const body = <div>{renderedLines}</div>;

  if (!shouldCollapse) {
    return (
      <Alert
        type="info"
        showIcon
        message={`How to get credentials for ${platform}`}
        description={body}
      />
    );
  }

  return (
    <Collapse
      size="small"
      items={[{
        key: '1',
        label: <span style={{ color: '#1677ff' }}>How to get credentials for {platform}</span>,
        children: body,
      }]}
    />
  );
}
