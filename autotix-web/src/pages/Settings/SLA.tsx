import { useEffect, useState } from 'react';
import {
  Table, Button, InputNumber, Switch, Space, Typography,
  message as antMessage, Spin, Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { listSlaPolicies, updateSlaPolicy, type SlaPolicyDTO } from '@/services/sla';

const { Title } = Typography;

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'default',
  NORMAL: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
};

export default function SLASettingsPage() {
  const [policies, setPolicies] = useState<SlaPolicyDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState<string | null>(null);
  const [edits, setEdits] = useState<Record<string, Partial<SlaPolicyDTO>>>({});

  async function fetchPolicies() {
    setLoading(true);
    try {
      const data = await listSlaPolicies();
      setPolicies(data);
    } catch {
      antMessage.error('Failed to load SLA policies');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchPolicies();
  }, []);

  function getEdit(priority: string): Partial<SlaPolicyDTO> {
    return edits[priority] || {};
  }

  function setEdit(priority: string, field: keyof SlaPolicyDTO, value: unknown) {
    setEdits((prev) => ({
      ...prev,
      [priority]: { ...prev[priority], [field]: value },
    }));
  }

  function getEffective(row: SlaPolicyDTO, field: keyof SlaPolicyDTO) {
    const edit = getEdit(row.priority);
    return field in edit ? edit[field] : row[field];
  }

  async function handleSave(priority: string) {
    const row = policies.find((p) => p.priority === priority);
    if (!row) return;
    const edit = getEdit(priority);
    const payload: Partial<SlaPolicyDTO> = {
      name: row.name,
      firstResponseMinutes: row.firstResponseMinutes,
      resolutionMinutes: row.resolutionMinutes,
      enabled: row.enabled,
      ...edit,
    };
    setSaving(priority);
    try {
      const updated = await updateSlaPolicy(priority, payload);
      setPolicies((prev) => prev.map((p) => (p.priority === priority ? updated : p)));
      setEdits((prev) => { const next = { ...prev }; delete next[priority]; return next; });
      antMessage.success(`${priority} SLA policy saved`);
    } catch {
      antMessage.error('Failed to save SLA policy');
    } finally {
      setSaving(null);
    }
  }

  const columns: ColumnsType<SlaPolicyDTO> = [
    {
      title: 'Priority',
      dataIndex: 'priority',
      key: 'priority',
      render: (p: string) => <Tag color={PRIORITY_COLORS[p] || 'default'}>{p}</Tag>,
      width: 100,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'First Response (min)',
      key: 'firstResponseMinutes',
      render: (_: unknown, row: SlaPolicyDTO) => (
        <InputNumber
          min={1}
          value={getEffective(row, 'firstResponseMinutes') as number}
          onChange={(v) => v != null && setEdit(row.priority, 'firstResponseMinutes', v)}
          style={{ width: 100 }}
        />
      ),
    },
    {
      title: 'Resolution (min)',
      key: 'resolutionMinutes',
      render: (_: unknown, row: SlaPolicyDTO) => (
        <InputNumber
          min={1}
          value={getEffective(row, 'resolutionMinutes') as number}
          onChange={(v) => v != null && setEdit(row.priority, 'resolutionMinutes', v)}
          style={{ width: 100 }}
        />
      ),
    },
    {
      title: 'Enabled',
      key: 'enabled',
      render: (_: unknown, row: SlaPolicyDTO) => (
        <Switch
          checked={getEffective(row, 'enabled') as boolean}
          onChange={(v) => setEdit(row.priority, 'enabled', v)}
        />
      ),
      width: 80,
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, row: SlaPolicyDTO) => (
        <Button
          type="primary"
          size="small"
          loading={saving === row.priority}
          onClick={() => handleSave(row.priority)}
        >
          Save
        </Button>
      ),
      width: 80,
    },
  ];

  if (loading) return <Spin size="large" />;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Title level={4}>SLA Policies</Title>
      <Table<SlaPolicyDTO>
        rowKey="priority"
        columns={columns}
        dataSource={policies}
        pagination={false}
        bordered
      />
    </Space>
  );
}
