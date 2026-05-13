package dev.autotix.domain;

/**
 * Base domain exception — maps to 4xx responses at the interfaces layer.
 */
public class AutotixException extends RuntimeException {

    public AutotixException(String message) {
        super(message);
    }

    public AutotixException(String message, Throwable cause) {
        super(message, cause);
    }

    // -----------------------------------------------------------------------
    // Subclasses
    // -----------------------------------------------------------------------

    /** Authentication / authorization failures. Always renders as 401 or 403. */
    public static class AuthException extends AutotixException {
        public AuthException(String message) {
            super(message);
        }
    }

    /** Business-rule / input validation violation. Renders as 400 or 422. */
    public static class ValidationException extends AutotixException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Resource not found. Renders as 404. */
    public static class NotFoundException extends AutotixException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Failure calling an external system (AI, platform API, etc.). Renders as 502. */
    public static class IntegrationException extends AutotixException {
        private final String platform;
        public IntegrationException(String platform, String message) {
            super("[" + platform + "] " + message);
            this.platform = platform;
        }
        public IntegrationException(String platform, String message, Throwable cause) {
            super("[" + platform + "] " + message, cause);
            this.platform = platform;
        }
        public String platform() { return platform; }
    }
}
