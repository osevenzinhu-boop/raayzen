package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuerraCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public GuerraCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) { p.sendMessage(Theme.MM.deserialize("<red>Sem nação.</red>")); return true; }

        if (args.length >= 2 && args[0].equalsIgnoreCase("iniciar")) {
            if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
                p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode iniciar.</red>")); return true;
            }
            String target = args[1].toLowerCase();
            if (plugin.getNationManager().getNation(target) == null) {
                p.sendMessage(Theme.MM.deserialize("<red>Nação invalida (brasil/japao/eua/russia).</red>")); return true;
            }
            int id = plugin.getVotingManager().startWarProposal(my.getId(), target);
            if (id == -2) p.sendMessage(Theme.MM.deserialize("<red>Ja estao em guerra.</red>"));
            else if (id == -3) p.sendMessage(Theme.MM.deserialize("<red>Ja existe votacao aberta contra essa nação.</red>"));
            else if (id == -4) p.sendMessage(Theme.MM.deserialize("<red>Sua nacao precisa de no minimo 5 membros para iniciar guerra.</red>"));
            else if (id > 0) p.sendMessage(Theme.MM.deserialize("<green>Votacao aberta! ID: " + id +
                    " - civils: /votar " + id + " sim|nao (70%, sem prazo)</green>"));
            else p.sendMessage(Theme.MM.deserialize("<red>Falha.</red>"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            var props = plugin.getVotingManager().getActiveProposals();
            if (props.isEmpty()) p.sendMessage(Theme.MM.deserialize("<gray>Nenhuma proposta ativa.</gray>"));
            else for (var v : props) {
                String phase = v.waitingTarget ? "Aguardando alvo" : "Votacao propositor";
                p.sendMessage(Theme.MM.deserialize("<gold>#" + v.id + ": " + v.proposerNation + " > " +
                        v.targetNation + " [" + phase + "]</gold>"));
            }
            return true;
        }
        p.sendMessage(Theme.MM.deserialize(
                "<yellow>/guerra iniciar [nacao]</yellow> <gray>- Presidente inicia votacao 70%</gray>\n" +
                "<yellow>/guerra status</yellow> <gray>- propostas ativas</gray>\n" +
                "<dark_gray>Fluxo: A vota 70% > embargo em B > B vota 70% > GUERRA</dark_gray>"));
        return true;
    }
}
