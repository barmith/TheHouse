package com.thehouse.paperutility;

import java.util.Locale;


public enum HudColor {
    WHITE("White", 0xFFFFFF),
    GRAY("Gray", 0x9CA3AF),
    RED("Red", 0xEF4444),
    ORANGE("Orange", 0xF97316),
    YELLOW("Yellow", 0xEAB308),
    LIME("Lime", 0x84CC16),
    GREEN("Green", 0x22C55E),
    CYAN("Cyan", 0x06B6D4),
    LIGHT_BLUE("Light Blue", 0x38BDF8),
    BLUE("Blue", 0x3B82F6),
    PURPLE("Purple", 0x8B5CF6),
    PINK("Pink", 0xEC4899);

    private final String displayName;
    private final int rgb;

    HudColor(String displayName, int rgb) {
        this.displayName = displayName;
        this.rgb = rgb;
    }

    public String displayName() {
        return displayName;
    }

    
    public int opaqueArgb() {
        return 0xFF000000 | rgb;
    }

    
    public int hudArgb() {
        return 0x80000000 | rgb;
    }

    public HudColor next() {
        HudColor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public HudColor previous() {
        HudColor[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static HudColor parse(String raw, HudColor fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
