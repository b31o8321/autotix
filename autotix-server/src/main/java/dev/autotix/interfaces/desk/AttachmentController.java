package dev.autotix.interfaces.desk;

import dev.autotix.application.attachment.UploadAttachmentUseCase;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.interfaces.desk.dto.AttachmentDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Attachment upload endpoint.
 * Auth: JWT (enforced by SecurityConfig — ROLE_AGENT or ROLE_ADMIN via /api/desk/**).
 *
 * POST /api/desk/tickets/{ticketId}/attachments  — upload file; returns AttachmentDTO
 */
@RestController
@RequestMapping("/api/desk/tickets")
public class AttachmentController {

    private final UploadAttachmentUseCase uploadUseCase;

    public AttachmentController(UploadAttachmentUseCase uploadUseCase) {
        this.uploadUseCase = uploadUseCase;
    }

    @PostMapping("/{ticketId}/attachments")
    public ResponseEntity<AttachmentDTO> upload(
            @PathVariable String ticketId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String uploader = authentication != null ? authentication.getName() : "agent";
        AttachmentDTO dto = uploadUseCase.upload(
                new TicketId(ticketId),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                uploader);
        return ResponseEntity.ok(dto);
    }
}
