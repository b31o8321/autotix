import { useEffect, useState } from 'react';
import {
  Alert, Breadcrumb, Button, Form, Input, Select, Space, Spin, Typography, message,
} from 'antd';
import { history, useParams } from 'umi';
import { getPlatforms, type PlatformDescriptorDTO } from '@/services/platform';
import { connectWithApiKey, startOAuth } from '@/services/channel';
import AuthFieldRenderer from '@/components/AuthFieldRenderer';
import EmailCredentialsForm from '@/components/EmailCredentialsForm';

export default function PlatformAddPage() {
  const { platform } = useParams<{ platform: string }>();
  const [descriptor, setDescriptor] = useState<PlatformDescriptorDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [oauthComingSoon, setOauthComingSoon] = useState(false);
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
      await connectWithApiKey({
        platform: platform!,
        channelType: values.channelType,
        displayName: values.displayName,
        credentials,
      });
      message.success('Channel connected');
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
      // Render app credentials form (client_id, client_secret, subdomain, etc.)
      // plus the Start OAuth button (in the parent submit area)
      return (
        <>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            Enter your {descriptor.displayName} app credentials, then click "Start OAuth" to
            authorize access.
          </Typography.Paragraph>
          <AuthFieldRenderer fields={descriptor.authFields} />
        </>
      );

    case 'API_KEY':
    case 'APP_CREDENTIALS':
    default:
      return <AuthFieldRenderer fields={descriptor.authFields} />;
  }
}
