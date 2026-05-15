import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  AutoComplete,
  Avatar,
  Badge,
  Button,
  Card,
  DatePicker,
  Divider,
  Dropdown,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Segmented,
  Spin,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import {
  CloseOutlined,
  EllipsisOutlined,
  HistoryOutlined,
  LeftOutlined,
  LoadingOutlined,
  MailOutlined,
  MessageOutlined,
  PaperClipOutlined,
  PlusOutlined,
  RightOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from 'umi';
import type { TicketDTO, AttachmentDTO, TicketActivity } from '@/services/ticket';
import { listTicketActivity } from '@/services/ticket';
import {
  closeTicket,
  escalateTicket,
  getTicket,
  listTickets,
  markSpam,
  replyTicket,
  resumeAi,
  solveTicket,
  updateTicketTags,
  updateTicketCustomField,
  uploadAttachment,
} from '@/services/ticket';
import type { DraftDTO, StyleHint } from '@/services/ai';
import { generateAIDraft } from '@/services/ai';
import { subscribeInbox } from '@/services/inbox';
import type { CustomerDetailDTO } from '@/services/customer';
import { getTagSuggestions } from '@/services/tag';
import { getCustomFieldSchema } from '@/services/customfield';
import type { TagDTO } from '@/services/tag';
import type { CustomFieldDTO } from '@/services/customfield';
import { getAccessToken, getCurrentUser, hasRole } from '@/utils/auth';

const { Text, Link } = Typography;

type SmartView = 'mine' | 'unassigned' | 'open' | 'needs_human' | 'all';
type StatusFilter = 'open' | 'solved' | 'closed' | 'spam' | 'all';

// ──────────────────────────────────────────────
// Helper: relative time
// ──────────────────────────────────────────────
function relativeTime(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.floor(hours / 24)}d`;
}

// ──────────────────────────────────────────────
// Helper: derive role + style tokens for message
// ──────────────────────────────────────────────
type MessageRole = 'CUSTOMER' | 'AI' | 'AGENT' | 'INTERNAL';
function messageRole(
  direction: 'INBOUND' | 'OUTBOUND',
  author: string,
  visibility?: string,
): MessageRole {
  if (visibility === 'INTERNAL') return 'INTERNAL';
  if (direction === 'INBOUND') return 'CUSTOMER';
  if (author.toLowerCase() === 'ai' || author.toLowerCase().startsWith('ai')) return 'AI';
  return 'AGENT';
}
const ROLE_STYLES: Record<MessageRole, { bar: string; bg: string; chip: string; chipFg: string; label: string }> = {
  CUSTOMER: { bar: '#9BAAB8', bg: '#F7F9FB', chip: '#EEF2F6', chipFg: '#5A6B7D', label: 'Customer' },
  AI:       { bar: '#2962FF', bg: '#EFF6FF', chip: '#2962FF', chipFg: '#FFFFFF', label: 'AI' },
  AGENT:    { bar: '#16A34A', bg: '#F0FDF4', chip: '#16A34A', chipFg: '#FFFFFF', label: 'Agent' },
  INTERNAL: { bar: '#F59E0B', bg: '#FFFBEB', chip: '#FEF3C7', chipFg: '#92400E', label: 'Internal Note' },
};
function messageBarColor(
  direction: 'INBOUND' | 'OUTBOUND',
  author: string,
  visibility?: string,
): string {
  if (visibility === 'INTERNAL') return '#F59E0B';
  if (direction === 'INBOUND') return '#9BAAB8';
  if (author.toLowerCase().includes('ai') || author.toLowerCase() === 'ai') return '#2962FF';
  return '#16A34A';
}

// ──────────────────────────────────────────────
// Helper: platform icon label
// ──────────────────────────────────────────────
function platformLabel(platform: string): string {
  const map: Record<string, string> = {
    EMAIL: 'Email',
    CHAT: 'Chat',
    LINE: 'LINE',
    WECOM: 'WeCom',
    ZENDESK: 'Zendesk',
    INSTAGRAM: 'Instagram',
    WHATSAPP: 'WhatsApp',
  };
  return map[platform] || platform;
}

// ──────────────────────────────────────────────
// Client-side smart view filter
// TODO (slice 17): move to server-side with proper query params
// ──────────────────────────────────────────────
function applySmartView(tickets: TicketDTO[], view: SmartView, currentUserId?: string): TicketDTO[] {
  switch (view) {
    case 'mine':
      return tickets.filter((t) => t.assigneeId === currentUserId);
    case 'unassigned':
      return tickets.filter(
        (t) =>
          !t.assigneeId &&
          !['SOLVED', 'CLOSED', 'SPAM'].includes(t.status),
      );
    case 'open':
      return tickets.filter(
        (t) =>
          ['NEW', 'OPEN', 'WAITING_ON_CUSTOMER', 'WAITING_ON_INTERNAL'].includes(t.status) &&
          !t.aiSuspended,
      );
    case 'needs_human':
      return tickets.filter((t) => t.aiSuspended || t.slaBreached);
    case 'all':
    default:
      return tickets;
  }
}

function applyStatusFilter(tickets: TicketDTO[], filter: StatusFilter): TicketDTO[] {
  if (filter === 'all') return tickets;
  const map: Record<StatusFilter, string[]> = {
    open: ['NEW', 'OPEN', 'WAITING_ON_CUSTOMER', 'WAITING_ON_INTERNAL'],
    solved: ['SOLVED'],
    closed: ['CLOSED'],
    spam: ['SPAM'],
    all: [],
  };
  const statuses = map[filter];
  return tickets.filter((t) => statuses.includes(t.status));
}

// ──────────────────────────────────────────────
// Left column: ticket list item
// ──────────────────────────────────────────────
interface TicketRowProps {
  ticket: TicketDTO;
  selected: boolean;
  unread: boolean;
  tagColorMap: Record<string, string>;
  onClick: () => void;
}

function TicketRow({ ticket, selected, unread, tagColorMap, onClick }: TicketRowProps) {
  return (
    <div
      onClick={onClick}
      style={{
        height: 80,
        padding: '10px 12px',
        cursor: 'pointer',
        borderBottom: '1px solid #EEF2F6',
        background: selected ? '#F7F9FB' : '#FFFFFF',
        borderLeft: selected ? '3px solid #2962FF' : '3px solid transparent',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        gap: 4,
        boxSizing: 'border-box',
      }}
    >
      {/* Row 1: unread dot + subject + time */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: unread ? '#2962FF' : 'transparent',
            border: unread ? 'none' : '1.5px solid #9BAAB8',
            flexShrink: 0,
          }}
        />
        <Text
          style={{
            fontWeight: unread ? 600 : 400,
            fontSize: 13,
            flex: 1,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            color: '#0B1426',
          }}
          title={ticket.subject}
        >
          {ticket.subject}
        </Text>
        <Text style={{ fontSize: 11, color: '#9BAAB8', flexShrink: 0 }}>
          {relativeTime(ticket.updatedAt)}
        </Text>
      </div>
      {/* Row 2: customer + platform */}
      <Text style={{ fontSize: 12, color: '#5A6B7D', paddingLeft: 14 }}>
        {ticket.customerName || ticket.customerIdentifier} · {platformLabel(ticket.platform)}
      </Text>
      {/* Row 3: tags + priority + SLA badge */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, paddingLeft: 14, flexWrap: 'wrap' }}>
        {ticket.tags.slice(0, 2).map((tag) => (
          <Tag
            key={tag}
            style={{
              fontSize: 11,
              lineHeight: '16px',
              padding: '0 4px',
              margin: 0,
              background: tagColorMap[tag] ? `${tagColorMap[tag]}22` : '#EEF2F6',
              color: tagColorMap[tag] || '#5A6B7D',
              border: 'none',
            }}
          >
            {tag}
          </Tag>
        ))}
        {ticket.priority && ticket.priority !== 'NORMAL' && (
          <Tag style={{ fontSize: 11, lineHeight: '16px', padding: '0 4px', margin: 0, border: 'none' }}>
            {ticket.priority.toLowerCase()}
          </Tag>
        )}
        {ticket.slaBreached && (
          <Tag
            color="error"
            style={{ fontSize: 11, lineHeight: '16px', padding: '0 4px', margin: 0 }}
          >
            SLA!
          </Tag>
        )}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────
// Main component
// ──────────────────────────────────────────────
export default function InboxPage() {
  const currentUser = getCurrentUser();
  const isAdmin = hasRole('ADMIN');

  // ── List state ────────────────────────────────
  const [currentView, setCurrentView] = useState<SmartView>('mine');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [allTickets, setAllTickets] = useState<TicketDTO[]>([]);
  const [loadingList, setLoadingList] = useState(false);

  // ── Selection state ───────────────────────────
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [currentTicket, setCurrentTicket] = useState<TicketDTO | null>(null);
  const [loadingTicket, setLoadingTicket] = useState(false);
  const [viewedTicketIds] = useState<Set<string>>(new Set());

  // ── Right panel state ─────────────────────────
  type RailPane = 'properties' | 'ai' | 'activity' | null;
  const [activeRail, setActiveRail] = useState<RailPane>('properties');
  const [activity, setActivity] = useState<TicketActivity[]>([]);
  const [activityLoading, setActivityLoading] = useState(false);

  // Fetch activity log when activity rail is opened or ticket changes
  useEffect(() => {
    if (activeRail !== 'activity' || !currentTicket?.id) {
      return;
    }
    setActivityLoading(true);
    listTicketActivity(currentTicket.id, 0, 100)
      .then(setActivity)
      .catch(() => setActivity([]))
      .finally(() => setActivityLoading(false));
  }, [activeRail, currentTicket?.id]);
  const rightPanelOpen = activeRail !== null;
  const setRightPanelOpen = (_v: boolean | ((p: boolean) => boolean)) => {
    // legacy compat — collapsing closes pane, expanding opens default
    if (typeof _v === 'function') {
      const next = _v(rightPanelOpen);
      setActiveRail(next ? 'properties' : null);
    } else {
      setActiveRail(_v ? 'properties' : null);
    }
  };
  const [tagDefs, setTagDefs] = useState<TagDTO[]>([]);
  const [customFieldSchema, setCustomFieldSchema] = useState<CustomFieldDTO[]>([]);

  // ── Reply box state ───────────────────────────
  const [replyContent, setReplyContent] = useState('');
  const [internalNote, setInternalNote] = useState(false);
  const [pendingAttachments, setPendingAttachments] = useState<AttachmentDTO[]>([]);
  const [sendingReply, setSendingReply] = useState(false);

  // ── Escalate modal state ──────────────────────
  const [escalateVisible, setEscalateVisible] = useState(false);
  const [escalateReason, setEscalateReason] = useState('');

  // ── AI Draft state ────────────────────────────
  type DraftState = 'idle' | 'loading' | 'ready' | 'error';
  const [draftState, setDraftState] = useState<DraftState>('idle');
  const [draft, setDraft] = useState<DraftDTO | null>(null);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draftStyleHint, setDraftStyleHint] = useState<StyleHint>('DEFAULT');

  // ── Tag input state ───────────────────────────
  const [tagInputValue, setTagInputValue] = useState('');

  // ── Custom field save pending ─────────────────
  const [savingField, setSavingField] = useState<Record<string, boolean>>({});

  // ── Refresh debounce ref ──────────────────────
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const messageContainerRef = useRef<HTMLDivElement>(null);

  // ──────────────────────────────────────────────
  // Fetch ticket list
  // ──────────────────────────────────────────────
  const fetchTickets = useCallback(async () => {
    setLoadingList(true);
    try {
      // TODO (slice 17): replace with server-side smart view filtering
      const tickets = await listTickets({ limit: 200 });
      setAllTickets(tickets);
    } catch (e) {
      // silently ignore network errors in v1
    } finally {
      setLoadingList(false);
    }
  }, []);

  // ──────────────────────────────────────────────
  // Fetch single ticket detail
  // ──────────────────────────────────────────────
  const fetchTicketDetail = useCallback(async (ticketId: string) => {
    setLoadingTicket(true);
    try {
      const t = await getTicket(ticketId);
      setCurrentTicket(t);
      // Auto-clear draft when ticket changes
      setDraftState('idle');
      setDraft(null);
      setDraftError(null);
    } catch {
      // ignore
    } finally {
      setLoadingTicket(false);
    }
  }, []);

  // ──────────────────────────────────────────────
  // Mount: initial fetch + SSE subscription + periodic refresh
  // ──────────────────────────────────────────────
  useEffect(() => {
    fetchTickets();

    // Load tag definitions for color map
    getTagSuggestions()
      .then(setTagDefs)
      .catch(() => {});

    // Load custom field schema
    getCustomFieldSchema('TICKET')
      .then(setCustomFieldSchema)
      .catch(() => {});

    const token = getAccessToken();
    if (!token) return;

    // intentionally no-op — activity fetch is wired in a dedicated effect below

    const unsubscribe = subscribeInbox(token, (event) => {
      const refreshEvents = ['TICKET_CREATED', 'STATUS_CHANGED', 'AI_REPLIED', 'AGENT_REPLIED'];
      if (refreshEvents.includes(event.kind)) {
        // Debounced re-fetch
        if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
        refreshTimerRef.current = setTimeout(() => {
          fetchTickets();
          // Refresh current ticket if it's the one that changed
          if (selectedTicketId && event.ticketId === selectedTicketId) {
            fetchTicketDetail(selectedTicketId);
          }
        }, 500);

        // Toast for AI replies on tickets not currently viewing
        if (event.kind === 'AI_REPLIED' && event.ticketId !== selectedTicketId) {
          message.info(`AI replied to ticket ${event.ticketId}`, 3);
        }
      }
    });

    // Periodic refresh every 30s
    const interval = setInterval(fetchTickets, 30000);

    return () => {
      unsubscribe();
      clearInterval(interval);
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    };
  }, [fetchTickets, fetchTicketDetail, selectedTicketId]);

  // ──────────────────────────────────────────────
  // Derived: filtered ticket list
  // ──────────────────────────────────────────────
  const filteredByView = applySmartView(allTickets, currentView, currentUser?.id);
  const displayedTickets = applyStatusFilter(filteredByView, statusFilter);

  // ──────────────────────────────────────────────
  // Tag color map
  // ──────────────────────────────────────────────
  const tagColorMap: Record<string, string> = {};
  tagDefs.forEach((td) => {
    tagColorMap[td.name] = td.color;
  });

  // ──────────────────────────────────────────────
  // Tab counts
  // ──────────────────────────────────────────────
  const tabCounts: Record<SmartView, number> = {
    mine: applySmartView(allTickets, 'mine', currentUser?.id).length,
    unassigned: applySmartView(allTickets, 'unassigned', currentUser?.id).length,
    open: applySmartView(allTickets, 'open', currentUser?.id).length,
    needs_human: applySmartView(allTickets, 'needs_human', currentUser?.id).length,
    all: allTickets.length,
  };

  // ──────────────────────────────────────────────
  // Select ticket
  // ──────────────────────────────────────────────
  function handleSelectTicket(ticketId: string) {
    viewedTicketIds.add(ticketId);
    setSelectedTicketId(ticketId);
    fetchTicketDetail(ticketId);
    // Update URL for shareable link without full navigation
    history.replace(`/inbox?ticket=${ticketId}`);
  }

  // ──────────────────────────────────────────────
  // Reply
  // ──────────────────────────────────────────────
  async function handleSendReply(closeAfter = false) {
    if (!currentTicket || !replyContent.trim()) return;
    setSendingReply(true);
    try {
      const attachmentIds = pendingAttachments.map((a) => a.id);
      await replyTicket(
        currentTicket.id,
        replyContent,
        closeAfter,
        internalNote,
        attachmentIds.length > 0 ? attachmentIds : undefined,
      );
      setReplyContent('');
      setInternalNote(false);
      setPendingAttachments([]);
      await fetchTicketDetail(currentTicket.id);
      await fetchTickets();
    } catch {
      message.error('Failed to send reply');
    } finally {
      setSendingReply(false);
    }
  }

  async function handleSolveAndSend() {
    if (!currentTicket || !replyContent.trim()) return;
    setSendingReply(true);
    try {
      await replyTicket(currentTicket.id, replyContent, false, internalNote);
      await solveTicket(currentTicket.id);
      setReplyContent('');
      setInternalNote(false);
      await fetchTicketDetail(currentTicket.id);
      await fetchTickets();
    } catch {
      message.error('Failed to send and solve');
    } finally {
      setSendingReply(false);
    }
  }

  // ──────────────────────────────────────────────
  // Escalate
  // ──────────────────────────────────────────────
  async function handleEscalate() {
    if (!currentTicket) return;
    try {
      await escalateTicket(currentTicket.id, escalateReason);
      setEscalateVisible(false);
      setEscalateReason('');
      await fetchTicketDetail(currentTicket.id);
      message.success('Ticket escalated to human');
    } catch {
      message.error('Failed to escalate ticket');
    }
  }

  async function handleResumeAi() {
    if (!currentTicket) return;
    try {
      await resumeAi(currentTicket.id);
      await fetchTicketDetail(currentTicket.id);
      message.success('AI resumed for this ticket');
    } catch {
      message.error('Failed to resume AI');
    }
  }

  // ──────────────────────────────────────────────
  // AI Draft
  // ──────────────────────────────────────────────
  async function handleGenerateDraft() {
    if (!currentTicket) return;
    setDraftState('loading');
    setDraftError(null);
    try {
      const d = await generateAIDraft(currentTicket.id, draftStyleHint);
      setDraft(d);
      setDraftState('ready');
    } catch (e: any) {
      setDraftError(e?.message || 'AI generation failed');
      setDraftState('error');
    }
  }

  async function handleUseDraft() {
    if (!currentTicket || !draft) return;
    setSendingReply(true);
    try {
      await replyTicket(currentTicket.id, draft.reply, false, false);
      message.success('Reply sent');
      setDraftState('idle');
      setDraft(null);
      await fetchTicketDetail(currentTicket.id);
      await fetchTickets();
    } catch {
      message.error('Failed to send reply');
    } finally {
      setSendingReply(false);
    }
  }

  function handleEditAndUseDraft() {
    if (!draft) return;
    setReplyContent(draft.reply);
    setDraftState('idle');
    setDraft(null);
    // Focus reply textarea (best-effort)
  }

  // ──────────────────────────────────────────────
  // Tag editing
  // ──────────────────────────────────────────────
  async function handleAddTag(tagName: string) {
    if (!currentTicket || !tagName.trim()) return;
    const name = tagName.trim();
    try {
      await updateTicketTags(currentTicket.id, [name], []);
      setTagInputValue('');
      await fetchTicketDetail(currentTicket.id);
      // Also refresh tag definitions
      getTagSuggestions().then(setTagDefs).catch(() => {});
    } catch {
      message.error('Failed to add tag');
    }
  }

  async function handleRemoveTag(tagName: string) {
    if (!currentTicket) return;
    try {
      await updateTicketTags(currentTicket.id, [], [tagName]);
      await fetchTicketDetail(currentTicket.id);
    } catch {
      message.error('Failed to remove tag');
    }
  }

  // ──────────────────────────────────────────────
  // Custom field editing
  // ──────────────────────────────────────────────
  async function handleSaveCustomField(key: string, value: string | null) {
    if (!currentTicket) return;
    setSavingField((prev) => ({ ...prev, [key]: true }));
    try {
      await updateTicketCustomField(currentTicket.id, key, value);
      await fetchTicketDetail(currentTicket.id);
    } catch {
      message.error('Failed to save custom field');
    } finally {
      setSavingField((prev) => ({ ...prev, [key]: false }));
    }
  }

  // ──────────────────────────────────────────────
  // Middle column "More" dropdown
  // ──────────────────────────────────────────────
  const moreMenuItems = [
    {
      key: 'escalate',
      label: 'Escalate to human',
      onClick: () => setEscalateVisible(true),
      disabled: currentTicket?.aiSuspended,
    },
    {
      key: 'close',
      label: 'Permanently Close',
      onClick: () => {
        if (!currentTicket) return;
        Modal.confirm({
          title: 'Permanently close this ticket?',
          content: 'This action cannot be undone.',
          okText: 'Close',
          okButtonProps: { danger: true },
          onOk: async () => {
            try {
              await closeTicket(currentTicket.id);
              await fetchTicketDetail(currentTicket.id);
              await fetchTickets();
            } catch {
              message.error('Failed to close ticket');
            }
          },
        });
      },
    },
    {
      key: 'spam',
      label: 'Mark spam',
      onClick: () => {
        if (!currentTicket) return;
        Modal.confirm({
          title: 'Mark this ticket as spam?',
          okText: 'Mark spam',
          okButtonProps: { danger: true },
          onOk: async () => {
            try {
              await markSpam(currentTicket.id);
              await fetchTicketDetail(currentTicket.id);
              await fetchTickets();
              message.success('Ticket marked as spam');
            } catch {
              message.error('Failed to mark ticket as spam');
            }
          },
        });
      },
    },
    ...(isAdmin && currentTicket?.aiSuspended
      ? [
          {
            key: 'resume-ai',
            label: 'Resume AI',
            onClick: () => {
              Modal.confirm({
                title: 'Resume AI for this ticket?',
                onOk: handleResumeAi,
              });
            },
          },
        ]
      : []),
  ];

  // ──────────────────────────────────────────────
  // Scroll to bottom when messages change
  // ──────────────────────────────────────────────
  useEffect(() => {
    if (messageContainerRef.current) {
      messageContainerRef.current.scrollTop = messageContainerRef.current.scrollHeight;
    }
  }, [currentTicket?.messages]);

  // ──────────────────────────────────────────────
  // Render
  // ──────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 48px)', overflow: 'hidden' }}>
      {/* ── Left column: smart views + ticket list ── */}
      <div
        style={{
          width: 300,
          background: '#FFFFFF',
          borderRight: '1px solid #EEF2F6',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
          overflow: 'hidden',
        }}
      >
        {/* Smart view selector (dropdown) */}
        <div
          style={{
            padding: '8px 12px',
            borderBottom: '1px solid #EEF2F6',
          }}
        >
          <Select
            value={currentView}
            onChange={(v) => setCurrentView(v as SmartView)}
            style={{ width: '100%' }}
            size="middle"
            options={([
              { key: 'mine',        label: 'Mine',        count: tabCounts.mine },
              { key: 'unassigned',  label: 'Unassigned',  count: tabCounts.unassigned },
              { key: 'open',        label: 'Open',        count: tabCounts.open },
              { key: 'needs_human', label: 'Needs human', count: tabCounts.needs_human },
              { key: 'all',         label: 'All',         count: tabCounts.all },
            ] as Array<{ key: SmartView; label: string; count: number }>).map((v) => ({
              value: v.key,
              label: (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>{v.label}</span>
                  {v.count > 0 && (
                    <Badge
                      count={v.count}
                      size="small"
                      style={{
                        background: currentView === v.key ? '#2962FF' : '#9BAAB8',
                        boxShadow: 'none',
                      }}
                    />
                  )}
                </div>
              ),
            }))}
          />
        </div>

        {/* Secondary status filter */}
        <div style={{ padding: '8px 12px', borderBottom: '1px solid #EEF2F6' }}>
          <Segmented
            size="small"
            value={statusFilter}
            onChange={(v) => setStatusFilter(v as StatusFilter)}
            options={[
              { label: 'All', value: 'all' },
              { label: 'Open', value: 'open' },
              { label: 'Solved', value: 'solved' },
              { label: 'Closed', value: 'closed' },
              { label: 'Spam', value: 'spam' },
            ]}
            style={{ width: '100%' }}
            block
          />
        </div>

        {/* Ticket list */}
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {loadingList && (
            <div style={{ padding: 16, color: '#9BAAB8', fontSize: 12, textAlign: 'center' }}>
              Loading…
            </div>
          )}
          {!loadingList && displayedTickets.length === 0 && (
            <div style={{ padding: 24, color: '#9BAAB8', fontSize: 12, textAlign: 'center' }}>
              No tickets in this view
            </div>
          )}
          {displayedTickets.map((ticket) => (
            <TicketRow
              key={ticket.id}
              ticket={ticket}
              selected={selectedTicketId === ticket.id}
              unread={!viewedTicketIds.has(ticket.id)}
              tagColorMap={tagColorMap}
              onClick={() => handleSelectTicket(ticket.id)}
            />
          ))}
        </div>
      </div>

      {/* ── Middle column: conversation thread ── */}
      <div
        style={{
          flex: 1,
          background: '#FFFFFF',
          display: 'flex',
          flexDirection: 'column',
          minWidth: 0,
          overflow: 'hidden',
        }}
      >
        {!selectedTicketId ? (
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexDirection: 'column',
              gap: 8,
              color: '#9BAAB8',
            }}
          >
            <MailOutlined style={{ fontSize: 40 }} />
            <Text style={{ color: '#9BAAB8' }}>Select a ticket from the list</Text>
          </div>
        ) : (
          <>
            {/* Header bar */}
            {currentTicket && (
              <div
                style={{
                  height: 64,
                  padding: '0 16px',
                  borderBottom: '1px solid #EEF2F6',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  flexShrink: 0,
                  background: '#FFFFFF',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      flexWrap: 'wrap',
                    }}
                  >
                    <Text
                      style={{
                        fontWeight: 600,
                        fontSize: 14,
                        color: '#0B1426',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        maxWidth: 300,
                      }}
                    >
                      {currentTicket.subject}
                    </Text>
                    <Tag style={{ fontSize: 11 }}>{platformLabel(currentTicket.platform)}</Tag>
                    <Tag
                      color={
                        currentTicket.status === 'SOLVED' || currentTicket.status === 'CLOSED'
                          ? 'success'
                          : currentTicket.status === 'SPAM'
                          ? 'error'
                          : 'processing'
                      }
                      style={{ fontSize: 11 }}
                    >
                      {currentTicket.status}
                    </Tag>
                    {currentTicket.priority && currentTicket.priority !== 'NORMAL' && (
                      <Tag style={{ fontSize: 11 }}>{currentTicket.priority}</Tag>
                    )}
                    {currentTicket.aiSuspended && (
                      <Tag color="warning" style={{ fontSize: 11 }}>
                        AI suspended
                      </Tag>
                    )}
                  </div>
                  <Text style={{ fontSize: 12, color: '#5A6B7D' }}>
                    {currentTicket.customerName || currentTicket.customerIdentifier}
                    {currentTicket.customerIdentifier &&
                      currentTicket.customerName &&
                      ` · ${currentTicket.customerIdentifier}`}
                  </Text>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Dropdown menu={{ items: moreMenuItems }} trigger={['click']}>
                    <Button icon={<EllipsisOutlined />} size="small">
                      More
                    </Button>
                  </Dropdown>
                </div>
              </div>
            )}

            {/* Message thread */}
            <div
              ref={messageContainerRef}
              style={{
                flex: 1,
                overflowY: 'auto',
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
            >
              {loadingTicket && (
                <div style={{ textAlign: 'center', color: '#9BAAB8', fontSize: 12 }}>Loading…</div>
              )}
              {currentTicket?.messages?.map((msg, idx) => {
                const role = messageRole(msg.direction, msg.author, msg.visibility);
                const s = ROLE_STYLES[role];
                return (
                  <div
                    key={idx}
                    style={{
                      borderRadius: 8,
                      border: `1px solid ${s.bg === '#FFFFFF' ? '#EEF2F6' : s.bg}`,
                      background: s.bg,
                      overflow: 'hidden',
                      display: 'flex',
                    }}
                  >
                    {/* 6px left color bar */}
                    <div
                      style={{
                        width: 6,
                        flexShrink: 0,
                        background: s.bar,
                      }}
                    />
                    <div style={{ flex: 1, padding: '10px 14px' }}>
                      {/* Header: role chip + author + time */}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          marginBottom: 6,
                        }}
                      >
                        <Tag
                          style={{
                            fontSize: 11,
                            background: s.chip,
                            color: s.chipFg,
                            border: 'none',
                            padding: '0 6px',
                            lineHeight: '18px',
                            margin: 0,
                            fontWeight: 600,
                          }}
                        >
                          {s.label}
                        </Tag>
                        <Text style={{ fontWeight: 500, fontSize: 12, color: '#0B1426' }}>
                          {msg.author}
                        </Text>
                        <Text style={{ fontSize: 11, color: '#9BAAB8', marginLeft: 'auto' }}>
                          {new Date(msg.occurredAt).toLocaleString()}
                        </Text>
                      </div>
                      {/* Content */}
                      <div
                        style={{
                          fontSize: 13,
                          color: '#0B1426',
                          whiteSpace: 'pre-wrap',
                          lineHeight: 1.6,
                        }}
                      >
                        {msg.content}
                      </div>
                      {/* Attachments */}
                      {msg.attachments && msg.attachments.length > 0 && (
                        <div
                          style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}
                        >
                          {msg.attachments.map((att) => (
                            <a
                              key={att.id}
                              href={att.downloadUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                                padding: '2px 8px',
                                background: '#EEF2F6',
                                borderRadius: 4,
                                fontSize: 11,
                                color: '#2962FF',
                                textDecoration: 'none',
                              }}
                            >
                              <PaperClipOutlined />
                              {att.fileName}
                            </a>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
              {(!currentTicket?.messages || currentTicket.messages.length === 0) &&
                !loadingTicket && (
                  <div style={{ textAlign: 'center', color: '#9BAAB8', fontSize: 12, marginTop: 32 }}>
                    No messages yet
                  </div>
                )}
            </div>

            {/* Reply box footer */}
            {currentTicket && (
              <div
                style={{
                  borderTop: '1px solid #EEF2F6',
                  padding: '12px 16px',
                  background: '#FFFFFF',
                  flexShrink: 0,
                }}
              >
                <Input.TextArea
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  placeholder={internalNote ? 'Type internal note...' : 'Type your reply...'}
                  autoSize={{ minRows: 3, maxRows: 8 }}
                  style={{
                    borderRadius: 6,
                    fontSize: 13,
                    background: internalNote ? '#FFFBEB' : undefined,
                  }}
                />
                {/* Pending attachments */}
                {pendingAttachments.length > 0 && (
                  <div
                    style={{
                      marginTop: 8,
                      display: 'flex',
                      gap: 6,
                      flexWrap: 'wrap',
                    }}
                  >
                    {pendingAttachments.map((att) => (
                      <span
                        key={att.id}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                          padding: '2px 8px',
                          background: '#EEF2F6',
                          borderRadius: 4,
                          fontSize: 11,
                        }}
                      >
                        <PaperClipOutlined />
                        {att.fileName}
                        <CloseOutlined
                          style={{ cursor: 'pointer', fontSize: 10 }}
                          onClick={() =>
                            setPendingAttachments((prev) =>
                              prev.filter((a) => a.id !== att.id),
                            )
                          }
                        />
                      </span>
                    ))}
                  </div>
                )}
                {/* Toolbar */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    marginTop: 8,
                  }}
                >
                  {/* Upload button */}
                  <Upload
                    showUploadList={false}
                    customRequest={async ({ file, onSuccess, onError }) => {
                      try {
                        const att = await uploadAttachment(
                          currentTicket.id,
                          file as File,
                        );
                        setPendingAttachments((prev) => [...prev, att]);
                        onSuccess?.({});
                      } catch (e) {
                        onError?.(e as Error);
                      }
                    }}
                  >
                    <Button icon={<PaperClipOutlined />} size="small" type="text" />
                  </Upload>

                  {/* Internal note toggle */}
                  <label
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                      fontSize: 12,
                      color: '#5A6B7D',
                      cursor: 'pointer',
                      userSelect: 'none',
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={internalNote}
                      onChange={(e) => setInternalNote(e.target.checked)}
                    />
                    Internal note
                  </label>

                  {/* Spacer */}
                  <div style={{ flex: 1 }} />

                  {/* Send button */}
                  <Button
                    size="small"
                    onClick={() => handleSendReply(false)}
                    loading={sendingReply}
                    disabled={!replyContent.trim()}
                  >
                    Send
                  </Button>

                  {/* Solve & Send split button */}
                  <Dropdown
                    menu={{
                      items: [
                        {
                          key: 'solve',
                          label: 'Send and solve',
                          onClick: handleSolveAndSend,
                        },
                        {
                          key: 'close',
                          label: 'Send and close',
                          onClick: () => handleSendReply(true),
                        },
                      ],
                    }}
                  >
                    <Button
                      type="primary"
                      size="small"
                      loading={sendingReply}
                      disabled={!replyContent.trim()}
                      onClick={handleSolveAndSend}
                    >
                      Solve &amp; Send ▾
                    </Button>
                  </Dropdown>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* ── Right column: content pane (collapsible) ── */}
      <div
        style={{
          width: rightPanelOpen ? 320 : 0,
          transition: 'width 0.18s ease',
          overflow: 'hidden',
          flexShrink: 0,
          position: 'relative',
        }}
      >

        <div
          style={{
            width: 320,
            height: '100%',
            borderLeft: '1px solid #EEF2F6',
            background: '#FFFFFF',
            overflowY: 'auto',
            padding: '16px 12px',
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            gap: 0,
          }}
        >
          {!currentTicket ? (
            <Text style={{ color: '#9BAAB8', fontSize: 12 }}>No ticket selected</Text>
          ) : (
            <>
              {activeRail === 'properties' && (
              <>
              {/* Customer section */}
              <div>
                <Text style={{ fontSize: 12, fontWeight: 600, color: '#5A6B7D', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Customer
                </Text>
                <Divider style={{ margin: '6px 0' }} />
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Avatar
                      size={28}
                      icon={<UserOutlined />}
                      style={{ background: '#EEF2F6', color: '#5A6B7D', flexShrink: 0 }}
                    />
                    <div style={{ minWidth: 0 }}>
                      <Text style={{ fontSize: 13, fontWeight: 600, color: '#0B1426', display: 'block' }}>
                        {currentTicket.customer?.displayName ||
                          currentTicket.customerName ||
                          currentTicket.customerIdentifier ||
                          'Unknown'}
                      </Text>
                      {(currentTicket.customer?.primaryEmail || currentTicket.customerIdentifier) && (
                        <Link
                          href={`mailto:${currentTicket.customer?.primaryEmail || currentTicket.customerIdentifier}`}
                          style={{ fontSize: 12 }}
                        >
                          {currentTicket.customer?.primaryEmail || currentTicket.customerIdentifier}
                        </Link>
                      )}
                    </div>
                  </div>
                  {currentTicket.customer && (
                    <Text style={{ fontSize: 12, color: '#5A6B7D' }}>
                      {currentTicket.customer.identifierCount} channel{currentTicket.customer.identifierCount !== 1 ? 's' : ''}
                    </Text>
                  )}
                </div>

                {/* Customer ticket history */}
                {currentTicket.recentTicketSummary && currentTicket.recentTicketSummary.length > 0 && (
                  <div style={{ marginTop: 10 }}>
                    <Text style={{ fontSize: 12, color: '#5A6B7D' }}>
                      History · {currentTicket.recentTicketSummary.length} ticket{currentTicket.recentTicketSummary.length !== 1 ? 's' : ''}
                    </Text>
                    <div style={{ marginTop: 4, display: 'flex', flexDirection: 'column', gap: 2 }}>
                      {currentTicket.recentTicketSummary.map((ts) => (
                        <div
                          key={ts.id}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                            cursor: 'pointer',
                            padding: '2px 0',
                          }}
                          onClick={() => handleSelectTicket(ts.id)}
                        >
                          <span
                            style={{
                              width: 8,
                              height: 8,
                              borderRadius: '50%',
                              background: ts.status === 'SOLVED' || ts.status === 'CLOSED' ? '#16A34A' : '#9BAAB8',
                              flexShrink: 0,
                            }}
                          />
                          <Text style={{ fontSize: 12, color: '#2962FF', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {ts.subject}
                          </Text>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <div style={{ height: 16 }} />
              <Divider style={{ margin: 0 }} />
              <div style={{ height: 16 }} />

              {/* Tags section */}
              <div>
                <Text style={{ fontSize: 12, fontWeight: 600, color: '#5A6B7D', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Tags
                </Text>
                <Divider style={{ margin: '6px 0' }} />
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 6 }}>
                  {currentTicket.tags.length === 0 && (
                    <Text style={{ fontSize: 12, color: '#9BAAB8' }}>No tags</Text>
                  )}
                  {currentTicket.tags.map((tag) => (
                    <Tag
                      key={tag}
                      closable
                      onClose={(e) => {
                        e.preventDefault();
                        handleRemoveTag(tag);
                      }}
                      style={{
                        fontSize: 12,
                        background: tagColorMap[tag] ? `${tagColorMap[tag]}22` : '#EEF2F6',
                        color: tagColorMap[tag] || '#5A6B7D',
                        border: 'none',
                      }}
                    >
                      {tag}
                    </Tag>
                  ))}
                </div>
                <AutoComplete
                  style={{ width: '100%' }}
                  placeholder="Add tag…"
                  options={tagDefs
                    .filter((t) => !currentTicket.tags.includes(t.name))
                    .map((t) => ({ value: t.name, label: t.name }))}
                  size="small"
                  value={tagInputValue}
                  onChange={(v) => setTagInputValue(v)}
                  onSelect={(v) => handleAddTag(v)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && tagInputValue.trim()) {
                      handleAddTag(tagInputValue);
                    }
                  }}
                />
              </div>

              <div style={{ height: 16 }} />
              <Divider style={{ margin: 0 }} />
              <div style={{ height: 16 }} />

              {/* Custom Fields section */}
              <div>
                <Text style={{ fontSize: 12, fontWeight: 600, color: '#5A6B7D', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Custom Fields
                </Text>
                <Divider style={{ margin: '6px 0' }} />
                {customFieldSchema.length === 0 ? (
                  <Text style={{ fontSize: 12, color: '#9BAAB8' }}>No custom fields defined</Text>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {customFieldSchema.map((field) => {
                      const currentVal = currentTicket.customFields?.[field.key] ?? '';
                      const isSaving = savingField[field.key];
                      return (
                        <div key={field.id} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                          <Text style={{ fontSize: 11, color: '#5A6B7D' }}>{field.name}</Text>
                          <div style={{ position: 'relative' }}>
                            {field.type === 'NUMBER' ? (
                              <InputNumber
                                size="small"
                                style={{ width: '100%', fontSize: 12 }}
                                value={currentVal ? Number(currentVal) : undefined}
                                onBlur={(e) => handleSaveCustomField(field.key, e.target.value || null)}
                                placeholder="—"
                              />
                            ) : field.type === 'DATE' ? (
                              <Input
                                size="small"
                                style={{ fontSize: 12 }}
                                value={currentVal}
                                placeholder="YYYY-MM-DD"
                                onBlur={(e) => handleSaveCustomField(field.key, e.target.value || null)}
                              />
                            ) : (
                              <Input
                                size="small"
                                style={{ fontSize: 12 }}
                                defaultValue={currentVal}
                                key={`${field.key}-${currentVal}`}
                                placeholder="—"
                                onBlur={(e) => handleSaveCustomField(field.key, e.target.value || null)}
                              />
                            )}
                            {isSaving && (
                              <span style={{ position: 'absolute', right: 4, top: '50%', transform: 'translateY(-50%)' }}>
                                <LoadingOutlined style={{ fontSize: 11, color: '#9BAAB8' }} />
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              </>
              )}

              {activeRail === 'ai' && (
              <>
              {/* AI Draft section */}
              <Card
                size="small"
                title={<span style={{ fontSize: 12, fontWeight: 600 }}>AI Draft</span>}
                style={{ border: '1px solid #EEF2F6' }}
                styles={{ header: { minHeight: 36, padding: '0 12px' }, body: { padding: '8px 12px' } }}
              >
                {currentTicket.aiSuspended ? (
                  <Text style={{ fontSize: 12, color: '#9BAAB8' }}>
                    AI suspended. Resume from ⋯ More menu (admin only).
                  </Text>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {draftState === 'idle' && (
                      <>
                        <Select
                          size="small"
                          value={draftStyleHint}
                          onChange={(v) => setDraftStyleHint(v as StyleHint)}
                          options={[
                            { value: 'DEFAULT', label: 'Default style' },
                            { value: 'FRIENDLIER', label: 'Friendlier' },
                            { value: 'FORMAL', label: 'Formal' },
                            { value: 'SHORTER', label: 'Shorter' },
                          ]}
                          style={{ width: '100%' }}
                        />
                        <Button
                          size="small"
                          type="primary"
                          onClick={handleGenerateDraft}
                          block
                        >
                          Generate AI draft
                        </Button>
                      </>
                    )}
                    {draftState === 'loading' && (
                      <div style={{ textAlign: 'center', padding: '8px 0' }}>
                        <Spin indicator={<LoadingOutlined />} />
                        <div style={{ marginTop: 4 }}>
                          <Button
                            size="small"
                            onClick={() => setDraftState('idle')}
                          >
                            Cancel
                          </Button>
                        </div>
                      </div>
                    )}
                    {draftState === 'ready' && draft && (
                      <>
                        <div style={{ fontSize: 11, color: '#9BAAB8' }}>
                          {draft.latencyMs < 2000 ? '⚡ fast' : '⏱'} · {draft.modelName}
                        </div>
                        <div
                          style={{
                            fontSize: 12,
                            color: '#0B1426',
                            background: '#F7F9FB',
                            borderRadius: 6,
                            padding: '8px 10px',
                            whiteSpace: 'pre-wrap',
                            lineHeight: 1.5,
                            maxHeight: 160,
                            overflowY: 'auto',
                          }}
                        >
                          {draft.reply}
                        </div>
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                          <Button
                            size="small"
                            type="primary"
                            onClick={handleUseDraft}
                            loading={sendingReply}
                          >
                            Use this
                          </Button>
                          <Button size="small" onClick={handleEditAndUseDraft}>
                            Edit &amp; use
                          </Button>
                          <Button size="small" onClick={handleGenerateDraft}>
                            Regenerate
                          </Button>
                        </div>
                      </>
                    )}
                    {draftState === 'error' && (
                      <>
                        <Alert
                          type="error"
                          message={draftError || 'AI generation failed'}
                          style={{ fontSize: 11 }}
                        />
                        <Button size="small" onClick={handleGenerateDraft} block>
                          Retry
                        </Button>
                      </>
                    )}
                  </div>
                )}
              </Card>
              </>
              )}

              {activeRail === 'activity' && (
                <div>
                  <Text style={{ fontSize: 12, fontWeight: 600, color: '#5A6B7D', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                    Activity
                  </Text>
                  <Divider style={{ margin: '6px 0' }} />
                  {activityLoading ? (
                    <Text style={{ fontSize: 12, color: '#9BAAB8' }}>Loading…</Text>
                  ) : activity.length === 0 ? (
                    <Text style={{ fontSize: 12, color: '#9BAAB8' }}>No activity yet.</Text>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {activity.map((a) => (
                        <div
                          key={a.id}
                          style={{
                            paddingLeft: 10,
                            borderLeft: '2px solid #EEF2F6',
                          }}
                        >
                          <div style={{ fontSize: 12, fontWeight: 600, color: '#0B1426' }}>
                            {a.action.replaceAll('_', ' ')}
                          </div>
                          <div style={{ fontSize: 11, color: '#5A6B7D' }}>
                            by <span style={{ fontWeight: 500 }}>{a.actor}</span> · {new Date(a.occurredAt).toLocaleString()}
                          </div>
                          {a.details && (
                            <div
                              style={{
                                fontSize: 11,
                                color: '#5A6B7D',
                                marginTop: 2,
                                fontFamily: 'monospace',
                                wordBreak: 'break-all',
                              }}
                            >
                              {a.details}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* ── Right icon rail (always visible) ── */}
      <div
        style={{
          width: 48,
          flexShrink: 0,
          borderLeft: '1px solid #EEF2F6',
          background: '#FFFFFF',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          padding: '8px 0',
          gap: 4,
        }}
      >
        {([
          { key: 'properties', icon: <UserOutlined />,    label: 'Customer / tags / fields' },
          { key: 'ai',         icon: <RobotOutlined />,   label: 'AI Draft' },
          { key: 'activity',   icon: <HistoryOutlined />, label: 'Activity (coming soon)' },
        ] as Array<{ key: RailPane; icon: React.ReactNode; label: string }>).map((it) => {
          const isActive = activeRail === it.key;
          return (
            <Tooltip key={it.key ?? 'x'} title={it.label} placement="left">
              <button
                onClick={() => setActiveRail(isActive ? null : it.key)}
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 8,
                  border: 'none',
                  background: isActive ? '#EFF6FF' : 'transparent',
                  color: isActive ? '#2962FF' : '#5A6B7D',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 16,
                }}
              >
                {it.icon}
              </button>
            </Tooltip>
          );
        })}
        <div style={{ flex: 1 }} />
        <Tooltip title="More modules coming" placement="left">
          <button
            style={{
              width: 36, height: 36, borderRadius: 8, border: 'none',
              background: 'transparent', color: '#9BAAB8', cursor: 'not-allowed',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14,
            }}
            disabled
          >
            <PlusOutlined />
          </button>
        </Tooltip>
      </div>

      {/* Escalate modal */}
      <Modal
        title="Escalate to human"
        open={escalateVisible}
        onOk={handleEscalate}
        onCancel={() => {
          setEscalateVisible(false);
          setEscalateReason('');
        }}
        okText="Escalate"
        okButtonProps={{ danger: false }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <Text style={{ fontSize: 13 }}>
            AI will be suspended for this ticket. A human agent will handle further replies.
          </Text>
          <Input.TextArea
            placeholder="Reason (optional)"
            value={escalateReason}
            onChange={(e) => setEscalateReason(e.target.value)}
            autoSize={{ minRows: 2 }}
          />
        </div>
      </Modal>
    </div>
  );
}
