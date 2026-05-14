package dev.autotix.interfaces.desk.dto;

/**
 * REST DTO for a file attachment.
 */
public class AttachmentDTO {
    public Long id;
    public String key;
    public String fileName;
    public String contentType;
    public long sizeBytes;
    public String uploadedBy;
    public String uploadedAt;   // ISO-8601
    public String downloadUrl;
}
