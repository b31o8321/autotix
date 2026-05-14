package dev.autotix.application.attachment;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.AttachmentRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import dev.autotix.interfaces.desk.dto.AttachmentDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Handles pre-upload of attachments before they are linked to a message.
 *
 * Key scheme: attachments/{yyyy}/{MM}/{ticketId}/{uuid}-{safeFileName}
 */
@Service
public class UploadAttachmentUseCase {

    private final StorageProvider storageProvider;
    private final AttachmentRepository attachmentRepository;

    @Value("${autotix.storage.max-upload-bytes:10485760}")
    private long maxUploadBytes;

    public UploadAttachmentUseCase(StorageProvider storageProvider,
                                   AttachmentRepository attachmentRepository) {
        this.storageProvider = storageProvider;
        this.attachmentRepository = attachmentRepository;
    }

    public AttachmentDTO upload(TicketId ticketId, String originalFileName,
                                String contentType, InputStream content,
                                long sizeBytes, String uploadedBy) {
        if (sizeBytes > maxUploadBytes) {
            throw new AutotixException.ValidationException(
                    "File too large: " + sizeBytes + " bytes (max " + maxUploadBytes + ")");
        }

        String safeFileName = (originalFileName != null ? originalFileName : "file")
                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
        Date now = new Date();
        String year = String.format("%tY", now);
        String month = String.format("%tm", now);
        String key = "attachments/" + year + "/" + month + "/" + ticketId.value()
                + "/" + UUID.randomUUID().toString() + "-" + safeFileName;

        storageProvider.upload(key, content, sizeBytes, contentType);

        Instant uploadedAt = Instant.now();
        Attachment attachment = new Attachment(
                null, null, ticketId, key,
                originalFileName != null ? originalFileName : safeFileName,
                contentType != null ? contentType : "application/octet-stream",
                sizeBytes, uploadedBy, uploadedAt);

        Long id = attachmentRepository.save(attachment);
        attachment.setId(id);

        return toDTO(attachment);
    }

    /** Convert Attachment to DTO, generating a fresh download URL. */
    public AttachmentDTO toDTO(Attachment attachment) {
        String downloadUrl = buildDownloadUrl(attachment.key());
        AttachmentDTO dto = new AttachmentDTO();
        dto.id = attachment.id();
        dto.key = attachment.key();
        dto.fileName = attachment.fileName();
        dto.contentType = attachment.contentType();
        dto.sizeBytes = attachment.sizeBytes();
        dto.uploadedBy = attachment.uploadedBy();
        dto.uploadedAt = attachment.uploadedAt() != null ? attachment.uploadedAt().toString() : null;
        dto.downloadUrl = downloadUrl;
        return dto;
    }

    private String buildDownloadUrl(String key) {
        try {
            URL signed = storageProvider.presignedGet(key, Duration.ofMinutes(60));
            // For local storage, presignedGet returns http://localhost/api/files?key=...
            // Strip the scheme+host so the browser uses a relative path.
            String urlStr = signed.toString();
            if ("localhost".equals(signed.getHost())) {
                // Return just path+query
                String path = signed.getPath();
                String query = signed.getQuery();
                return query != null ? path + "?" + query : path;
            }
            return urlStr;
        } catch (Exception e) {
            return "/api/files?key=" + key;
        }
    }
}
