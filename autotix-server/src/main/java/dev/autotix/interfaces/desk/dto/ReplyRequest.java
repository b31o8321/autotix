package dev.autotix.interfaces.desk.dto;

/**
 * TODO: Payload for human agent reply.
 *  - content: markdown (server will format per channel)
 *  - closeAfter: optional, close ticket after sending
 */
public class ReplyRequest {
    public String content;
    public boolean closeAfter;
}
