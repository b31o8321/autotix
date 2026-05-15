import { useEffect, useState, useRef } from 'react';
import {
  Form, Input, InputNumber, Button, Card, Space, message, Alert, Typography, Spin, Select,
} from 'antd';
import { getAIConfig, updateAIConfig, testAIConfig, type AIConfigDTO, type AITestResult } from '@/services/ai';

const { TextArea } = Input;
const { Text } = Typography;

const MASKED_KEY_PATTERN = /^sk-\*+/;

type Provider = 'ollama' | 'cloud';

const DEFAULT_SYSTEM_PROMPT = `You are a professional customer support assistant.

Guidelines:
- Reply in the customer's language with a polite, concise tone (under 200 words).
- Acknowledge the issue first, then give a clear answer or next step.
- Use accurate information only; if uncertain, ask one clarifying question instead of guessing.
- For sensitive matters (refunds, account access, abuse, legal), escalate to a human agent.
- Avoid making promises about timelines or outcomes you cannot guarantee.

Output:
- For straightforward replies, output plain text.
- For replies that should trigger an action, output JSON: {"reply": "...", "action": "CLOSE|ASSIGN|TAG|NONE", "tags": ["..."]}.`;

const PROVIDER_STORAGE_KEY = 'autotix.ai.provider';

// Detect provider from saved endpoint + a localStorage hint (so refresh keeps the user's choice).
function inferProvider(cfg: { endpoint?: string; apiKey?: string | null }): Provider {
  const fromStorage = localStorage.getItem(PROVIDER_STORAGE_KEY) as Provider | null;
  if (fromStorage === 'ollama' || fromStorage === 'cloud') return fromStorage;
  const ep = (cfg.endpoint ?? '').toLowerCase();
  if (ep.includes('11434') || ep.includes('ollama') || ep.includes('localhost') || ep.includes('host.docker.internal')) {
    return 'ollama';
  }
  return 'cloud';
}

export default function AIConfigPage() {
  const [form] = Form.useForm<AIConfigDTO>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<AITestResult | null>(null);
  const [provider, setProvider] = useState<Provider>('cloud');
  const originalApiKey = useRef<string>('');

  useEffect(() => {
    setLoading(true);
    getAIConfig()
      .then((cfg) => {
        originalApiKey.current = cfg.apiKey;
        setProvider(inferProvider(cfg));
        form.setFieldsValue({
          ...cfg,
          systemPrompt: cfg.systemPrompt || DEFAULT_SYSTEM_PROMPT,
        });
      })
      .catch(() => message.error('Failed to load AI config'))
      .finally(() => setLoading(false));
  }, [form]);

  function handleProviderChange(next: Provider) {
    setProvider(next);
    localStorage.setItem(PROVIDER_STORAGE_KEY, next);
    // Suggest endpoint defaults when switching
    const current = form.getFieldsValue();
    if (next === 'ollama') {
      form.setFieldsValue({
        endpoint: current.endpoint?.includes('ollama') || current.endpoint?.includes('11434')
          ? current.endpoint
          : 'http://host.docker.internal:11434/v1',
        apiKey: '',
        model: current.model || 'qwen2.5:7b',
      });
    } else {
      form.setFieldsValue({
        endpoint: current.endpoint && !current.endpoint.includes('11434')
          ? current.endpoint
          : 'https://api.openai.com/v1',
        model: current.model && current.model.startsWith('gpt') ? current.model : 'gpt-4o',
      });
    }
  }

  function buildPayload(values: AIConfigDTO): AIConfigDTO {
    const apiKey = provider === 'ollama'
      ? ''
      : MASKED_KEY_PATTERN.test(values.apiKey)
        ? originalApiKey.current
        : values.apiKey;
    return { ...values, apiKey };
  }

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateAIConfig(buildPayload(values));
      message.success('AI config saved');
    } catch {
      message.error('Failed to save AI config');
    } finally {
      setSaving(false);
    }
  }

  async function handleTest() {
    const values = await form.validateFields();
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testAIConfig(buildPayload(values));
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

      <Card>
        <Form<AIConfigDTO> form={form} layout="vertical">
          <Form.Item
            label="Provider"
            extra={provider === 'ollama'
              ? 'Ollama runs locally — only the endpoint is needed, no API key.'
              : 'Any OpenAI-compatible cloud (OpenAI / DeepSeek / Claude via proxy / etc.) — endpoint + API key required.'}
          >
            <Select
              value={provider}
              onChange={(v: Provider) => handleProviderChange(v)}
              style={{ maxWidth: 360 }}
              options={[
                { value: 'ollama', label: 'Local — Ollama (no API key)' },
                { value: 'cloud',  label: 'Cloud — OpenAI-compatible (API key required)' },
              ]}
            />
          </Form.Item>

          <Form.Item name="endpoint" label="Endpoint" rules={[{ required: true }]}>
            <Input
              placeholder={provider === 'ollama' ? 'http://host.docker.internal:11434/v1' : 'https://api.openai.com/v1'}
            />
          </Form.Item>

          {provider === 'cloud' && (
            <Form.Item name="apiKey" label="API Key" rules={[{ required: true }]}>
              <Input.Password placeholder="sk-..." />
            </Form.Item>
          )}

          <Form.Item name="model" label="Model" rules={[{ required: true }]}>
            <Input placeholder={provider === 'ollama' ? 'qwen2.5:7b' : 'gpt-4o'} />
          </Form.Item>

          <Form.Item name="systemPrompt" label="System Prompt" rules={[{ required: true }]}>
            <TextArea rows={12} />
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
