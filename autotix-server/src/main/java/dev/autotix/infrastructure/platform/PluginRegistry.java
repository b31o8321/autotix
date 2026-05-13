package dev.autotix.infrastructure.platform;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.PlatformType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all available {@link TicketPlatformPlugin} beans, keyed by {@link PlatformType}.
 *
 * <p>Spring auto-injects all {@code TicketPlatformPlugin} implementations at startup.
 * Duplicate platform registrations are rejected immediately with {@link IllegalStateException}.
 */
@Component
public class PluginRegistry {

    private final Map<PlatformType, TicketPlatformPlugin> pluginMap;
    private final List<TicketPlatformPlugin> pluginList;

    @Autowired
    public PluginRegistry(List<TicketPlatformPlugin> plugins) {
        Map<PlatformType, TicketPlatformPlugin> map = new HashMap<>();
        for (TicketPlatformPlugin plugin : plugins) {
            PlatformType key = plugin.platform();
            if (map.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate TicketPlatformPlugin for platform: " + key
                        + " (conflicting classes: "
                        + map.get(key).getClass().getSimpleName()
                        + " vs " + plugin.getClass().getSimpleName() + ")");
            }
            map.put(key, plugin);
        }
        this.pluginMap = Collections.unmodifiableMap(map);
        this.pluginList = Collections.unmodifiableList(new ArrayList<>(plugins));
    }

    /**
     * Returns the plugin for the given platform.
     *
     * @throws AutotixException.NotFoundException if no plugin is registered for the given type
     */
    public TicketPlatformPlugin get(PlatformType platform) {
        TicketPlatformPlugin plugin = pluginMap.get(platform);
        if (plugin == null) {
            throw new AutotixException.NotFoundException(
                    "No plugin registered for platform: " + platform);
        }
        return plugin;
    }

    /** Returns all registered plugins as an unmodifiable list. */
    public List<TicketPlatformPlugin> all() {
        return pluginList;
    }
}
