package com.joshuaop.rankforge.softdep;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Isolated Vault Economy adapter.
 * All references to the Vault API are contained here so the JVM only loads
 * this class (and the Economy API classes) after we have confirmed that Vault
 * is installed.  Never import or reference this class from code that runs
 * before the Vault presence check — use DependencyManager / SoftDependency instead.
 */
class VaultAdapter {

    private final Economy economy;

    private VaultAdapter(Economy economy) {
        this.economy = economy;
    }

    /**
     * Attempt to obtain a Vault Economy provider and wrap it.
     *
     * @return a ready {@link VaultAdapter}, or {@code null} if no Economy provider is registered.
     */
    static VaultAdapter create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return null;
        Economy eco = rsp.getProvider();
        return eco != null ? new VaultAdapter(eco) : null;
    }

    double getBalance(Player player) {
        try { return economy.getBalance(player); }
        catch (Exception e) { return 0; }
    }

    double getBalance(OfflinePlayer player) {
        try { return economy.getBalance(player); }
        catch (Exception e) { return 0; }
    }

    boolean withdraw(Player player, double amount) {
        try {
            return economy.has(player, amount)
                    && economy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Exception e) { return false; }
    }

    void setBalance(OfflinePlayer player, double targetAmount) {
        try {
            double current = economy.getBalance(player);
            double diff    = targetAmount - current;
            if (diff > 0)      economy.depositPlayer(player, diff);
            else if (diff < 0) economy.withdrawPlayer(player, -diff);
        } catch (Exception ignored) {}
    }
}
