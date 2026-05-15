import { useEffect, useState } from 'react';
import {
  Table, Button, Switch, Space, Modal, Form, Input, InputNumber,
  message, Popconfirm, Typography,
} from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  listRules, createRule, deleteRule, setRuleEnabled,
  type AutomationRuleDTO, type CreateRulePayload,
} from '@/services/automation';

interface RuleFormValues {
  name: string;
  priority: number;
  enabled: boolean;
  conditions: Array<{ field: string; op: string; value: string }>;
  actions: Array<{ type: string; paramKey: string; paramValue: string }>;
}

export default function AutomationPage() {
  const [rules, setRules] = useState<AutomationRuleDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();

  async function fetchRules() {
    setLoading(true);
    try {
      const data = await listRules();
      setRules(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchRules(); }, []);

  async function handleToggle(id: string, enabled: boolean) {
    try {
      await setRuleEnabled(id, enabled);
      setRules((prev) =>
        prev.map((r) => (r.id === id ? { ...r, enabled } : r)),
      );
    } catch {
      message.error('Failed to update rule');
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteRule(id);
      setRules((prev) => prev.filter((r) => r.id !== id));
      message.success('Rule deleted');
    } catch {
      message.error('Failed to delete rule');
    }
  }

  async function handleCreate() {
    const values = await form.validateFields();
    const payload: CreateRulePayload = {
      name: values.name,
      priority: values.priority,
      enabled: values.enabled ?? true,
      conditions: (values.conditions || []).map((c) => ({
        field: c.field,
        op: c.op,
        value: c.value,
      })),
      actions: (values.actions || []).map((a) => ({
        type: a.type,
        params: a.paramKey ? { [a.paramKey]: a.paramValue } : {},
      })),
    };
    setSubmitting(true);
    try {
      await createRule(payload);
      message.success('Rule created');
      setModalOpen(false);
      form.resetFields();
      await fetchRules();
    } catch {
      message.error('Failed to create rule');
    } finally {
      setSubmitting(false);
    }
  }

  const columns: ColumnsType<AutomationRuleDTO> = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Priority', dataIndex: 'priority', key: 'priority', width: 90 },
    {
      title: 'Enabled',
      key: 'enabled',
      width: 90,
      render: (_: unknown, r: AutomationRuleDTO) => (
        <Switch checked={r.enabled} onChange={(v) => handleToggle(r.id, v)} />
      ),
    },
    {
      title: 'Conditions',
      key: 'conditions',
      render: (_: unknown, r: AutomationRuleDTO) => r.conditions.length,
      width: 100,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, r: AutomationRuleDTO) => r.actions.length,
      width: 80,
    },
    {
      title: '',
      key: 'ops',
      render: (_: unknown, r: AutomationRuleDTO) => (
        <Popconfirm
          title="Delete this rule?"
          onConfirm={() => handleDelete(r.id)}
          okText="Yes"
          cancelText="No"
        >
          <Button size="small" danger>Delete</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <Button type="primary" onClick={() => setModalOpen(true)}>Add Rule</Button>
      </div>

      <Table<AutomationRuleDTO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rules}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="Add Automation Rule"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        onOk={handleCreate}
        confirmLoading={submitting}
        okText="Create"
        width={640}
      >
        <Form<RuleFormValues> form={form} layout="vertical" initialValues={{ priority: 10, enabled: true }}>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="priority" label="Priority">
            <InputNumber min={1} max={1000} style={{ width: 120 }} />
          </Form.Item>

          <Typography.Text strong>Conditions</Typography.Text>
          <Form.List name="conditions">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Space key={key} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
                    <Form.Item name={[name, 'field']} rules={[{ required: true }]}>
                      <Input placeholder="field (e.g. status)" style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item name={[name, 'op']} rules={[{ required: true }]}>
                      <Input placeholder="op (e.g. equals)" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item name={[name, 'value']} rules={[{ required: true }]}>
                      <Input placeholder="value" style={{ width: 120 }} />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(name)} />
                  </Space>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add()}
                  icon={<PlusOutlined />}
                  size="small"
                >
                  Add Condition
                </Button>
              </>
            )}
          </Form.List>

          <Typography.Text strong style={{ display: 'block', marginTop: 12 }}>Actions</Typography.Text>
          <Form.List name="actions">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Space key={key} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
                    <Form.Item name={[name, 'type']} rules={[{ required: true }]}>
                      <Input placeholder="type (e.g. ASSIGN)" style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item name={[name, 'paramKey']}>
                      <Input placeholder="param key" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item name={[name, 'paramValue']}>
                      <Input placeholder="param value" style={{ width: 120 }} />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(name)} />
                  </Space>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add()}
                  icon={<PlusOutlined />}
                  size="small"
                >
                  Add Action
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </Space>
  );
}
