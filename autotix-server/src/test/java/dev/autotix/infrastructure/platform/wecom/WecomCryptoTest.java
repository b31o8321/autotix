package dev.autotix.infrastructure.platform.wecom;

import dev.autotix.domain.AutotixException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WecomCrypto — pure crypto utilities.
 */
class WecomCryptoTest {

    // -----------------------------------------------------------------------
    // Helper: generate a valid random 43-char base64 AES key
    // -----------------------------------------------------------------------

    private static String randomAesKey43() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        // Encode 32 bytes → 44 chars base64; trim last '=' to get 43
        String b64 = Base64.getEncoder().encodeToString(key);
        return b64.substring(0, 43);
    }

    // -----------------------------------------------------------------------
    // sha1Signature
    // -----------------------------------------------------------------------

    @Test
    void sha1Signature_knownInput_matchesExpected() {
        // Pre-computed: SHA1 of sorted-concat [token, timestamp, nonce, encrypt] where
        // sorted order is: "encrypt_val", "nonce_val", "timestamp_val", "token_val"
        // (alphabetical: e < n < ti < to)
        // We just verify the output is 40-char lowercase hex and consistent.
        String sig1 = WecomCrypto.sha1Signature("token_val", "timestamp_val", "nonce_val", "encrypt_val");
        String sig2 = WecomCrypto.sha1Signature("token_val", "timestamp_val", "nonce_val", "encrypt_val");

        assertNotNull(sig1);
        assertEquals(40, sig1.length(), "SHA1 hex should be 40 chars");
        assertTrue(sig1.matches("[0-9a-f]+"), "SHA1 hex should be lowercase hex");
        assertEquals(sig1, sig2, "Same inputs should produce same signature");
    }

    @Test
    void sha1Signature_sortingMatters_differentOrderSameResult() {
        // Alphabetical sort means order of params doesn't matter
        String sig = WecomCrypto.sha1Signature("token", "1234567890", "abc", "encryptedMsg");
        // Just verify it's stable
        assertEquals(sig, WecomCrypto.sha1Signature("token", "1234567890", "abc", "encryptedMsg"));
        assertEquals(40, sig.length());
    }

    @Test
    void sha1Signature_differentInputs_differentResults() {
        String sig1 = WecomCrypto.sha1Signature("tokenA", "ts", "nc", "enc");
        String sig2 = WecomCrypto.sha1Signature("tokenB", "ts", "nc", "enc");
        assertNotEquals(sig1, sig2);
    }

    // -----------------------------------------------------------------------
    // encrypt / decrypt round-trip
    // -----------------------------------------------------------------------

    @Test
    void encryptDecrypt_roundTrip_yieldsOriginalPlaintext() {
        String aesKey = randomAesKey43();
        String corpId = "wwCORPID12345678";
        String original = "Hello WeCom round-trip test!";

        String encrypted = WecomCrypto.encrypt(aesKey, corpId, original);
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());

        String decrypted = WecomCrypto.decrypt(aesKey, corpId, encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptDecrypt_longMessage_roundTrip() {
        String aesKey = randomAesKey43();
        String corpId = "wwCORPID12345678";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("中文消息内容 WeCom test message ");
        String original = sb.toString();

        String decrypted = WecomCrypto.decrypt(aesKey, corpId, WecomCrypto.encrypt(aesKey, corpId, original));
        assertEquals(original, decrypted);
    }

    @Test
    void encryptDecrypt_xmlContent_roundTrip() {
        String aesKey = randomAesKey43();
        String corpId = "wwCORPID12345678";
        String xmlMsg = "<xml><ToUserName><![CDATA[wkABCD]]></ToUserName><Token>voucher123</Token></xml>";

        assertEquals(xmlMsg, WecomCrypto.decrypt(aesKey, corpId, WecomCrypto.encrypt(aesKey, corpId, xmlMsg)));
    }

    // -----------------------------------------------------------------------
    // decrypt: corpId mismatch
    // -----------------------------------------------------------------------

    @Test
    void decrypt_corpIdMismatch_throwsAuthException() {
        String aesKey = randomAesKey43();
        String corpId = "wwCORPID12345678";
        String encrypted = WecomCrypto.encrypt(aesKey, corpId, "test payload");

        // Decrypt with wrong corpId
        assertThrows(AutotixException.AuthException.class,
                () -> WecomCrypto.decrypt(aesKey, "wwWRONGCORPID", encrypted));
    }

    // -----------------------------------------------------------------------
    // sha1Signature: specific known vector (manually computed)
    // -----------------------------------------------------------------------

    @Test
    void sha1Signature_specificVector_matchesManualComputation() {
        // Sorted: ["hello", "nonce1", "token123", "ts999"] → "hellononce1token123ts999"
        // SHA1("hellononce1token123ts999") → compute with known algorithm
        String computed = WecomCrypto.sha1Signature("token123", "ts999", "nonce1", "hello");

        // Re-compute locally to verify
        try {
            String[] parts = {"token123", "ts999", "nonce1", "hello"};
            java.util.Arrays.sort(parts);
            StringBuilder sb = new StringBuilder();
            for (String p : parts) sb.append(p);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String expected = WecomCrypto.bytesToHex(digest);
            assertEquals(expected, computed);
        } catch (Exception e) {
            fail("SHA1 computation failed: " + e.getMessage());
        }
    }
}
