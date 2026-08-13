package com.joshuaop.rankforge.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Interface for external plugins to supply a custom GUI that RankForge
 * can open in place of (or alongside) its built-in rank tree.
 *
 * <h3>Use cases:</h3>
 * <ul>
 *   <li>Replace the default rank tree with a custom animated GUI.</li>
 *   <li>Inject additional pages into the rank GUI flow.</li>
 *   <li>Provide a themed GUI matching the server's resource pack.</li>
 * </ul>
 *
 * <h3>Registration:</h3>
 * <pre>{@code
 * RankForgeAPI.getInstance().getExternalGUIRegistry()
 *     .register(GuiType.RANK_TREE, new MyCustomRankTree());
 * }</pre>
 *
 * <h3>Implementation example:</h3>
 * <pre>{@code
 * public class MyCustomRankTree implements ExternalGUIProvider {
 *     public String getName() { return "MyRankTree"; }
 *
 *     public Inventory buildInventory(Player player) {
 *         Inventory inv = Bukkit.createInventory(null, 54, "§6Custom Tree");
 *         // ... populate ...
 *         return inv;
 *     }
 * }
 * }</pre>
 */
public interface ExternalGUIProvider {

    /**
     * Supported GUI types that can be overridden.
     */
    enum GuiType {
        /** The main player-facing rank tree GUI ({@code /rank}). */
        RANK_TREE,
        /** The admin overview editor ({@code /rank editor}). */
        ADMIN_EDITOR,
        /** The leaderboard GUI ({@code /rank leaderboard}). */
        LEADERBOARD,
        /** The challenges GUI ({@code /rank challenges}). */
        CHALLENGES,
        /** The quests GUI ({@code /rank quests}). */
        QUESTS
    }

    /**
     * Unique name for this provider (used for identification and logging).
     */
    String getName();

    /**
     * Build and return the inventory to display to the player.
     * Called on the main server thread each time the GUI is opened.
     *
     * @param player the player for whom the GUI is being built
     * @return the populated inventory to open; must not be {@code null}
     */
    Inventory buildInventory(Player player);

    /**
     * Called by RankForge after opening the inventory via {@code player.openInventory()}.
     * Override to register any additional click handlers or state for this session.
     * Default implementation does nothing.
     *
     * @param player the player who opened the GUI
     */
    default void onOpen(Player player) {}

    /**
     * Called by RankForge when the player closes the inventory.
     * Override to clean up any per-session state.
     * Default implementation does nothing.
     *
     * @param player the player who closed the GUI
     */
    default void onClose(Player player) {}
}
