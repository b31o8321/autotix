package dev.autotix.domain.ai;

/**
 * TODO: Port (hex-arch) for invoking AI to generate a reply.
 *  Implemented in infrastructure/ai (OpenAI-compatible HTTP client).
 *  Replaceable per deployment.
 */
public interface AIReplyPort {

    /**
     * TODO: call AI with the prepared request and return structured response.
     *  Implementation must:
     *    - apply per-tenant AI config (endpoint/apiKey/model)
     *    - tolerate plain-string AI responses (wrap as AIResponse with reply only)
     *    - handle timeout, retry, and error -&gt; throw domain exception
     */
    AIResponse generate(AIRequest request);
}
