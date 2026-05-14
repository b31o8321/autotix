package dev.autotix.infrastructure.persistence.attachment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.AttachmentRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.persistence.attachment.mapper.AttachmentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MyBatis Plus implementation of AttachmentRepository.
 */
@Repository
public class AttachmentRepositoryImpl implements AttachmentRepository {

    private final AttachmentMapper mapper;

    public AttachmentRepositoryImpl(AttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(Attachment attachment) {
        AttachmentEntity entity = toEntity(attachment);
        entity.setId(null); // let AUTO_INCREMENT assign
        mapper.insert(entity);
        attachment.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public Optional<Attachment> findById(Long id) {
        AttachmentEntity entity = mapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<Attachment> findByMessageId(Long messageId) {
        QueryWrapper<AttachmentEntity> qw = new QueryWrapper<>();
        qw.eq("message_id", messageId).orderByAsc("id");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Attachment> findByTicketId(TicketId ticketId) {
        QueryWrapper<AttachmentEntity> qw = new QueryWrapper<>();
        qw.eq("ticket_id", Long.parseLong(ticketId.value())).orderByAsc("uploaded_at");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void linkToMessage(Long attachmentId, Long messageId) {
        UpdateWrapper<AttachmentEntity> uw = new UpdateWrapper<>();
        uw.eq("id", attachmentId).set("message_id", messageId);
        mapper.update(null, uw);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // -----------------------------------------------------------------------

    private AttachmentEntity toEntity(Attachment a) {
        AttachmentEntity e = new AttachmentEntity();
        e.setId(a.id());
        e.setMessageId(a.messageId());
        e.setTicketId(Long.parseLong(a.ticketId().value()));
        e.setStorageKey(a.key());
        e.setFileName(a.fileName());
        e.setContentType(a.contentType());
        e.setSizeBytes(a.sizeBytes());
        e.setUploadedBy(a.uploadedBy());
        e.setUploadedAt(a.uploadedAt());
        return e;
    }

    private Attachment toDomain(AttachmentEntity e) {
        Attachment a = new Attachment(
                e.getId(),
                e.getMessageId(),
                new TicketId(String.valueOf(e.getTicketId())),
                e.getStorageKey(),
                e.getFileName(),
                e.getContentType(),
                e.getSizeBytes() != null ? e.getSizeBytes() : 0L,
                e.getUploadedBy(),
                e.getUploadedAt()
        );
        return a;
    }
}
