package dev.autotix.interfaces.desk.dto;

import java.util.List;

/**
 * Payload for human agent reply.
 *  - content: markdown (server will format per channel)
 *  - closeAfter: optional, close ticket after sending
 */
public class ReplyRequest {
    public String content;
    public boolean closeAfter;
    /** Slice 9: if true, this is an internal note (not sent externally, no status change) */
    public boolean internal;
    /** Slice 11: IDs of pre-uploaded attachments to link to this reply */
    public List<Long> attachmentIds;
}
