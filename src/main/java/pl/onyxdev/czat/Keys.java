package pl.onyxdev.czat;

import org.bukkit.plugin.Plugin;
import pl.onyxdev.suncore.Messages;
import pl.onyxdev.suncore.SunCore;

public final class Keys {
    private Keys() {
    }

    public static final String PRIVATE = "czat.prywatna";
    public static final String MENTION = "czat.wzmianka";
    public static final String SPY = "czat.podglad";
    public static final String INFO = "czat.info";
    public static final String MUTE = "czat.wyciszenie";

    public static void register(Plugin p) {
        Messages m = SunCore.messages();
        m.register(p, PRIVATE, "Prywatne wiadomości (/msg)", true, false);
        m.register(p, MENTION, "Pasek „X wspomniał o tobie” przy @wzmiance", true, true);
        m.register(p, SPY, "Podgląd cudzych prywatnych wiadomości (tylko admin)", true, true);
        m.register(p, INFO, "Komunikaty czatu (anty-spam, ignorowanie, rangi)", true, false);
        m.register(p, MUTE, "Informacja, że zostałeś wyciszony lub odciszony", true, false);
    }
}
