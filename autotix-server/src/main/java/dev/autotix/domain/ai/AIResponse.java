package dev.autotix.domain.ai;

import java.util.List;

/**
 * TODO: Value object returned by AIReplyPort.
 *  - reply  : Markdown content (mandatory)
 *  - action : optional follow-up
 *  - tags   : optional tags to add to the ticket
 *
 *  Parsing rules (in infrastructure/ai):
 *    - If AI returned a JSON object, map directly
 *    - If AI returned plain text, wrap as {reply: text, action: NONE, tags: []}
 */
public final class AIResponse {

    private final String reply;
    private final AIAction action;
    private final List<String> tags;

    public AIResponse(String reply, AIAction action, List<String> tags) {
        // TODO: validate reply non-empty; null-safe defaults for action/tags
        this.reply = reply;
        this.action = action == null ? AIAction.NONE : action;
        this.tags = tags;
    }

    public String reply() { return reply; }
    public AIAction action() { return action; }
    public List<String> tags() { return tags; }
}
