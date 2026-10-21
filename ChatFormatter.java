package com.nationswaw.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ChatFormatter {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChatFormatter() {}

    public static Component conversionPreview(String fromSym, double amount, String toSym,
                                              double rate, double issp, double isdn,
                                              double effective, double receive,
                                              String fromName, String toName) {
        String box =
                "<gold>==========================================</gold>\n" +
                "<gradient:#FFD700:#FFA000><bold>[◆] CONVERSAO DE MOEDA - NationsWaW</bold></gradient>\n" +
                "<gold>==========================================</gold>\n" +
                "<white>[■] Valor a converter:</white>   <yellow>" + fromSym + " " + fmt(amount) + "</yellow>\n" +
                "<white>[■] Taxa de cambio:</white>      <aqua>1 " + fromName + " = " + String.format("%.4f", rate) + " " + toName + "</aqua>\n" +
                "<white>[■] ISSP (3%):</white>           <red>" + fromSym + " " + fmt(issp) + "</red>\n" +
                "<white>[■] ISDN (2%):</white>           <red>" + fromSym + " " + fmt(isdn) + "</red>\n" +
                "<white>[■] Valor efetivo:</white>       <green>" + fromSym + " " + fmt(effective) + "</green>\n" +
                "<white>[■] Voce recebera:</white>       <green>" + toSym + " " + fmt(receive) + "</green>\n" +
                "<gold>==========================================</gold>\n" +
                "<yellow>[◆] Confirme a operacao:</yellow>\n";
        Component base = MM.deserialize(box);
        Component confirm = MM.deserialize("<green><bold>[CONFIRMAR]</bold></green>")
                .clickEvent(ClickEvent.runCommand("/confirmar"))
                .hoverEvent(HoverEvent.showText(MM.deserialize("<green>Clique para confirmar</green>")));
        Component cancel = MM.deserialize("<red><bold>[CANCELAR]</bold></red>")
                .clickEvent(ClickEvent.runCommand("/cancelar"))
                .hoverEvent(HoverEvent.showText(MM.deserialize("<red>Clique para cancelar</red>")));
        Component footer = MM.deserialize("\n<dark_gray>Digite /confirmar ou /cancelar em 30s.</dark_gray>\n" +
                "<gold>==========================================</gold>");
        return base.append(confirm).append(Component.text("  ")).append(cancel).append(footer);
    }

    private static String fmt(double v) {
        return String.format("%,.2f", v);
    }

    public static Component prefix(String msg) {
        return MM.deserialize("<gradient:#FFD700:#FFA000><bold>NationsWaW</bold></gradient> <dark_gray>»</dark_gray> " + msg);
    }
}
