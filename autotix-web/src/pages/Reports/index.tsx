import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Result, Row, Spin, Statistic, Typography } from 'antd';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getReportsSummary, ReportsSummaryDTO } from '@/services/reports';

const { Title } = Typography;

// ── Platform colour palette ───────────────────────────────────────────────────
const PLATFORM_COLORS: Record<string, string> = {
  ZENDESK: '#03363d',
  SHOPIFY: '#96bf48',
  AMAZON: '#ff9900',
  FRESHDESK: '#25c16f',
  CUSTOM: '#6366f1',
  DEFAULT: '#8884d8',
};

function platformColor(platform?: string): string {
  return PLATFORM_COLORS[platform ?? ''] ?? PLATFORM_COLORS.DEFAULT;
}

// ── Formatters ────────────────────────────────────────────────────────────────

function formatFRT(seconds: number | null): string {
  if (seconds == null) return '—';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (m === 0) return `${s}s`;
  return `${m}m ${s}s`;
}

function formatSlaRate(pct: number): string {
  return `${pct.toFixed(1)}%`;
}

function mmdd(dateStr: string): string {
  // "2024-01-15" → "01-15"
  return dateStr.length >= 10 ? dateStr.slice(5) : dateStr;
}

// ── Empty state ───────────────────────────────────────────────────────────────
function EmptyChart() {
  return (
    <div style={{ textAlign: 'center', padding: 32, color: '#999' }}>
      No data yet
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export default function ReportsPage() {
  const [data, setData] = useState<ReportsSummaryDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getReportsSummary()
      .then(setData)
      .catch((e: Error) => setError(e?.message ?? 'Unknown error'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <Result
        status="error"
        title="Failed to load reports"
        subTitle={error ?? 'Unexpected error'}
        extra={
          <Button type="primary" onClick={load}>
            Retry
          </Button>
        }
      />
    );
  }

  // ── Derived ─────────────────────────────────────────────────────────────────
  const createdSeries = data.createdSeries.map((d) => ({ ...d, label: mmdd(d.date) }));
  const solvedSeries = data.solvedSeries.map((d) => ({ ...d, label: mmdd(d.date) }));
  const byChannel = data.byChannel.map((c) => ({
    name: c.displayName ?? c.channelId,
    count: c.openCount,
    platform: c.platform,
  }));
  const byAgent = data.byAgent.map((a) => ({
    name: a.displayName ?? a.agentId,
    count: a.solvedCount,
  }));

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 24 }}>
        Reports
      </Title>

      {/* ── Row 1: KPI cards ───────────────────────────────────────────────── */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="Open Tickets" value={data.openTickets} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="Solved Today" value={data.solvedToday} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Median First Response"
              value={formatFRT(data.medianFirstResponseSeconds)}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="SLA Breach Rate"
              value={formatSlaRate(data.slaBreachRatePct)}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
      </Row>

      {/* ── Row 2: Line charts ─────────────────────────────────────────────── */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="Tickets Created (last 14 days)">
            {createdSeries.every((d) => d.count === 0) ? (
              <EmptyChart />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={createdSeries}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Line
                    type="monotone"
                    dataKey="count"
                    name="Created"
                    stroke="#6366f1"
                    dot={false}
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Tickets Solved (last 14 days)">
            {solvedSeries.every((d) => d.count === 0) ? (
              <EmptyChart />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={solvedSeries}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Line
                    type="monotone"
                    dataKey="count"
                    name="Solved"
                    stroke="#22c55e"
                    dot={false}
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
      </Row>

      {/* ── Row 3: Bar charts ──────────────────────────────────────────────── */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="Open Tickets by Channel">
            {byChannel.length === 0 ? (
              <EmptyChart />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={byChannel}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="count" name="Open">
                    {byChannel.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={platformColor(entry.platform)} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Solved by Agent (last 30 days, top 10)">
            {byAgent.length === 0 ? (
              <EmptyChart />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={byAgent} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={100} />
                  <Tooltip />
                  <Bar dataKey="count" name="Solved" fill="#6366f1" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
