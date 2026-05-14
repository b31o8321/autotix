package dev.autotix.domain.customer;

/**
 * The type of identifier used to identify a customer across channels.
 */
public enum CustomerIdentifierType {
    EMAIL,
    PHONE,
    LINE_USER_ID,
    WECOM_USER_ID,
    WHATSAPP_NUM,
    INSTAGRAM_HANDLE,
    ZENDESK_USER,
    CUSTOM_EXTERNAL
}
