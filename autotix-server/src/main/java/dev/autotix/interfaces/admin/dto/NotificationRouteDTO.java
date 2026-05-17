package dev.autotix.interfaces.admin.dto;

/**
 * DTO for notification route CRUD and test endpoints.
 */
public class NotificationRouteDTO {

    public Long id;
    public String name;
    /** e.g. SLA_BREACHED */
    public String eventKind;
    /** e.g. EMAIL | SLACK_WEBHOOK */
    public String channel;
    /**
     * Raw JSON config string.
     *   EMAIL:         {"to":["a@b.com"],"subjectTemplate":"..."}
     *   SLACK_WEBHOOK: {"webhookUrl":"https://...","messageTemplate":"..."}
     */
    public String configJson;
    public boolean enabled;
    public String createdAt;
    public String updatedAt;
}
