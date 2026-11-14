package pl.onyxdev.czat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

record Rank(String id, String name, Component prefix, Component badge, TextColor nickColor, TextColor messageColor, int weight,
            boolean isDefault, List<String> permissions) {
    static final Key BADGE_FONT = Key.key("suncraft", "rangi");

    static Rank load(String id, ConfigurationSection s) {
        MiniMessage mm = MiniMessage.miniMessage();
        TextColor nick = TextColor.fromHexString(s.getString("kolor-nicku", "#ffffff"));
        TextColor msg = TextColor.fromHexString(s.getString("kolor-wiadomosci", "#ffffff"));
        String glyph = s.getString("obrazek", "");
        Component badge = glyph.isEmpty() ? null
                : Component.text(glyph).font(BADGE_FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
        return new Rank(id, s.getString("nazwa", id), mm.deserialize(s.getString("prefiks", "[" + id + "]")), badge,
                nick != null ? nick : TextColor.color(0xFFFFFF), msg != null ? msg : TextColor.color(0xFFFFFF),
                s.getInt("waga", 0), s.getBoolean("domyslna", false), s.getStringList("uprawnienia"));
    }

    Component display(boolean hasPack) {
        return hasPack && badge != null ? badge : prefix;
    }

    Component shared() {
        return badge != null ? badge : prefix;
    }
}
