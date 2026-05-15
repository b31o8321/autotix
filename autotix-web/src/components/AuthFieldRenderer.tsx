import { Form, Input, InputNumber, Select, Switch } from 'antd';
import type { AuthFieldDTO } from '@/services/platform';

interface Props {
  fields: AuthFieldDTO[];
}

/**
 * Renders a dynamic list of form fields based on AuthFieldDTO schema from the backend.
 * Handles string / password / number / boolean / select input types.
 */
export default function AuthFieldRenderer({ fields }: Props) {
  return (
    <>
      {fields.map((field) => (
        <Form.Item
          key={field.key}
          name={['credentials', field.key]}
          label={field.label}
          rules={field.required ? [{ required: true, message: `${field.label} is required` }] : []}
          initialValue={field.defaultValue}
          extra={field.help}
        >
          {renderInput(field)}
        </Form.Item>
      ))}
    </>
  );
}

function renderInput(field: AuthFieldDTO) {
  switch (field.type) {
    case 'password':
      return <Input.Password placeholder={field.placeholder} />;
    case 'number':
      return <InputNumber style={{ width: '100%' }} placeholder={field.placeholder} />;
    case 'boolean':
      return (
        <Select
          options={[
            { label: 'Yes', value: 'true' },
            { label: 'No', value: 'false' },
          ]}
        />
      );
    case 'select':
      return (
        <Select
          options={(field.options ?? []).map((o) => ({ label: o, value: o }))}
          placeholder={field.placeholder}
        />
      );
    case 'string':
    default:
      return <Input placeholder={field.placeholder} />;
  }
}
