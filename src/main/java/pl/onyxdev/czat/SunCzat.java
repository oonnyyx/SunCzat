package pl.onyxdev.czat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pl.onyxdev.suncore.SunCore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SunCzat extends JavaPlugin implements Listener, TabExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern TOKENS = Pattern.compile("@([A-Za-z0-9_]{3,16})|\\[(item|i|przedmiot)]", Pattern.CASE_INSENSITIVE);

    static final class PData {
        volatile String rank;
        volatile long mutedUntil;
        volatile String muteReason = "";
        final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
        volatile boolean spy;
        volatile String name = "";
    }

    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private Rank defaultRank;
    private final java.util.Set<UUID> withPack = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile PackCfg pack;

    private record PackCfg(UUID id, java.net.URI uri, String sha1, Component prompt, boolean required, int waitSeconds) {
    }
    private final Map<UUID, PData> data = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final ChatFilter filter = new ChatFilter();
    private final NotifyGui gui = new NotifyGui();
    private final TabList tab = new TabList(this);
    private File dataFile;
    private String format;
    private String mentionSound, pmSound;
    private float mentionPitch, pmPitch;
    private volatile boolean dirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.register(this);
        dataFile = new File(getDataFolder(), "gracze.yml");
        loadData();
        loadConfigValues();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        for (String c : List.of("msg", "r", "ignoruj", "powiadomienia", "ranga", "wycisz", "odcisz", "podglad", "czat")) {
            var cmd = getCommand(c);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dirty) saveData();
        }, 1200, 1200);
    }

    @Override
    public void onDisable() {
        tab.stop();
        saveData();
        attachments.values().forEach(PermissionAttachment::remove);
        attachments.clear();
    }

    private void loadConfigValues() {
        reloadConfig();
        format = getConfig().getString("format", "<prefiks> <nick> <dark_gray>»</dark_gray> <wiadomosc>");
        loadPack();
        ranks.clear();
        defaultRank = null;
        ConfigurationSection rs = getConfig().getConfigurationSection("rangi");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                Rank r = Rank.load(id, rs.getConfigurationSection(id));
                ranks.put(id, r);
                if (r.isDefault() && defaultRank == null) defaultRank = r;
            }
        }
        if (defaultRank == null) {
            defaultRank = ranks.isEmpty() ? new Rank("gracz", "Gracz", Component.text("[Gracz]", NamedTextColor.GRAY), null,
                    TextColor.color(0xE6E6E6), TextColor.color(0xD6D6D6), 0, true, List.of()) : ranks.values().iterator().next();
            ranks.putIfAbsent(defaultRank.id(), defaultRank);
        }
        ConfigurationSection spam = getConfig().getConfigurationSection("antyspam");
        ConfigurationSection filt = getConfig().getConfigurationSection("filtr");
        filter.load(spam != null ? spam : new YamlConfiguration(), filt != null ? filt : new YamlConfiguration());
        mentionSound = getConfig().getString("wzmianki.dzwiek", "block.note_block.pling");
        mentionPitch = (float) getConfig().getDouble("wzmianki.ton", 1.6);
        pmSound = getConfig().getString("prywatne.dzwiek", "block.note_block.chime");
        pmPitch = (float) getConfig().getDouble("prywatne.ton", 1.3);
        setupTeams();
        tab.load(getConfig().getConfigurationSection("tab"));
    }

    PData d(UUID id) {
        return data.computeIfAbsent(id, k -> new PData());
    }

    Rank rankOf(UUID id) {
        PData p = data.get(id);
        Rank r = p == null || p.rank == null ? null : ranks.get(p.rank);
        return r != null ? r : defaultRank;
    }

    private void loadData() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                PData p = d(id);
                p.rank = y.getString(k + ".ranga");
                p.mutedUntil = y.getLong(k + ".wyciszony-do", 0);
                p.muteReason = y.getString(k + ".powod", "");
                p.spy = y.getBoolean(k + ".podglad", false);
                p.name = y.getString(k + ".nick", "");
                for (String s : y.getStringList(k + ".ignoruje")) p.ignored.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private synchronized void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((id, p) -> {
            String k = id.toString();
            boolean any = false;
            if (p.rank != null) {
                y.set(k + ".ranga", p.rank);
                any = true;
            }
            if (p.mutedUntil != 0) {
                y.set(k + ".wyciszony-do", p.mutedUntil);
                y.set(k + ".powod", p.muteReason);
                any = true;
            }
            if (!p.ignored.isEmpty()) {
                y.set(k + ".ignoruje", p.ignored.stream().map(UUID::toString).toList());
                any = true;
            }
            if (p.spy) {
                y.set(k + ".podglad", true);
                any = true;
            }
            if (any) y.set(k + ".nick", p.name);
        });
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            getLogger().warning("Nie zapisano gracze.yml: " + e.getMessage());
        }
    }

    private String teamName(Rank r) {
        String n = "sc" + String.format(Locale.ROOT, "%03d", Math.max(0, 999 - r.weight())) + r.id();
        return n.length() > 64 ? n.substring(0, 64) : n;
    }

    private void setupTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(sb.getTeams())) {
            String n = t.getName();
            if (n.startsWith("sc") && n.length() > 5 && Character.isDigit(n.charAt(2)) && Character.isDigit(n.charAt(4))) t.unregister();
        }
        for (Rank r : ranks.values()) {
            Team t = sb.registerNewTeam(teamName(r));
            t.prefix(Component.text().append(r.shared()).append(Component.space()).build());
        }
        for (Player p : Bukkit.getOnlinePlayers()) applyRank(p);
    }

    void applyRank(Player p) {
        Rank r = rankOf(p.getUniqueId());
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Rank o : ranks.values()) {
            Team t = sb.getTeam(teamName(o));
            if (t != null && o != r) t.removeEntry(p.getName());
        }
        Team t = sb.getTeam(teamName(r));
        if (t != null) t.addEntry(p.getName());
        TabList.listName(p, r);
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) old.remove();
        if (!r.permissions().isEmpty()) {
            PermissionAttachment a = p.addAttachment(this);
            for (String perm : r.permissions()) a.setPermission(perm, true);
            attachments.put(p.getUniqueId(), a);
        }
        p.updateCommands();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PData pd = d(p.getUniqueId());
        if (!p.getName().equals(pd.name)) {
            pd.name = p.getName();
            dirty = true;
        }
        applyRank(p);
    }

    private void loadPack() {
        String url = getConfig().getString("paczka.url", "").trim();
        String sha1 = getConfig().getString("paczka.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (url.isEmpty()) {
            pack = null;
            return;
        }
        try {
            pack = new PackCfg(UUID.nameUUIDFromBytes(("suncraft-pack:" + url).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    java.net.URI.create(url), sha1, MM.deserialize(getConfig().getString("paczka.opis", "")),
                    getConfig().getBoolean("paczka.wymagana", false), Math.max(5, getConfig().getInt("paczka.czekaj-sekund", 90)));
            getLogger().info("Paczka zasobów: " + url);
        } catch (IllegalArgumentException e) {
            pack = null;
            getLogger().warning("Zły adres paczki zasobów: " + url);
        }
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent e) {
        PackCfg pc = pack;
        if (pc == null) return;
        UUID player = e.getConnection().getProfile().getId();
        if (player == null) return;
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(pc.id(), pc.uri(), pc.sha1()))
                .prompt(pc.prompt())
                .required(pc.required())
                .replace(true)
                .callback((id, status, audience) -> {
                    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) withPack.add(player);
                    else if (!status.intermediate()) withPack.remove(player);
                    if (!status.intermediate()) done.countDown();
                })
                .build();
        e.getConnection().getAudience().sendResourcePacks(req);
        try {
            if (!done.await(pc.waitSeconds(), java.util.concurrent.TimeUnit.SECONDS))
                getLogger().info("Paczka: brak odpowiedzi od " + e.getConnection().getProfile().getName() + " (wchodzi bez obrazków)");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    boolean hasPack(Player p) {
        return withPack.contains(p.getUniqueId());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> withPack.add(id);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> withPack.remove(id);
            default -> {
            }
        }
        getLogger().fine("Paczka " + e.getPlayer().getName() + ": " + e.getStatus());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        withPack.remove(e.getPlayer().getUniqueId());
        PermissionAttachment a = attachments.remove(e.getPlayer().getUniqueId());
        if (a != null) a.remove();
        filter.forget(e.getPlayer().getUniqueId());
    }

    private boolean muted(PData p) {
        if (p.mutedUntil == 0) return false;
        if (p.mutedUntil > 0 && System.currentTimeMillis() > p.mutedUntil) {
            p.mutedUntil = 0;
            dirty = true;
            return false;
        }
        return true;
    }

    private String muteInfo(PData p) {
        String when = p.mutedUntil < 0 ? "na zawsze" : "jeszcze " + duration(p.mutedUntil - System.currentTimeMillis());
        return "Jesteś wyciszony (" + when + ")" + (p.muteReason.isEmpty() ? "." : ": " + p.muteReason);
    }

    private void infoSync(Player p, String key, Component msg) {
        Bukkit.getScheduler().runTask(this, () -> SunCore.messages().send(p, key, msg));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        PData pd = d(p.getUniqueId());
        if (muted(pd)) {
            e.setCancelled(true);
            infoSync(p, Keys.MUTE, Component.text(muteInfo(pd), NamedTextColor.GRAY));
            return;
        }
        String text = PLAIN.serialize(e.message()).trim();
        if (text.isEmpty()) {
            e.setCancelled(true);
            return;
        }
        if (!p.hasPermission("czat.bypass")) {
            String why = filter.spamCheck(p.getUniqueId(), text);
            if (why != null) {
                e.setCancelled(true);
                infoSync(p, Keys.INFO, Component.text(why, NamedTextColor.GRAY));
                return;
            }
            text = filter.censor(filter.fixCaps(text));
        }
        Rank r = rankOf(p.getUniqueId());
        List<Player> mentioned = new ArrayList<>();
        Component body = buildBody(p, text, r.messageColor(), mentioned);
        e.setCancelled(true);
        Component withBadge = line(p, r, body, true), plain = line(p, r, body, false);
        for (var v : e.viewers()) {
            if (v instanceof Player vp && d(vp.getUniqueId()).ignored.contains(p.getUniqueId())) continue;
            if (v instanceof Player vp) vp.sendMessage(hasPack(vp) ? withBadge : plain);
        }
        Bukkit.getConsoleSender().sendMessage(plain);
        if (!mentioned.isEmpty()) {
            Bukkit.getScheduler().runTask(this, () -> {
                for (Player m : mentioned) {
                    if (!m.isOnline() || d(m.getUniqueId()).ignored.contains(p.getUniqueId())) continue;
                    m.playSound(m.getLocation(), mentionSound, SoundCategory.PLAYERS, 1f, mentionPitch);
                    SunCore.messages().actionBar(m, Keys.MENTION, Component.text(p.getName(), NamedTextColor.GOLD)
                            .append(Component.text(" wspomniał o tobie na czacie", NamedTextColor.GRAY)));
                }
            });
        }
    }

    private Component buildBody(Player p, String text, TextColor color, List<Player> mentioned) {
        TextComponent.Builder b = Component.text().color(color);
        Matcher m = TOKENS.matcher(text);
        int last = 0;
        boolean itemUsed = false;
        while (m.find()) {
            b.append(Component.text(text.substring(last, m.start())));
            last = m.end();
            if (m.group(1) != null) {
                Player t = Bukkit.getPlayerExact(m.group(1));
                if (t == null) {
                    b.append(Component.text(m.group()));
                    continue;
                }
                if (!mentioned.contains(t) && t != p) mentioned.add(t);
                b.append(Component.text("@" + t.getName(), NamedTextColor.GOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Kliknij, żeby napisać do " + t.getName(), NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.suggestCommand("/msg " + t.getName() + " ")));
            } else if (!itemUsed) {
                itemUsed = true;
                ItemStack hand = p.getInventory().getItemInMainHand().clone();
                if (hand.getType().isAir()) {
                    b.append(Component.text("[pusta ręka]", NamedTextColor.DARK_GRAY));
                } else {
                    Component name = hand.displayName();
                    if (hand.getAmount() > 1) name = Component.text(hand.getAmount() + "× ", NamedTextColor.GRAY).append(name);
                    b.append(name);
                }
            } else {
                b.append(Component.text(m.group()));
            }
        }
        b.append(Component.text(text.substring(last)));
        return b.build();
    }

    private Component line(Player p, Rank r, Component body, boolean pack) {
        Component nick = Component.text(p.getName(), r.nickColor())
                .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text("Ranga: ", NamedTextColor.GRAY)).append(r.display(pack))
                        .append(Component.newline())
                        .append(Component.text("Kliknij, żeby napisać prywatnie", NamedTextColor.DARK_GRAY)).build()))
                .clickEvent(ClickEvent.suggestCommand("/msg " + p.getName() + " "));
        Component prefix = r.display(pack).hoverEvent(HoverEvent.showText(Component.text(r.name(), NamedTextColor.WHITE)));
        return MM.deserialize(format, Placeholder.component("prefiks", prefix), Placeholder.component("nick", nick),
                Placeholder.component("wiadomosc", body));
    }

    private void privateMessage(Player from, Player to, String text) {
        PData pf = d(from.getUniqueId());
        if (muted(pf)) {
            SunCore.messages().send(from, Keys.MUTE, Component.text(muteInfo(pf), NamedTextColor.GRAY));
            return;
        }
        if (to == from) {
            SunCore.messages().send(from, Keys.INFO, Component.text("Rozmowa ze sobą to dobry znak, ale nie tutaj.", NamedTextColor.GRAY));
            return;
        }
        if (!from.hasPermission("czat.bypass")) {
            String why = filter.spamCheck(from.getUniqueId(), "msg:" + to.getName() + ":" + text);
            if (why != null) {
                SunCore.messages().send(from, Keys.INFO, Component.text(why, NamedTextColor.GRAY));
                return;
            }
            text = filter.censor(filter.fixCaps(text));
        }
        if (d(to.getUniqueId()).ignored.contains(from.getUniqueId())) {
            SunCore.messages().send(from, Keys.INFO, Component.text(to.getName() + " nie przyjmuje od ciebie wiadomości.", NamedTextColor.GRAY));
            return;
        }
        Component body = buildBody(from, text, TextColor.color(0xF2E6FF), new ArrayList<>());
        Component out = Component.text().append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Ty", NamedTextColor.GRAY)).append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(to.getName(), rankOf(to.getUniqueId()).nickColor())
                        .clickEvent(ClickEvent.suggestCommand("/msg " + to.getName() + " ")))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY)).append(body).build();
        Component in = Component.text().append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(from.getName(), rankOf(from.getUniqueId()).nickColor())
                        .hoverEvent(HoverEvent.showText(Component.text("Kliknij, żeby odpisać", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.suggestCommand("/msg " + from.getName() + " ")))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY)).append(Component.text("Ty", NamedTextColor.GRAY))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY)).append(body).build();
        SunCore.messages().send(from, Keys.PRIVATE, out);
        if (SunCore.messages().send(to, Keys.PRIVATE, in)) {
            to.playSound(to.getLocation(), pmSound, SoundCategory.PLAYERS, 0.9f, pmPitch);
        }
        lastPartner.put(from.getUniqueId(), to.getUniqueId());
        lastPartner.put(to.getUniqueId(), from.getUniqueId());
        Component spy = Component.text("[Podgląd] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(from.getName() + " → " + to.getName() + ": ", NamedTextColor.GRAY))
                .append(Component.text(PLAIN.serialize(body), NamedTextColor.DARK_GRAY));
        for (Player a : Bukkit.getOnlinePlayers()) {
            if (a == from || a == to || !d(a.getUniqueId()).spy || !a.hasPermission("czat.admin")) continue;
            SunCore.messages().send(a, Keys.SPY, spy);
        }
        getLogger().info("[PW] " + from.getName() + " -> " + to.getName() + ": " + PLAIN.serialize(body));
    }

    private void info(CommandSender s, String text) {
        if (s instanceof Player p) SunCore.messages().send(p, Keys.INFO, Component.text(text, NamedTextColor.GRAY));
        else s.sendMessage(text);
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "msg" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length < 2) {
                    info(s, "Użycie: /msg <gracz> <wiadomość>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) {
                    info(s, "Gracz " + a[0] + " nie jest online.");
                    return true;
                }
                privateMessage(p, t, String.join(" ", List.of(a).subList(1, a.length)));
            }
            case "r" -> {
                if (!(s instanceof Player p)) return true;
                UUID last = lastPartner.get(p.getUniqueId());
                Player t = last == null ? null : Bukkit.getPlayer(last);
                if (t == null) {
                    info(s, "Nie masz komu odpowiedzieć (albo ta osoba wyszła).");
                    return true;
                }
                if (a.length == 0) {
                    info(s, "Użycie: /r <wiadomość>");
                    return true;
                }
                privateMessage(p, t, String.join(" ", a));
            }
            case "ignoruj" -> {
                if (!(s instanceof Player p)) return true;
                PData pd = d(p.getUniqueId());
                if (a.length == 0) {
                    List<String> names = pd.ignored.stream().map(id -> {
                        PData o = data.get(id);
                        return o != null && !o.name.isEmpty() ? o.name : id.toString().substring(0, 8);
                    }).toList();
                    info(s, names.isEmpty() ? "Nikogo nie ignorujesz. /ignoruj <gracz>" : "Ignorujesz: " + String.join(", ", names));
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
                if (t == null || t.getUniqueId().equals(p.getUniqueId())) {
                    info(s, "Nie znam takiego gracza.");
                    return true;
                }
                if (pd.ignored.remove(t.getUniqueId())) {
                    info(s, "Już nie ignorujesz " + t.getName() + ".");
                } else {
                    pd.ignored.add(t.getUniqueId());
                    d(t.getUniqueId()).name = t.getName() == null ? a[0] : t.getName();
                    info(s, "Ignorujesz " + t.getName() + ": nie zobaczysz jego czatu ani prywatnych wiadomości.");
                }
                dirty = true;
            }
            case "powiadomienia" -> {
                if (s instanceof Player p) gui.open(p, 0);
            }
            case "podglad" -> {
                if (!(s instanceof Player p)) return true;
                PData pd = d(p.getUniqueId());
                pd.spy = !pd.spy;
                dirty = true;
                info(s, pd.spy ? "Podgląd prywatnych wiadomości: włączony." : "Podgląd prywatnych wiadomości: wyłączony.");
            }
            case "ranga" -> rankCommand(s, a);
            case "wycisz" -> muteCommand(s, a);
            case "odcisz" -> {
                if (a.length < 1) {
                    info(s, "Użycie: /odcisz <gracz>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
                if (t == null) {
                    info(s, "Nie znam takiego gracza.");
                    return true;
                }
                d(t.getUniqueId()).mutedUntil = 0;
                dirty = true;
                saveData();
                info(s, "Odciszono " + t.getName() + ".");
                if (t.getPlayer() != null) {
                    SunCore.messages().send(t.getPlayer(), Keys.MUTE, Component.text("Możesz znowu pisać na czacie.", NamedTextColor.GRAY));
                }
            }
            case "czat" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
                    loadConfigValues();
                    info(s, "Przeładowano czat: " + ranks.size() + " rang.");
                } else {
                    info(s, "/czat reload");
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void rankCommand(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "lista" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "daj" -> {
                if (a.length < 3) {
                    info(s, "Użycie: /ranga daj <gracz> <ranga>");
                    return;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                Rank r = ranks.get(a[2].toLowerCase(Locale.ROOT).replace("+", "plus"));
                if (t == null) {
                    info(s, "Nie znam gracza " + a[1] + " (musi być choć raz na serwerze).");
                    return;
                }
                if (r == null) {
                    info(s, "Nie ma rangi " + a[2] + ". Rangi: " + String.join(", ", ranks.keySet()));
                    return;
                }
                PData pd = d(t.getUniqueId());
                pd.rank = r == defaultRank ? null : r.id();
                pd.name = t.getName() == null ? a[1] : t.getName();
                dirty = true;
                saveData();
                if (t.getPlayer() != null) {
                    applyRank(t.getPlayer());
                    SunCore.messages().send(t.getPlayer(), Keys.INFO, Component.text("Twoja ranga: ", NamedTextColor.GRAY).append(r.display(hasPack(t.getPlayer()))));
                }
                info(s, pd.name + " ma teraz rangę " + r.name() + ".");
            }
            case "gracz" -> {
                if (a.length < 2) {
                    info(s, "Użycie: /ranga gracz <nick>");
                    return;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) {
                    info(s, "Nie znam takiego gracza.");
                    return;
                }
                info(s, t.getName() + ": " + rankOf(t.getUniqueId()).name());
            }
            default -> {
                for (Rank r : ranks.values()) {
                    long n = data.values().stream().filter(p -> r.id().equals(p.rank)).count();
                    boolean pk = s instanceof Player sp && hasPack(sp);
                    s.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY).append(r.display(pk))
                            .append(Component.text(" " + r.id() + " (waga " + r.weight() + ")"
                                    + (r.isDefault() ? " — domyślna" : " — graczy: " + n), NamedTextColor.GRAY)));
                }
            }
        }
    }

    private void muteCommand(CommandSender s, String[] a) {
        if (a.length < 1) {
            info(s, "Użycie: /wycisz <gracz> [czas: 30s, 10m, 2h, 1d] [powód]");
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) {
            info(s, "Nie znam takiego gracza.");
            return;
        }
        long ms = -1;
        int reasonFrom = 1;
        if (a.length > 1) {
            long parsed = parseDuration(a[1]);
            if (parsed > 0) {
                ms = parsed;
                reasonFrom = 2;
            }
        }
        PData pd = d(t.getUniqueId());
        pd.mutedUntil = ms < 0 ? -1 : System.currentTimeMillis() + ms;
        pd.muteReason = a.length > reasonFrom ? String.join(" ", List.of(a).subList(reasonFrom, a.length)) : "";
        pd.name = t.getName() == null ? a[0] : t.getName();
        dirty = true;
        saveData();
        String when = ms < 0 ? "na zawsze" : "na " + duration(ms);
        info(s, "Wyciszono " + pd.name + " " + when + (pd.muteReason.isEmpty() ? "." : " (" + pd.muteReason + ")."));
        if (t.getPlayer() != null) SunCore.messages().send(t.getPlayer(), Keys.MUTE, Component.text(muteInfo(pd), NamedTextColor.GRAY));
    }

    static long parseDuration(String s) {
        Matcher m = Pattern.compile("^(\\d+)([smhd])$").matcher(s.toLowerCase(Locale.ROOT));
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "s" -> n * 1000;
            case "m" -> n * 60_000;
            case "h" -> n * 3_600_000;
            default -> n * 86_400_000;
        };
    }

    static String duration(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s < 60) return s + " s";
        if (s < 3600) return (s / 60) + " min";
        if (s < 86400) return (s / 3600) + " h " + (s % 3600 / 60) + " min";
        return (s / 86400) + " dni " + (s % 86400 / 3600) + " h";
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        Stream<String> online = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        List<String> out = switch (c) {
            case "msg", "ignoruj", "wycisz", "odcisz" -> a.length == 1 ? online.toList() : List.of();
            case "ranga" -> a.length == 1 ? List.of("daj", "lista", "gracz")
                    : a.length == 2 ? online.toList()
                    : a.length == 3 && a[0].equalsIgnoreCase("daj") ? new ArrayList<>(ranks.keySet()) : List.of();
            case "czat" -> a.length == 1 ? List.of("reload") : List.of();
            default -> List.of();
        };
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
