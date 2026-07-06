package dev.dimitra.bot;

import io.github.cdimascio.dotenv.Dotenv;

public final class Env {
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private Env() {
    }

    public static String get(String key) {
        String value = DOTENV.get(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null ? null : value.trim();
    }

    public static String get(String key, String def) {
        String value = get(key);
        return (value == null || value.isBlank()) ? def : value;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing env: " + key);
        }
        return value;
    }

    public static boolean getBoolean(String key, boolean def) {
        String value = get(key);
        if (value == null) {
            return def;
        }
        return value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("1")
                || value.equalsIgnoreCase("yes");
    }

    public static int getInt(String key, int def) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    public static double getDouble(String key, double def) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
