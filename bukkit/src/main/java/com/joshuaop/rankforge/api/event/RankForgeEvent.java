package com.joshuaop.rankforge.api.event;

import org.bukkit.event.Event;

/**
 * Abstract base class for all RankForge custom events.
 *
 * <p>All RankForge events extend this class so external plugins can
 * register a single listener for any RankForge event type:
 * <pre>
 * {@code
 * plugin.getServer().getPluginManager().registerEvents(listener, plugin);
 * }
 * </pre>
 *
 * <p>This class is part of the RankForge Developer API.
 * All sub-events are documented individually.
 */
public abstract class RankForgeEvent extends Event {

    protected RankForgeEvent() {
        super(false);   // synchronous by default
    }

    protected RankForgeEvent(boolean async) {
        super(async);
    }
}
