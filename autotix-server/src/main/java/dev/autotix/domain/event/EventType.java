package dev.autotix.domain.event;

/**
 * TODO: Standardized webhook event types.
 *  NEW_TICKET     — new ticket created on platform side
 *  NEW_MESSAGE    — new inbound message on existing ticket
 *  STATUS_CHANGE  — platform closed/reopened — sync to autotix
 *  AGENT_REPLY    — human agent replied on platform side (sync back)
 *  IGNORED        — payload received but irrelevant (filter early)
 */
public enum EventType {
    NEW_TICKET,
    NEW_MESSAGE,
    STATUS_CHANGE,
    AGENT_REPLY,
    IGNORED
}
