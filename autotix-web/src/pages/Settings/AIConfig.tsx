import { useEffect, useState, useRef } from 'react';
import {
  Form, Input, InputNumber, Button, Card, Space, message, Alert, Typography, Spin,
} from 'antd';
import { getAIConfig, updateAIConfig, testAIConfig, type AIConfigDTO, type AITestResult } from '@/services/ai';

const { TextArea } = Input;
const { Text } = Typography;

const MASKED_KEY_PATTERN = /^sk-\*+/;

export default function AIConfigPage() {
  const [form] = Form.useForm<AIConfigDTO>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<AITestResult | null>(null);
  const originalApiKey = useRef<string>('');

  useEffect(() => {
    setLoading(true);
    getAIConfig()
      .then((cfg) => {
        originalApiKey.current = cfg.apiKey;
        form.setFieldsValue(cfg);
      })
      .catch(() => message.error('Failed to load AI config'))
      .finally(() => setLoading(false));
  }, [form]);

  async function handleSave() {
    const values = await form.validateFields();
    // Don't send masked key back; only send if user typed a new one
    const apiKey = MASKED_KEY_PATTERN.test(values.apiKey)
      ? originalApiKey.current
      : values.apiKey;
    setSaving(true);
    try {
      await updateAIConfig({ ...values, apiKey });
      message.success('AI config saved');
    } catch {
      message.error('Failed to save AI config');
    } finally {
      setSaving(false);
    }
  }

  async function handleTest() {
    const values = await form.validateFields();
    const apiKey = MASKED_KEY_PATTERN.test(values.apiKey)
      ? originalApiKey.current
      : values.apiKey;
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testAIConfig({ ...values, apiKey });
      setTestResult(result);
    } catch {
      message.error('Test failed');
    } finally {
      setTesting(false);
    }
  }

  if (loading) return <Spin size="large" />;

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>AI Configuration</Typography.Title>

      <Card>
        <Form<AIConfigDTO> form={form} layout="vertical">
          <Form.Item name="endpoint" label="Endpoint" rules={[{ required: true }]}>
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item name="apiKey" label="API Key" rules={[{ required: true }]}>
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item name="model" label="Model" rules={[{ required: true }]}>
            <Input placeholder="gpt-4o" />
          </Form.Item>
          <Form.Item name="systemPrompt" label="System Prompt" rules={[{ required: true }]}>
            <TextArea rows={6} placeholder="You are a helpful customer support agent..." />
          </Form.Item>
          <Form.Item name="timeoutSeconds" label="Timeout (seconds)" rules={[{ required: true }]}>
            <InputNumber min={1} max={120} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="maxRetries" label="Max Retries" rules={[{ required: true }]}>
            <InputNumber min={0} max={10} style={{ width: 120 }} />
          </Form.Item>

          <Space>
            <Button type="primary" onClick={handleSave} loading={saving}>Save</Button>
            <Button onClick={handleTest} loading={testing}>Test Connection</Button>
          </Space>
        </Form>
      </Card>

      {testResult && (
        <Alert
          type={testResult.ok ? 'success' : 'error'}
          message={
            testResult.ok
              ? `Connection OK — ${testResult.latencyMs}ms`
              : `Connection failed: ${testResult.error}`
          }
          description={
            testResult.sampleReply ? (
              <Space direction="vertical">
                <Text strong>Sample reply:</Text>
                <Text>{testResult.sampleReply}</Text>
              </Space>
            ) : undefined
          }
          showIcon
        />
      )}
    </Space>
  );
}
