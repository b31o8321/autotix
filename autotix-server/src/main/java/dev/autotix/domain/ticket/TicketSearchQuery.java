package dev.autotix.domain.ticket;

import dev.autotix.domain.channel.ChannelId;

/**
 * TODO: Query value object for desk listing.
 *  - status filter (nullable = all)
 *  - channelId filter
 *  - assignee filter
 *  - text search across subject/messages
 *  - pagination: offset + limit
 */
public final class TicketSearchQuery {
    public TicketStatus status;
    public ChannelId channelId;
    public String assigneeId;
    public String text;
    public int offset;
    public int limit;
    // TODO: builder + validation
}
