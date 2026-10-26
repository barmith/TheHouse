package aurick.opsec.mod.lang;

import aurick.opsec.mod.Opsec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;















public final class OpsecLang {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final String PATH_PREFIX = "/assets/opsec/opseclang/";

    private static final Map<String, String> fallback = loadLocale(DEFAULT_LOCALE);
    private static volatile Map<String, String> current = Collections.emptyMap();
    private static volatile String currentLocale = DEFAULT_LOCALE;

    private OpsecLang() {}

    







    public static void reload(List<String> filenames) {
        String locale = DEFAULT_LOCALE;
        if (filenames != null) {
            for (String name : filenames) {
                if (name != null && !DEFAULT_LOCALE.equals(name)) {
                    locale = name;
                    break;
                }
            }
        }
        if (locale.equals(currentLocale)) return;
        current = loadLocale(locale);
        currentLocale = locale;
        Opsec.LOGGER.debug("[OpSec] OpsecLang reloaded for locale '{}' ({} entries)", locale, current.size());
    }

    



    public static String tr(String key, Object... args) {
        String template = current.get(key);
        if (template == null) template = fallback.get(key);
        if (template == null) return key;
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    




    public static Component component(String key, Object... args) {
        return Component.literal(tr(key, args));
    }

    private static Map<String, String> loadLocale(String locale) {
        String path = PATH_PREFIX + locale + ".json";
        try (InputStream in = OpsecLang.class.getResourceAsStream(path)) {
            if (in == null) return Collections.emptyMap();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> map = new HashMap<>(json.size());
                for (String key : json.keySet()) {
                    map.put(key, json.get(key).getAsString());
                }
                return map;
            }
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] OpsecLang failed to load {}: {}", path, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
