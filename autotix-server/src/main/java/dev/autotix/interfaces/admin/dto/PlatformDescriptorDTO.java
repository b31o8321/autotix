package dev.autotix.interfaces.admin.dto;

import java.util.List;

/**
 * REST response DTO for /api/admin/platforms.
 * Mirrors PlatformDescriptor value object.
 */
public class PlatformDescriptorDTO {

    public String platform;
    public String displayName;
    public String category;
    public String defaultChannelType;
    public List<String> allowedChannelTypes;
    public String authMethod;
    public List<AuthFieldDTO> authFields;
    public boolean functional;
    public String docsUrl;

    public static class AuthFieldDTO {
        public String key;
        public String label;
        public String type;
        public List<String> options;
        public boolean required;
        public String placeholder;
        public String help;
        public String defaultValue;
    }
}
