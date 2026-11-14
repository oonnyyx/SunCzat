package pl.onyxdev.czat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.onyxdev.suncore.MessageType;
import pl.onyxdev.suncore.Messages;
import pl.onyxdev.suncore.SunCore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NotifyGui implements Listener {
    private static final int PER_PAGE = 36;

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();
        int page;
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final Map<String, String> PLUGIN_NAMES = Map.of(
            "SunSkrzynki", "Skrzynki", "SunPowitanie", "Powitanie", "SunPiatki", "Piątki", "SunSzafa", "Szafa grająca",
            "SunBossy", "Bossowie", "SunQuesty", "Zadania", "SunCzat", "Czat");

    void open(Player p, int page) {
        Messages m = SunCore.messages();
        List<MessageType> types = new ArrayList<>(m.types().stream().filter(MessageType::playerMutable).toList());
        types.sort(Comparator.comparing(MessageType::plugin).thenComparing(MessageType::key));
        int pages = Math.max(1, (types.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Powiadomienia" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : ""),
                NamedTextColor.DARK_GRAY));
        h.inv = inv;
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        inv.setItem(4, item(Material.BELL, Component.text("Twoje powiadomienia", NamedTextColor.GOLD), List.of(
                Component.text("Kliknij, żeby włączyć albo wyłączyć.", NamedTextColor.GRAY),
                Component.text("Zmiana działa od razu i zostaje na stałe.", NamedTextColor.GRAY))));

        int slot = 9;
        for (int i = page * PER_PAGE; i < Math.min(types.size(), (page + 1) * PER_PAGE); i++) {
            MessageType t = types.get(i);
            boolean global = m.isEnabled(t.key());
            boolean on = global && !m.isMuted(p.getUniqueId(), t.key());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(PLUGIN_NAMES.getOrDefault(t.plugin(), t.plugin()), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            if (!global) lore.add(Component.text("Wyłączone przez administrację", NamedTextColor.GRAY));
            else lore.add(on ? Component.text("✔ Widzisz to", NamedTextColor.YELLOW) : Component.text("✖ Wyciszone", NamedTextColor.GRAY));
            if (global) lore.add(Component.text(on ? "Kliknij, żeby wyciszyć" : "Kliknij, żeby włączyć", NamedTextColor.DARK_GRAY));
            Material icon = !global ? Material.BARRIER : on ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(slot, item(icon, Component.text(t.description(), on ? NamedTextColor.WHITE : NamedTextColor.GRAY), lore));
            if (global) h.slots.put(slot, t.key());
            slot++;
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, Component.text("◀ Poprzednia strona", NamedTextColor.YELLOW), List.of()));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, Component.text("Następna strona ▶", NamedTextColor.YELLOW), List.of()));
        p.openInventory(inv);
    }

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.itemName(name);
        meta.lore(lore.stream().map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        if (e.getSlot() == 45 && h.page > 0) {
            open(p, h.page - 1);
            return;
        }
        if (e.getSlot() == 53) {
            open(p, h.page + 1);
            return;
        }
        String key = h.slots.get(e.getSlot());
        if (key == null) return;
        Messages m = SunCore.messages();
        boolean muted = m.isMuted(p.getUniqueId(), key);
        m.setMuted(p.getUniqueId(), key, !muted);
        p.playSound(p, Sound.UI_BUTTON_CLICK, 0.6f, muted ? 1.4f : 0.8f);
        open(p, h.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }
}
