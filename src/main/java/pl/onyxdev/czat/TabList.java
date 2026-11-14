package pl.onyxdev.czat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

final class TabList {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String GRADIENT = "#fff3b0:#ffc02e:#ff7a1a:#ffc02e:#fff3b0";

    private final SunCzat plugin;
    private BukkitTask task;
    private List<String> header = List.of(), footer = List.of();
    private boolean on;
    private int tick;

    TabList(SunCzat plugin) {
        this.plugin = plugin;
    }

    void load(ConfigurationSection s) {
        stop();
        on = s != null && s.getBoolean("wlaczony", true);
        if (!on) {
            for (Player p : Bukkit.getOnlinePlayers()) p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            return;
        }
        header = s.getStringList("naglowek");
        footer = s.getStringList("stopka");
        int every = Math.max(2, s.getInt("odswiezanie-tickow", 4));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 1, every);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void update() {
        tick++;
        double phase = ((tick * 0.08) % 2.0) - 1.0;
        String anim = "<gradient:" + GRADIENT + ":" + String.format(Locale.ROOT, "%.2f", phase) + ">";
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String max = String.valueOf(Bukkit.getMaxPlayers());
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        Component tpsC = Component.text(String.format(Locale.ROOT, "%.1f", tps),
                tps >= 18 ? NamedTextColor.WHITE : tps >= 15 ? TextColor.color(0xFFC83D) : TextColor.color(0xFF7A3D));
        for (Player p : Bukkit.getOnlinePlayers()) {
            int ping = p.getPing();
            Component pingC = Component.text(ping + " ms",
                    ping < 80 ? NamedTextColor.WHITE : ping < 180 ? TextColor.color(0xFFC83D) : TextColor.color(0xFF7A3D));
            Rank r = plugin.rankOf(p.getUniqueId());
            TagResolver res = TagResolver.resolver(
                    Placeholder.unparsed("online", online), Placeholder.unparsed("max", max),
                    Placeholder.component("ping", pingC), Placeholder.component("tps", tpsC),
                    Placeholder.unparsed("nick", p.getName()), Placeholder.component("ranga", r.display(plugin.hasPack(p))));
            p.sendPlayerListHeaderAndFooter(lines(header, anim, res), lines(footer, anim, res));
        }
    }

    private static Component lines(List<String> raw, String anim, TagResolver res) {
        return Component.join(JoinConfiguration.newlines(), raw.stream()
                .map(l -> MM.deserialize(l.replace("<anim>", anim).replace("</anim>", "</gradient>"), res)).toList());
    }

    static void listName(Player p, Rank r) {
        p.playerListName(Component.text().append(r.shared()).append(Component.space())
                .append(Component.text(p.getName(), r.nickColor())).build());
    }
}
