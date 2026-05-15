import { Form, Input, Select, Space, Typography } from 'antd';

/**
 * Structured IMAP/SMTP form fields for EMAIL platform channels.
 * Rendered inside a parent Form as nested credentials.* fields.
 */
export default function EmailCredentialsForm() {
  return (
    <>
      <Typography.Title level={5} style={{ marginTop: 4 }}>IMAP (incoming)</Typography.Title>
      <Form.Item label="IMAP host" name={['credentials', 'imap_host']} rules={[{ required: true }]}>
        <Input placeholder="imap.gmail.com" />
      </Form.Item>
      <Space style={{ width: '100%' }} size="middle">
        <Form.Item label="Port" name={['credentials', 'imap_port']} initialValue="993" rules={[{ required: true }]}>
          <Input style={{ width: 100 }} />
        </Form.Item>
        <Form.Item label="Use SSL" name={['credentials', 'imap_use_ssl']} initialValue="true">
          <Select style={{ width: 100 }} options={[{ label: 'Yes', value: 'true' }, { label: 'No', value: 'false' }]} />
        </Form.Item>
      </Space>
      <Form.Item label="IMAP user" name={['credentials', 'imap_user']} rules={[{ required: true }]}>
        <Input placeholder="you@example.com" />
      </Form.Item>
      <Form.Item label="IMAP password" name={['credentials', 'imap_password']} rules={[{ required: true }]}>
        <Input.Password placeholder="password" />
      </Form.Item>

      <Typography.Title level={5} style={{ marginTop: 4 }}>SMTP (outgoing)</Typography.Title>
      <Form.Item label="SMTP host" name={['credentials', 'smtp_host']} rules={[{ required: true }]}>
        <Input placeholder="smtp.gmail.com" />
      </Form.Item>
      <Space style={{ width: '100%' }} size="middle">
        <Form.Item label="Port" name={['credentials', 'smtp_port']} initialValue="587" rules={[{ required: true }]}>
          <Input style={{ width: 100 }} />
        </Form.Item>
        <Form.Item label="Use TLS" name={['credentials', 'smtp_use_tls']} initialValue="true">
          <Select style={{ width: 100 }} options={[{ label: 'Yes', value: 'true' }, { label: 'No', value: 'false' }]} />
        </Form.Item>
      </Space>
      <Form.Item label="SMTP user" name={['credentials', 'smtp_user']} rules={[{ required: true }]}>
        <Input placeholder="you@example.com" />
      </Form.Item>
      <Form.Item label="SMTP password" name={['credentials', 'smtp_password']} rules={[{ required: true }]}>
        <Input.Password placeholder="password" />
      </Form.Item>
      <Form.Item
        label="From address"
        name={['credentials', 'from_address']}
        rules={[{ required: true, type: 'email' }]}
      >
        <Input placeholder="support@example.com" />
      </Form.Item>
    </>
  );
}
