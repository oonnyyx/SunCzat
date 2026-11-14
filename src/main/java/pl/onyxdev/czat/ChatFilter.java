package pl.onyxdev.czat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatFilter {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}@$]+");

    private long minGapMs;
    private int maxMsgs;
    private long windowMs;
    private long repeatMs;
    private int capsPercent;
    private int capsMinLen;
    private boolean filterOn;
    private List<String> stems = List.of();
    private List<String> exceptions = List.of();

    private final Map<UUID, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTextAt = new ConcurrentHashMap<>();

    void load(ConfigurationSection spam, ConfigurationSection filter) {
        minGapMs = spam.getLong("min-odstep-ms", 1000);
        maxMsgs = spam.getInt("max-wiadomosci", 4);
        windowMs = spam.getLong("okno-sekund", 6) * 1000;
        repeatMs = spam.getLong("powtorka-sekund", 30) * 1000;
        capsPercent = spam.getInt("caps-procent", 70);
        capsMinLen = spam.getInt("caps-min-dlugosc", 8);
        filterOn = filter.getBoolean("wlaczony", true);
        stems = filter.getStringList("slowa").stream().map(ChatFilter::normalize).toList();
        exceptions = filter.getStringList("wyjatki").stream().map(ChatFilter::normalize).toList();
    }

    String spamCheck(UUID id, String text) {
        long now = System.currentTimeMillis();
        Deque<Long> q = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
            if (!q.isEmpty() && now - q.peekLast() < minGapMs) return "Zwolnij trochę — piszesz za szybko.";
            if (q.size() >= maxMsgs) return "Za dużo wiadomości naraz. Odczekaj chwilę.";
            String norm = normalize(text);
            String prev = lastText.get(id);
            Long prevAt = lastTextAt.get(id);
            if (prev != null && prev.equals(norm) && prevAt != null && now - prevAt < repeatMs) {
                return "Nie powtarzaj tej samej wiadomości.";
            }
            q.addLast(now);
            lastText.put(id, norm);
            lastTextAt.put(id, now);
        }
        return null;
    }

    void forget(UUID id) {
        recent.remove(id);
        lastText.remove(id);
        lastTextAt.remove(id);
    }

    String fixCaps(String text) {
        int letters = 0, upper = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters >= capsMinLen && upper * 100 > letters * capsPercent) return text.toLowerCase(Locale.ROOT);
        return text;
    }

    String censor(String text) {
        if (!filterOn || stems.isEmpty()) return text;
        Matcher m = WORD.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String word = m.group();
            out.append(bad(word) ? "*".repeat(word.length()) : word);
            last = m.end();
        }
        out.append(text.substring(last));
        return out.toString();
    }

    private boolean bad(String word) {
        String n = normalize(word);
        for (String ex : exceptions) if (n.contains(ex)) return false;
        for (String s : stems) if (!s.isEmpty() && n.contains(s)) return true;
        return false;
    }

    static String normalize(String s) {
        StringBuilder b = new StringBuilder();
        char prev = 0;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            char r = switch (c) {
                case 'ą' -> 'a';
                case 'ć' -> 'c';
                case 'ę' -> 'e';
                case 'ł' -> 'l';
                case 'ń' -> 'n';
                case 'ó', '0' -> 'o';
                case 'ś', '$', '5' -> 's';
                case 'ż', 'ź' -> 'z';
                case '1', '!' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '7' -> 't';
                default -> c;
            };
            if (!Character.isLetter(r)) continue;
            if (r == prev) continue;
            b.append(r);
            prev = r;
        }
        return b.toString();
    }
}
