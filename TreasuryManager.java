package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import com.nationswaw.utils.Ledger;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Tesouro Nacional - fundo inicial 5.000.000 */
public class TreasuryManager {
    private final NationsWaW plugin;
    private final Ledger ledger;

    public TreasuryManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
    }

    public boolean deposit(Player player, double amount) {
        Nation n = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        if (n == null) return false;
        if (!plugin.getEconomyManager().removeBalance(player.getUniqueId(), n.getId(), amount)) return false;
        n.addTreasury(amount);
        ledger.record(n.getId(), "DEPOSIT", amount, player.getUniqueId(), "Deposito no Tesouro por " + player.getName());
        plugin.getNationManager().saveAll();
        return true;
    }

    public boolean withdraw(Player player, double amount) {
        if (!plugin.getRoleManager().hasRoleAtLeast(player.getUniqueId(), RoleType.GOVERNOR)) return false;
        Nation n = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        if (n == null || !n.removeTreasury(amount)) return false;
        plugin.getEconomyManager().addBalance(player.getUniqueId(), n.getId(), amount);
        ledger.record(n.getId(), "WITHDRAW", amount, player.getUniqueId(), "Saque do Tesouro por " + player.getName());
        plugin.getNationManager().saveAll();
        return true;
    }

    public double getTreasury(String nationId) {
        Nation n = plugin.getNationManager().getNation(nationId);
        return n != null ? n.getTreasury() : 0;
    }

    public Ledger getLedger() { return ledger; }
}
