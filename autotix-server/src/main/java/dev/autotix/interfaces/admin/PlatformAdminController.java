package dev.autotix.interfaces.admin;

import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.platform.PluginRegistry;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import dev.autotix.interfaces.admin.dto.PlatformDescriptorDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin REST endpoint for platform metadata.
 * GET /api/admin/platforms — returns descriptors for all known PlatformTypes.
 * Auth: ROLE_ADMIN (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/platforms")
public class PlatformAdminController {

    private final PluginRegistry pluginRegistry;

    public PlatformAdminController(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @GetMapping
    public List<PlatformDescriptorDTO> list() {
        List<PlatformDescriptorDTO> result = new ArrayList<>();

        for (PlatformType platformType : PlatformType.values()) {
            PlatformDescriptor descriptor;
            try {
                TicketPlatformPlugin plugin = pluginRegistry.get(platformType);
                descriptor = plugin.descriptor();
            } catch (Exception e) {
                // No plugin registered — derive a default descriptor
                descriptor = defaultDescriptor(platformType);
            }
            result.add(toDTO(descriptor));
        }

        // Sort: functional first (alpha), then stubs (alpha)
        result.sort(Comparator
                .comparing((PlatformDescriptorDTO d) -> d.functional ? 0 : 1)
                .thenComparing(d -> d.displayName));

        return result;
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private PlatformDescriptorDTO toDTO(PlatformDescriptor d) {
        PlatformDescriptorDTO dto = new PlatformDescriptorDTO();
        dto.platform = d.platform.name();
        dto.displayName = d.displayName;
        dto.category = d.category;
        dto.defaultChannelType = d.defaultChannelType.name();
        dto.allowedChannelTypes = d.allowedChannelTypes.stream()
                .map(ChannelType::name)
                .collect(Collectors.toList());
        dto.authMethod = d.authMethod.name();
        dto.authFields = d.authFields.stream().map(this::toFieldDTO).collect(Collectors.toList());
        dto.functional = d.functional;
        dto.docsUrl = d.docsUrl;
        return dto;
    }

    private PlatformDescriptorDTO.AuthFieldDTO toFieldDTO(PlatformDescriptor.AuthField f) {
        PlatformDescriptorDTO.AuthFieldDTO dto = new PlatformDescriptorDTO.AuthFieldDTO();
        dto.key = f.key;
        dto.label = f.label;
        dto.type = f.type;
        dto.options = f.options;
        dto.required = f.required;
        dto.placeholder = f.placeholder;
        dto.help = f.help;
        dto.defaultValue = f.defaultValue;
        return dto;
    }

    /**
     * Derives a minimal default descriptor for PlatformTypes without a registered plugin.
     * Currently all PlatformTypes have plugins, but this is a safety fallback.
     */
    private PlatformDescriptor defaultDescriptor(PlatformType platformType) {
        String name = platformType.name();
        String displayName = name.charAt(0) + name.substring(1).toLowerCase().replace('_', ' ');
        return new PlatformDescriptor(
                platformType,
                displayName,
                "other",
                ChannelType.EMAIL,
                Collections.singletonList(ChannelType.EMAIL),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("apiKey", "API Key", "password", true)
                                .placeholder("Your API key")
                ),
                false,
                null
        );
    }
}
