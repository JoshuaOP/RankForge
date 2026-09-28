package com.joshuaop.rankforge.softdep;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Isolated Vault Economy adapter.
 * All references to the Vault API are contained here so the JVM only loads
 * this class (and the Economy API classes) after we have confirmed that Vault
 * is installed.
 */
class VaultAdapter {

    private final JavaPlugin plugin;
    private final Economy economy;

    private VaultAdapter(JavaPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /**
     * Attempt to obtain a Vault Economy provider and wrap it.
     *
     * @return a ready {@link VaultAdapter}, or {@code null} if no Economy provider is registered.
     */
    static VaultAdapter create(JavaPlugin plugin) {
        // Explicitly check if Vault is enabled first
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null ||
                !plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return null;
        Economy eco = rsp.getProvider();
        return eco != null ? new VaultAdapter(plugin, eco) : null;
    }

    double getBalance(Player player) {
        if (player == null) return 0.0;
        try { 
            return economy.getBalance(player); 
        } catch (Exception e) { 
            return 0.0; 
        }
    }

    double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0;
        try { 
            return economy.getBalance(player); 
        } catch (Exception e) { 
            return 0.0; 
        }
    }

    boolean withdraw(Player player, double amount) {
        if (player == null || !Double.isFinite(amount) || amount <= 0.0) return false;
        try {
            if (!economy.has(player, amount)) return false;
            var response = economy.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Exception e) { 
            return false; 
        }
    }

    boolean refund(Player player, double amount) {
        if (player == null || !Double.isFinite(amount) || amount <= 0.0) return false;
        try {
            var response = economy.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Exception e) { 
            return false; 
        }
    }

    void setBalance(OfflinePlayer player, double targetAmount) {
        if (player == null || !Double.isFinite(targetAmount) || targetAmount < 0.0) return;
        try {
            double current = economy.getBalance(player);
            double diff    = targetAmount - current;
            if (diff > 0)      economy.depositPlayer(player, diff);
            else if (diff < 0) economy.withdrawPlayer(player, -diff);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to set balance for player " + player.getName(), e);
        }
    }
}
