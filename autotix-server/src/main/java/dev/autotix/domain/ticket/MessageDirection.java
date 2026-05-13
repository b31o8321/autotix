package dev.autotix.domain.ticket;

/**
 * TODO: Direction of a message within a ticket conversation.
 *  INBOUND  = from customer (received via webhook)
 *  OUTBOUND = from us (AI reply or human agent reply)
 */
public enum MessageDirection {
    INBOUND,
    OUTBOUND
}
