package dev.autotix.domain.ai;

/**
 * TODO: Optional action returned by AI alongside the reply.
 *  CLOSE  — close ticket after sending reply
 *  ASSIGN — escalate to human queue
 *  TAG    — apply tags only (no close)
 *  NONE   — no follow-up action
 */
public enum AIAction {
    NONE,
    CLOSE,
    ASSIGN,
    TAG
}
