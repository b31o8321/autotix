package dev.autotix.domain.channel;

import java.util.Collections;
import java.util.List;

/**
 * Describes a platform's identity, category, and auth requirements.
 * Used by the frontend to render the correct add-channel form.
 */
public final class PlatformDescriptor {

    public final PlatformType platform;
    public final String displayName;
    /** One of: "ticket" | "chat" | "ecommerce" | "email" | "test" | "other" */
    public final String category;
    public final ChannelType defaultChannelType;
    public final List<ChannelType> allowedChannelTypes;
    public final AuthMethod authMethod;
    public final List<AuthField> authFields;
    /** false for stub plugins that throw UnsupportedOperationException. */
    public final boolean functional;
    /** Optional documentation URL. */
    public final String docsUrl;

    public PlatformDescriptor(
            PlatformType platform,
            String displayName,
            String category,
            ChannelType defaultChannelType,
            List<ChannelType> allowedChannelTypes,
            AuthMethod authMethod,
            List<AuthField> authFields,
            boolean functional,
            String docsUrl) {
        this.platform = platform;
        this.displayName = displayName;
        this.category = category;
        this.defaultChannelType = defaultChannelType;
        this.allowedChannelTypes = Collections.unmodifiableList(allowedChannelTypes);
        this.authMethod = authMethod;
        this.authFields = Collections.unmodifiableList(authFields);
        this.functional = functional;
        this.docsUrl = docsUrl;
    }

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    public enum AuthMethod {
        API_KEY,
        APP_CREDENTIALS,
        EMAIL_BASIC,
        OAUTH2,
        NONE
    }

    public static final class AuthField {
        /** Field key sent to backend in the credentials map (e.g. "apiKey", "imap_host"). */
        public String key;
        /** Human-readable label. */
        public String label;
        /** "string" | "password" | "number" | "boolean" | "select" */
        public String type;
        /** Non-null when type=select; lists allowed option values. */
        public List<String> options;
        public boolean required;
        public String placeholder;
        /** Short help text shown below the field. */
        public String help;
        public String defaultValue;

        public AuthField() {}

        public static AuthField of(String key, String label, String type, boolean required) {
            AuthField f = new AuthField();
            f.key = key;
            f.label = label;
            f.type = type;
            f.required = required;
            return f;
        }

        public AuthField placeholder(String ph) { this.placeholder = ph; return this; }
        public AuthField help(String h) { this.help = h; return this; }
        public AuthField defaultValue(String dv) { this.defaultValue = dv; return this; }
        public AuthField options(List<String> opts) { this.options = opts; return this; }
    }
}
