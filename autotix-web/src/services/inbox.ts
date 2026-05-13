// TODO: SSE client for /api/inbox/stream.
//   - Construct EventSource with `?token=<accessToken>` (CORS-safe auth)
//   - Listen for named events: TICKET_CREATED / AI_REPLIED / AGENT_REPLIED / STATUS_CHANGED / ASSIGNED
//   - Reconnect with exponential backoff on error
//   - Expose subscribe(handler) -> unsubscribe
export interface InboxEvent {
  kind: 'TICKET_CREATED' | 'AI_REPLIED' | 'AGENT_REPLIED' | 'STATUS_CHANGED' | 'ASSIGNED';
  ticketId: string;
  channelId: string;
  summary: string;
  occurredAt: string;
}

export type InboxHandler = (event: InboxEvent) => void;

export function subscribeInbox(token: string, handler: InboxHandler): () => void {
  // TODO: implement using EventSource
  //   const es = new EventSource(`/api/inbox/stream?token=${encodeURIComponent(token)}`);
  //   ['TICKET_CREATED','AI_REPLIED','AGENT_REPLIED','STATUS_CHANGED','ASSIGNED']
  //     .forEach(k => es.addEventListener(k, (e: MessageEvent) => handler(JSON.parse(e.data))));
  //   es.onerror = () => { /* reconnect handled by EventSource */ };
  //   return () => es.close();
  throw new Error('TODO');
}
