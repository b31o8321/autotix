package dev.autotix.infrastructure.platform.wecom;

import dev.autotix.domain.AutotixException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Pure-static crypto utilities for WeCom (企业微信客服) webhook signature and AES-CBC en/decryption.
 *
 * <h3>Signature</h3>
 * SHA1( alphabetically-sorted concat of [token, timestamp, nonce, encrypted] ).hex
 *
 * <h3>AES key derivation</h3>
 * {@code encodingAesKey43} is a 43-character Base64 string that decodes to a 32-byte key.
 * IV = first 16 bytes of the key.
 *
 * <h3>Plaintext layout (inbound / decrypt path)</h3>
 * {@code [16 random bytes][4 bytes BE msg length][msg bytes][corpId bytes]}
 *
 * <h3>Ciphertext layout (outbound / encrypt path)</h3>
 * {@code [16 random bytes][4 bytes BE msg length][msg bytes][corpId bytes]} → PKCS7-padded to 32-byte
 * multiple → AES/CBC/NoPadding → Base64.
 */
public final class WecomCrypto {

    private static final String AES_ALGO = "AES/CBC/NoPadding";

    private WecomCrypto() {}

    // -------------------------------------------------------------------------
    // Signature
    // -------------------------------------------------------------------------

    /**
     * Computes the WeCom message signature.
     *
     * @param token     admin-configured token
     * @param timestamp request timestamp parameter
     * @param nonce     request nonce parameter
     * @param encrypted the Encrypt element content (from POST body) or echostr (from GET query)
     * @return lowercase hex SHA1 of the sorted-concat string
     */
    public static String sha1Signature(String token, String timestamp, String nonce, String encrypted) {
        String[] parts = {token, timestamp, nonce, encrypted};
        Arrays.sort(parts);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new AutotixException.IntegrationException("wecom", "SHA1 computation failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Decryption
    // -------------------------------------------------------------------------

    /**
     * Decrypts a WeCom AES-CBC ciphertext.
     *
     * @param aesKey43      43-char Base64 encoding AES key
     * @param corpId        enterprise Corp ID (used for suffix validation)
     * @param encryptedB64  Base64-encoded ciphertext
     * @return plaintext XML (or echostr text for GET handshake)
     * @throws AutotixException.AuthException if corpId suffix does not match
     */
    public static String decrypt(String aesKey43, String corpId, String encryptedB64) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(aesKey43 + "="); // pad to 44 chars
            byte[] iv = Arrays.copyOfRange(aesKey, 0, 16);
            byte[] cipherBytes = Base64.getDecoder().decode(encryptedB64);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(cipherBytes);

            // Strip PKCS7 padding
            decrypted = pkcs7Unpad(decrypted);

            // Layout: [16 random][4 BE length][msg][corpId]
            if (decrypted.length < 20) {
                throw new AutotixException.AuthException("wecom: decrypted payload too short");
            }
            int msgLen = ByteBuffer.wrap(decrypted, 16, 4).getInt();
            if (msgLen < 0 || 20 + msgLen > decrypted.length) {
                throw new AutotixException.AuthException("wecom: decrypted message length out of range");
            }
            String msg = new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);

            // Validate corpId suffix
            String suffix = new String(decrypted, 20 + msgLen, decrypted.length - 20 - msgLen,
                    StandardCharsets.UTF_8);
            if (!suffix.equals(corpId)) {
                throw new AutotixException.AuthException(
                        "wecom: corpId mismatch in decrypted payload (expected: " + corpId +
                        ", got: " + suffix + ")");
            }
            return msg;
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException("wecom", "AES decryption failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Encryption
    // -------------------------------------------------------------------------

    /**
     * Encrypts a plaintext string for the WeCom GET handshake response.
     *
     * @param aesKey43  43-char Base64 encoding AES key
     * @param corpId    enterprise Corp ID (appended as suffix)
     * @param plaintext the echostr (or any message) to encrypt
     * @return Base64-encoded ciphertext
     */
    public static String encrypt(String aesKey43, String corpId, String plaintext) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(aesKey43 + "=");
            byte[] iv = Arrays.copyOfRange(aesKey, 0, 16);

            byte[] randomBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(randomBytes);

            byte[] msgBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] corpIdBytes = corpId.getBytes(StandardCharsets.UTF_8);

            int contentLen = 16 + 4 + msgBytes.length + corpIdBytes.length;
            byte[] content = new byte[contentLen];
            System.arraycopy(randomBytes, 0, content, 0, 16);
            // 4-byte BE length
            content[16] = (byte) ((msgBytes.length >> 24) & 0xFF);
            content[17] = (byte) ((msgBytes.length >> 16) & 0xFF);
            content[18] = (byte) ((msgBytes.length >> 8) & 0xFF);
            content[19] = (byte) (msgBytes.length & 0xFF);
            System.arraycopy(msgBytes, 0, content, 20, msgBytes.length);
            System.arraycopy(corpIdBytes, 0, content, 20 + msgBytes.length, corpIdBytes.length);

            // PKCS7 pad to 32-byte multiple
            byte[] padded = pkcs7Pad(content, 32);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(padded);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException("wecom", "AES encryption failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // PKCS7 helpers
    // -------------------------------------------------------------------------

    private static byte[] pkcs7Pad(byte[] data, int blockSize) {
        int pad = blockSize - (data.length % blockSize);
        byte[] padded = new byte[data.length + pad];
        System.arraycopy(data, 0, padded, 0, data.length);
        Arrays.fill(padded, data.length, padded.length, (byte) pad);
        return padded;
    }

    private static byte[] pkcs7Unpad(byte[] data) {
        if (data.length == 0) return data;
        int pad = data[data.length - 1] & 0xFF;
        if (pad < 1 || pad > 32 || pad > data.length) return data; // not PKCS7; return as-is
        return Arrays.copyOfRange(data, 0, data.length - pad);
    }

    // -------------------------------------------------------------------------
    // Hex helper
    // -------------------------------------------------------------------------

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
