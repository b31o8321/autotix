// reports REST client - matches ReportsController
import { request } from '@/utils/request';

export interface DateCountDTO {
  date: string; // yyyy-MM-dd
  count: number;
}

export interface ChannelOpenCountDTO {
  channelId: string;
  displayName: string;
  platform: string;
  openCount: number;
}

export interface AgentSolvedCountDTO {
  agentId: string;
  displayName: string;
  solvedCount: number;
}

export interface ReportsSummaryDTO {
  openTickets: number;
  solvedToday: number;
  medianFirstResponseSeconds: number | null;
  slaBreachRatePct: number;
  createdSeries: DateCountDTO[];
  solvedSeries: DateCountDTO[];
  byChannel: ChannelOpenCountDTO[];
  byAgent: AgentSolvedCountDTO[];
}

export function getReportsSummary(): Promise<ReportsSummaryDTO> {
  return request('/api/desk/reports/summary');
}
