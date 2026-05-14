package dev.autotix.interfaces.admin;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.interfaces.admin.dto.SlaPolicyDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for SLA policy management.
 *
 * GET  /api/admin/sla         — list all policies (4 rows, one per priority)
 * PUT  /api/admin/sla/{priority} — upsert policy for the given priority
 */
@RestController
@RequestMapping("/api/admin/sla")
@PreAuthorize("hasRole('ADMIN')")
public class SlaPolicyAdminController {

    private final SlaPolicyRepository slaPolicyRepository;

    public SlaPolicyAdminController(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @GetMapping
    public List<SlaPolicyDTO> getAll() {
        return slaPolicyRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PutMapping("/{priority}")
    public SlaPolicyDTO upsert(@PathVariable String priority,
                               @RequestBody SlaPolicyDTO dto) {
        TicketPriority ticketPriority;
        try {
            ticketPriority = TicketPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid priority: " + priority);
        }

        if (dto.firstResponseMinutes <= 0) {
            throw new AutotixException.ValidationException("firstResponseMinutes must be > 0");
        }
        if (dto.resolutionMinutes <= 0) {
            throw new AutotixException.ValidationException("resolutionMinutes must be > 0");
        }

        Optional<SlaPolicy> existing = slaPolicyRepository.findByPriority(ticketPriority);
        SlaPolicy policy;
        if (existing.isPresent()) {
            policy = existing.get();
            policy.update(
                    dto.name != null && !dto.name.trim().isEmpty() ? dto.name : policy.name(),
                    dto.firstResponseMinutes,
                    dto.resolutionMinutes,
                    dto.enabled);
        } else {
            String name = dto.name != null && !dto.name.trim().isEmpty()
                    ? dto.name : ticketPriority.name() + " Priority SLA";
            policy = SlaPolicy.create(name, ticketPriority,
                    dto.firstResponseMinutes, dto.resolutionMinutes, dto.enabled);
        }

        slaPolicyRepository.save(policy);
        return toDTO(policy);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SlaPolicyDTO toDTO(SlaPolicy p) {
        SlaPolicyDTO dto = new SlaPolicyDTO();
        dto.id = p.id();
        dto.name = p.name();
        dto.priority = p.priority().name();
        dto.firstResponseMinutes = p.firstResponseMinutes();
        dto.resolutionMinutes = p.resolutionMinutes();
        dto.enabled = p.enabled();
        dto.createdAt = p.createdAt();
        dto.updatedAt = p.updatedAt();
        return dto;
    }
}
