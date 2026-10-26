package com.thehouse.paperutility;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;


public final class ModConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("thehouse.properties");

    private ModConfig() {}

    public static void load() {
        boolean simulation = false;
        boolean hud = true;
        WinningSide side = WinningSide.RIGHT;
        boolean blackjackEnabled = false;
        boolean blackjackHud = true;
        boolean fiftyFiftyMode = false;
        boolean arrowEnabled = false;
        boolean arrowHud = true;
        boolean arrowMasking = false;
        boolean arrowDesiredHostWin = true;
        int arrowTargetX = 0;
        int arrowTargetZ = 1;
        BlockPos arrowCenter = null;

        Properties properties = new Properties();
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
                simulation = Boolean.parseBoolean(properties.getProperty("simulation", "false"));
                hud = Boolean.parseBoolean(properties.getProperty("hud", "true"));

                String rawSide = properties.getProperty("winningSide", "RIGHT")
                        .trim()
                        .toUpperCase(Locale.ROOT);
                side = "LEFT".equals(rawSide) ? WinningSide.LEFT : WinningSide.RIGHT;

                blackjackEnabled = Boolean.parseBoolean(properties.getProperty("blackjackEnabled", "false"));
                blackjackHud = Boolean.parseBoolean(properties.getProperty("blackjackHud", "true"));
                fiftyFiftyMode = Boolean.parseBoolean(properties.getProperty("fiftyFiftyMode", "false"));
                arrowEnabled = Boolean.parseBoolean(properties.getProperty("arrowEnabled", "false"));
                arrowHud = Boolean.parseBoolean(properties.getProperty("arrowHud", "true"));
                arrowMasking = Boolean.parseBoolean(properties.getProperty("arrowMasking", "false"));
                
                arrowDesiredHostWin = true;
                arrowTargetX = parseInt(properties.getProperty("arrowTargetX"), 0);
                arrowTargetZ = parseInt(properties.getProperty("arrowTargetZ"), 1);
                String rawCenter = properties.getProperty("arrowBoardCenter");
                if (rawCenter != null) {
                    String[] parts = rawCenter.split(",");
                    if (parts.length == 3) {
                        try { arrowCenter = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
                        catch (NumberFormatException ignored) { arrowCenter = null; }
                    }
                }
            } catch (IOException ignored) {
                
            }
        }

        PaperItemCatalog.resetDefaults();
        for (int slot = 1; slot <= 9; slot++) {
            String rawItem = properties.getProperty("slotItem" + slot);
            if (rawItem != null) {
                PaperItemCatalog.setItemById(slot, rawItem);
            }
        }

        PaperSlotProfiles.load(properties);
        com.thehouse.paperutility.render.RenderModules.load(properties);

        HudColors.applyPersisted(
                HudColor.parse(properties.getProperty("hudColor.paperRight"), HudColor.BLUE),
                HudColor.parse(properties.getProperty("hudColor.paperLeft"), HudColor.RED),
                HudColor.parse(properties.getProperty("hudColor.blackjackViewer"), HudColor.GREEN),
                HudColor.parse(properties.getProperty("hudColor.blackjackHost"), HudColor.ORANGE),
                HudColor.parse(properties.getProperty("hudColor.blackjackLocked"), HudColor.WHITE),
                HudColor.parse(properties.getProperty("hudColor.blackjackFailure"), HudColor.RED)
        );

        ModState.applyPersistedSettings(simulation, hud, side, fiftyFiftyMode);
        BlackjackState.applyPersisted(blackjackEnabled, blackjackHud);
        ArrowState.applyPersisted(arrowEnabled, arrowHud, arrowCenter, arrowMasking, arrowTargetX, arrowTargetZ, arrowDesiredHostWin);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("simulation", Boolean.toString(ModState.isSimulationEnabled()));
        properties.setProperty("hud", Boolean.toString(ModState.isHudEnabled()));
        properties.setProperty("winningSide", ModState.getWinningSide().name());
        properties.setProperty("fiftyFiftyMode", Boolean.toString(ModState.isFiftyFiftyModeEnabled()));
        properties.setProperty("blackjackEnabled", Boolean.toString(BlackjackState.isEnabled()));
        properties.setProperty("blackjackHud", Boolean.toString(BlackjackState.isHudEnabled()));
        properties.setProperty("arrowEnabled", Boolean.toString(ArrowState.isEnabled()));
        properties.setProperty("arrowHud", Boolean.toString(ArrowState.isHudEnabled()));
        properties.setProperty("arrowMasking", Boolean.toString(ArrowState.isMaskingEnabled()));
        properties.setProperty("arrowDesiredHostWin", "true");
        properties.setProperty("arrowTargetX", Integer.toString(ArrowState.getMaskTargetX()));
        properties.setProperty("arrowTargetZ", Integer.toString(ArrowState.getMaskTargetZ()));
        if (ArrowState.getBoardCenter() != null) {
            BlockPos c = ArrowState.getBoardCenter();
            properties.setProperty("arrowBoardCenter", c.getX() + "," + c.getY() + "," + c.getZ());
        }
        properties.setProperty("hudColor.paperRight", HudColors.paperRight().name());
        properties.setProperty("hudColor.paperLeft", HudColors.paperLeft().name());
        properties.setProperty("hudColor.blackjackViewer", HudColors.blackjackViewer().name());
        properties.setProperty("hudColor.blackjackHost", HudColors.blackjackHost().name());
        properties.setProperty("hudColor.blackjackLocked", HudColors.blackjackLocked().name());
        properties.setProperty("hudColor.blackjackFailure", HudColors.blackjackFailure().name());
        for (int slot = 1; slot <= 9; slot++) {
            properties.setProperty("slotItem" + slot, PaperItemCatalog.getItemId(slot));
        }

        PaperSlotProfiles.store(properties);
        com.thehouse.paperutility.render.RenderModules.store(properties);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, "TheHouse 1.0 settings");
            }
        } catch (IOException ignored) {
            
        }
    }
}
