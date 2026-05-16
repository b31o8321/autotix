import { useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  createMacro,
  deleteMacro,
  listAdminMacros,
  updateMacro,
  type MacroDTO,
} from '@/services/macro';
import { hasRole } from '@/utils/auth';

const { Text } = Typography;

const AVAILABILITY_COLORS: Record<string, string> = {
  ADMIN_ONLY: 'red',
  AGENT: 'blue',
  AI: 'purple',
};

const AVAILABILITY_LABELS: Record<string, string> = {
  ADMIN_ONLY: 'Admin only',
  AGENT: 'Agent',
  AI: 'AI',
};

interface MacroFormValues {
  name: string;
  bodyMarkdown: string;
  category?: string;
  availableTo: string;
}

export default function MacrosPage() {
  const isAdmin = hasRole('ADMIN');

  const [macros, setMacros] = useState<MacroDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingMacro, setEditingMacro] = useState<MacroDTO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<MacroFormValues>();

  async function fetchMacros() {
    setLoading(true);
    try {
      const data = await listAdminMacros();
      setMacros(data);
    } catch {
      message.error('Failed to load macros');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (isAdmin) fetchMacros();
  }, [isAdmin]);

  function openCreate() {
    setEditingMacro(null);
    form.resetFields();
    form.setFieldsValue({ availableTo: 'AGENT' });
    setModalOpen(true);
  }

  function openEdit(macro: MacroDTO) {
    setEditingMacro(macro);
    form.setFieldsValue({
      name: macro.name,
      bodyMarkdown: macro.bodyMarkdown,
      category: macro.category,
      availableTo: macro.availableTo,
    });
    setModalOpen(true);
  }

  async function handleSubmit() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editingMacro) {
        await updateMacro(editingMacro.id, {
          name: values.name,
          bodyMarkdown: values.bodyMarkdown,
          category: values.category,
          availableTo: values.availableTo,
        });
        message.success('Macro updated');
      } else {
        await createMacro({
          name: values.name,
          bodyMarkdown: values.bodyMarkdown,
          category: values.category,
          availableTo: values.availableTo,
        });
        message.success('Macro created');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingMacro(null);
      await fetchMacros();
    } catch (e: any) {
      const status = e?.response?.status || e?.status;
      if (status === 409) {
        message.error('A macro with this name already exists');
      } else {
        message.error(editingMacro ? 'Failed to update macro' : 'Failed to create macro');
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(macro: MacroDTO) {
    Modal.confirm({
      title: `Delete macro "${macro.name}"?`,
      content: 'This action cannot be undone.',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMacro(macro.id);
          message.success('Macro deleted');
          await fetchMacros();
        } catch {
          message.error('Failed to delete macro');
        }
      },
    });
  }

  const columns: ColumnsType<MacroDTO> = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      render: (cat?: string) =>
        cat ? <Tag>{cat}</Tag> : <Text type="secondary">—</Text>,
    },
    {
      title: 'Available to',
      dataIndex: 'availableTo',
      key: 'availableTo',
      render: (v: string) => (
        <Tag color={AVAILABILITY_COLORS[v] || 'default'}>
          {AVAILABILITY_LABELS[v] || v}
        </Tag>
      ),
    },
    {
      title: 'Uses',
      dataIndex: 'usageCount',
      key: 'usageCount',
      sorter: (a, b) => b.usageCount - a.usageCount,
    },
    {
      title: 'Updated',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (v: string) => new Date(v).toLocaleDateString(),
      sorter: (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: MacroDTO) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            Edit
          </Button>
          <Button size="small" danger onClick={() => handleDelete(record)}>
            Delete
          </Button>
        </Space>
      ),
    },
  ];

  if (!isAdmin) {
    return (
      <div style={{ padding: 24 }}>
        <Text type="secondary">Admin access required to manage macros.</Text>
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          Macros
        </Typography.Title>
        <Button type="primary" onClick={openCreate}>
          Add macro
        </Button>
      </div>

      <Table
        rowKey="id"
        dataSource={macros}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={editingMacro ? 'Edit macro' : 'Add macro'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          setEditingMacro(null);
          form.resetFields();
        }}
        okText={editingMacro ? 'Save' : 'Create'}
        confirmLoading={submitting}
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="e.g. Shipping delay apology" />
          </Form.Item>
          <Form.Item name="category" label="Category">
            <Input placeholder="e.g. billing, shipping (optional)" />
          </Form.Item>
          <Form.Item
            name="availableTo"
            label="Available to"
            rules={[{ required: true, message: 'Select availability' }]}
          >
            <Select
              options={[
                { value: 'AGENT', label: 'Agent (all agents and admins)' },
                { value: 'ADMIN_ONLY', label: 'Admin only' },
                { value: 'AI', label: 'AI (also exposed for AI context)' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="bodyMarkdown"
            label="Body (Markdown)"
            rules={[{ required: true, message: 'Body is required' }]}
          >
            <Input.TextArea
              rows={8}
              placeholder="Enter the macro text in Markdown..."
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
