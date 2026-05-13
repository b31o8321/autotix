package dev.autotix.application.channel;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.platform.PluginRegistry;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectChannelUseCaseTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private PluginRegistry pluginRegistry;
    @Mock private TicketPlatformPlugin plugin;

    private ConnectChannelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConnectChannelUseCase(channelRepository, pluginRegistry);
    }

    @Test
    void connectWithApiKey_healthCheckOk_savesChannel_returnsChannelId() {
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        when(plugin.healthCheck(any(ChannelCredential.class))).thenReturn(true);
        when(channelRepository.save(any())).thenReturn(new ChannelId("42"));

        Map<String, String> credentials = new HashMap<>();
        credentials.put("api_key", "test-key");
        credentials.put("subdomain", "mycompany");

        ChannelId id = useCase.connectWithApiKey(
                PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk", credentials);

        assertNotNull(id);
        assertEquals("42", id.value());
        verify(channelRepository).save(any());
    }

    @Test
    void connectWithApiKey_healthCheckFails_throwsValidationException_nothingSaved() {
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        when(plugin.healthCheck(any(ChannelCredential.class))).thenReturn(false);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("api_key", "bad-key");

        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.connectWithApiKey(
                        PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk", credentials));

        verifyNoInteractions(channelRepository);
    }

    @Test
    void connectWithApiKey_withAccessToken_setsAccessToken() {
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        when(plugin.healthCheck(any(ChannelCredential.class))).thenAnswer(invocation -> {
            ChannelCredential cred = invocation.getArgument(0);
            // access_token should be set
            return cred.accessToken() != null;
        });
        when(channelRepository.save(any())).thenReturn(new ChannelId("7"));

        Map<String, String> credentials = new HashMap<>();
        credentials.put("access_token", "oauth-token");

        ChannelId id = useCase.connectWithApiKey(
                PlatformType.ZENDESK, ChannelType.CHAT, "Chat Channel", credentials);

        assertNotNull(id);
        verify(channelRepository).save(any());
    }

    @Test
    void startOAuth_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> useCase.startOAuth(PlatformType.ZENDESK, ChannelType.EMAIL, "Test"));
    }
}
