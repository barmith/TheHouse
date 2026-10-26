package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;





public class CrackedAccount implements Account {

    private final String username;
    private final String uuid;
    private String lastError = null;

    public CrackedAccount(String username) {
        this.username = username;
        this.uuid = generateOfflineUuid(username).toString();
    }

    private static UUID generateOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean login() {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftAccessor accessor = (MinecraftAccessor) mc;

                        User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    "",
                    Optional.empty(),
                    Optional.empty()
            );
            
            accessor.opsec$setUser(newUser);

            
            accessor.opsec$setUserApiService(UserApiService.OFFLINE);

            
            ProfileKeyPairManager profileKeyPairManager = ProfileKeyPairManager.create(
                    UserApiService.OFFLINE,
                    newUser,
                    mc.gameDirectory.toPath()
            );
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);

            
                        PlayerSocialManager socialManager = new PlayerSocialManager(mc, UserApiService.OFFLINE);
                        accessor.opsec$setPlayerSocialManager(socialManager);

            this.lastError = null;
            Opsec.LOGGER.info("[OpSec] Successfully logged in as offline account: {}", username);
            return true;

        } catch (Exception e) {
            this.lastError = "Login error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] Failed to login as offline account: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public boolean hasValidInfo() {
        return username != null && !username.isBlank();
    }

    @Override
    public boolean isCracked() {
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "cracked");
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        return json;
    }

    public static CrackedAccount fromJson(JsonObject json) {
        String username = json.has("username") ? json.get("username").getAsString() : "";
        return new CrackedAccount(username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrackedAccount other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}
