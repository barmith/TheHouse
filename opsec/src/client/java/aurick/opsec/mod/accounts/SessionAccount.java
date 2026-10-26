package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConstants;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;





public class SessionAccount implements Account {
    
    


    public enum ValidationResult {
        VALID,       
        INVALID,     
        RATE_LIMITED,
        ERROR        
    }
    
    private String accessToken;
    private String refreshToken; 
    private String username;
    private String uuid;
    private long lastValidated;
    private boolean valid = true; 
    private String lastError = null; 

    
    
    private static final String[] AZURE_CLIENT_IDS = {
            OpsecConstants.AzureClientIds.MINECRAFT_PUBLIC,
            OpsecConstants.AzureClientIds.XBOX_APP,
            OpsecConstants.AzureClientIds.MINECRAFT_IOS,
            OpsecConstants.AzureClientIds.LAUNCHER_CLIENT
    };

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
            .build();
    
    public SessionAccount(String accessToken) {
        this.accessToken = accessToken;
        this.refreshToken = null;
        this.username = "";
        this.uuid = "";
        this.lastValidated = 0;
    }
    
    public SessionAccount(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = "";
        this.uuid = "";
        this.lastValidated = 0;
    }
    
    public SessionAccount(String accessToken, String username, String uuid) {
        this.accessToken = accessToken;
        this.refreshToken = null;
        this.username = username;
        this.uuid = uuid;
        this.lastValidated = System.currentTimeMillis();
    }
    
    




    public boolean fetchInfo() {
        return fetchInfoWithResult() == ValidationResult.VALID;
    }
    
    




    public ValidationResult fetchInfoWithRetry() {
        ValidationResult result = null;
        long retryAfterMs = 0; 
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            var validationResult = fetchInfoWithResultAndRetryAfter();
            result = validationResult.result;
            retryAfterMs = validationResult.retryAfterMs;
            
            
            if (result == ValidationResult.VALID || result == ValidationResult.INVALID) {
                return result;
            }
            
            
            if (attempt < MAX_RETRIES) {
                long delayMs = calculateRetryDelay(attempt, result, retryAfterMs);
                Opsec.LOGGER.info("[OpSec] Validation attempt {} failed ({}), retrying in {}ms...", 
                        attempt, result, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result != null ? result : ValidationResult.ERROR;
    }
    
    



    private long calculateRetryDelay(int attempt, ValidationResult result, long retryAfterMs) {
        
        if (retryAfterMs > 0) {
            long jitter = (long)(Math.random() * 1000); 
            return retryAfterMs + jitter;
        }
        
        
        long baseDelay = (result == ValidationResult.RATE_LIMITED) 
                ? RATE_LIMIT_BASE_DELAY_MS 
                : BASE_RETRY_DELAY_MS;
        
        long exponentialDelay = baseDelay * (1L << (attempt - 1)); 
        long jitter = (long)(Math.random() * baseDelay); 
        
        return exponentialDelay + jitter;
    }
    
    


    private static class ValidationResultWithRetry {
        final ValidationResult result;
        final long retryAfterMs;
        
        ValidationResultWithRetry(ValidationResult result, long retryAfterMs) {
            this.result = result;
            this.retryAfterMs = retryAfterMs;
        }
    }
    
    



    public ValidationResult fetchInfoWithResult() {
        return fetchInfoWithResultAndRetryAfter().result;
    }
    
    



    private ValidationResultWithRetry fetchInfoWithResultAndRetryAfter() {
        if (accessToken == null || accessToken.isBlank()) {
            return new ValidationResultWithRetry(ValidationResult.INVALID, 0);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.PROFILE_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            long retryAfterMs = extractRetryAfter(response);
            
            if (statusCode == 429) {
                Opsec.LOGGER.warn("[OpSec] Rate limited while validating token for {} (Retry-After: {}ms)", 
                        username.isEmpty() ? "unknown" : username, retryAfterMs);
                return new ValidationResultWithRetry(ValidationResult.RATE_LIMITED, retryAfterMs);
            }
            
            if (statusCode == 401 || statusCode == 403) {
                Opsec.LOGGER.warn("[OpSec] Token invalid/expired for {}: HTTP {}", 
                        username.isEmpty() ? "unknown" : username, statusCode);
                return new ValidationResultWithRetry(ValidationResult.INVALID, 0);
            }
            
            if (statusCode != 200) {
                Opsec.LOGGER.warn("[OpSec] Failed to validate token: HTTP {}", statusCode);
                return new ValidationResultWithRetry(ValidationResult.ERROR, retryAfterMs);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            
            if (!json.has("id") || !json.has("name")) {
                Opsec.LOGGER.warn("[OpSec] Invalid profile response: missing id or name");
                return new ValidationResultWithRetry(ValidationResult.ERROR, 0);
            }
            
            this.uuid = formatUuid(json.get("id").getAsString());
            this.username = json.get("name").getAsString();
            this.lastValidated = System.currentTimeMillis();
            
            Opsec.LOGGER.info("[OpSec] Validated account: {} ({})", username, uuid);
            return new ValidationResultWithRetry(ValidationResult.VALID, 0);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to validate token: {}", e.getMessage());
            return new ValidationResultWithRetry(ValidationResult.ERROR, 0);
        }
    }
    
    



    private long extractRetryAfter(HttpResponse<?> response) {
        try {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter == null || retryAfter.isEmpty()) {
                return 0;
            }
            
            
            try {
                long seconds = Long.parseLong(retryAfter);
                return seconds * 1000; 
            } catch (NumberFormatException e) {
                
                Opsec.LOGGER.debug("[OpSec] Retry-After header format not recognized: {}", retryAfter);
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static final int MAX_RETRIES = OpsecConstants.Retry.MAX_RETRIES;
    private static final long BASE_RETRY_DELAY_MS = OpsecConstants.Retry.BASE_RETRY_DELAY_MS;
    private static final long RATE_LIMIT_BASE_DELAY_MS = OpsecConstants.Retry.RATE_LIMIT_BASE_DELAY_MS;
    
    






    @Override
    public boolean login() {
        if (accessToken == null || accessToken.isBlank()) {
            this.lastError = "Missing token";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing token");
            return false;
        }
        
        
        ValidationResult validationResult = fetchInfoWithRetry();
        if (validationResult != ValidationResult.VALID) {
            
            if (validationResult == ValidationResult.INVALID && hasRefreshToken()) {
                Opsec.LOGGER.info("[OpSec] Token invalid, attempting refresh...");
                if (refreshAccessToken()) {
                    
                    validationResult = fetchInfoWithResult();
                }
            }
            
            
            if (validationResult != ValidationResult.VALID) {
                this.valid = (validationResult != ValidationResult.INVALID);
                switch (validationResult) {
                    case INVALID -> this.lastError = hasRefreshToken() ? "Token refresh failed" : "Token expired or invalid";
                    case RATE_LIMITED -> this.lastError = "Rate limited - try again later";
                    case ERROR -> this.lastError = "Network error - check connection";
                    default -> this.lastError = "Validation failed";
                }
                Opsec.LOGGER.error("[OpSec] Cannot login: token validation failed ({})", validationResult);
                return false;
            }
        }
        this.valid = true;
        this.lastError = null; 
        
        if (uuid == null || uuid.isBlank()) {
            this.lastError = "Missing UUID after validation";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing UUID after validation");
            return false;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            
            
            
                        User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    accessToken,
                    Optional.empty(),
                    Optional.empty()
            );
                        
            
            accessor.opsec$setUser(newUser);
            
            
            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(mc.getProxy());
            
            
            UserApiService userApiService = authService.createUserApiService(accessToken);
            accessor.opsec$setUserApiService(userApiService);
            
            
            ProfileKeyPairManager profileKeyPairManager = ProfileKeyPairManager.create(
                    userApiService, 
                    newUser, 
                    mc.gameDirectory.toPath()
            );
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);
            
            
                        PlayerSocialManager socialManager = new PlayerSocialManager(mc, userApiService);
                        accessor.opsec$setPlayerSocialManager(socialManager);
            
            Opsec.LOGGER.info("[OpSec] Successfully logged in as: {}", username);
            return true;
            
        } catch (Exception e) {
            this.lastError = "Login error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] Failed to login: {}", e.getMessage(), e);
            return false;
        }
    }
    
    


    private String formatUuid(String undashed) {
        if (undashed == null || undashed.length() != 32) {
            return undashed;
        }
        return String.format("%s-%s-%s-%s-%s",
                undashed.substring(0, 8),
                undashed.substring(8, 12),
                undashed.substring(12, 16),
                undashed.substring(16, 20),
                undashed.substring(20, 32));
    }
    
    
    @Override public String getUsername() { return username; }
    @Override public String getUuid() { return uuid; }
    public long getLastValidated() { return lastValidated; }
    @Override public boolean isValid() { return valid; }
    @Override public String getLastError() { return lastError; }
    public boolean hasRefreshToken() { return refreshToken != null && !refreshToken.isBlank(); }

    @Override
    public boolean hasValidInfo() {
        return username != null && !username.isBlank() && uuid != null && !uuid.isBlank();
    }

    @Override
    public boolean isCracked() {
        return false;
    }
    
    





    public boolean refreshAccessToken() {
        if (!hasRefreshToken()) {
            Opsec.LOGGER.debug("[OpSec] No refresh token available for {}", username);
            return false;
        }
        
        long lastRetryAfterMs = 0;
        boolean wasRateLimited = false;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Opsec.LOGGER.info("[OpSec] Attempting to refresh token for {} (attempt {})", username, attempt);
                
                
                var msResult = refreshMicrosoftTokenWithRetry();
                if (msResult.token == null) {
                    lastRetryAfterMs = msResult.retryAfterMs;
                    wasRateLimited = msResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt, 
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR, 
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] MS token refresh failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed to refresh Microsoft token";
                    return false;
                }
                
                
                var xblResult = authenticateXboxLiveWithRetry(msResult.token);
                if (xblResult.token == null) {
                    lastRetryAfterMs = xblResult.retryAfterMs;
                    wasRateLimited = xblResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] Xbox Live auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed Xbox Live auth";
                    return false;
                }
                
                
                var xstsResult = authenticateXSTSWithRetry(xblResult.token);
                if (xstsResult.result == null) {
                    lastRetryAfterMs = xstsResult.retryAfterMs;
                    wasRateLimited = xstsResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] XSTS auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed XSTS auth";
                    return false;
                }
                String xstsToken = xstsResult.result[0];
                String userHash = xstsResult.result[1];
                
                
                var mcResult = authenticateMinecraftWithRetry(xstsToken, userHash);
                if (mcResult.token == null) {
                    lastRetryAfterMs = mcResult.retryAfterMs;
                    wasRateLimited = mcResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] Minecraft auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed Minecraft auth";
                    return false;
                }
                
                
                this.accessToken = mcResult.token;
                this.lastValidated = System.currentTimeMillis();
                this.valid = true;
                this.lastError = null;
                
                Opsec.LOGGER.info("[OpSec] Successfully refreshed token for {}", username);
                return true;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.lastError = "Refresh interrupted";
                return false;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long delay = calculateRetryDelay(attempt, ValidationResult.ERROR, 0);
                    Opsec.LOGGER.warn("[OpSec] Refresh attempt {} failed: {}, retrying in {}ms...", 
                            attempt, e.getMessage(), delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    this.lastError = "Refresh error: " + e.getMessage();
                    Opsec.LOGGER.error("[OpSec] Failed to refresh token: {}", e.getMessage());
                }
            }
        }
        return false;
    }
    
    


    private static class TokenResult {
        final String token;
        final long retryAfterMs;
        final boolean wasRateLimited;
        
        TokenResult(String token, long retryAfterMs, boolean wasRateLimited) {
            this.token = token;
            this.retryAfterMs = retryAfterMs;
            this.wasRateLimited = wasRateLimited;
        }
    }
    
    private static class XSTSResult {
        final String[] result;
        final long retryAfterMs;
        final boolean wasRateLimited;
        
        XSTSResult(String[] result, long retryAfterMs, boolean wasRateLimited) {
            this.result = result;
            this.retryAfterMs = retryAfterMs;
            this.wasRateLimited = wasRateLimited;
        }
    }
    
    private TokenResult refreshMicrosoftTokenWithRetry() {
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        
        for (String clientId : AZURE_CLIENT_IDS) {
            var result = tryRefreshWithClientIdAndRetry(clientId);
            if (result.token != null) {
                Opsec.LOGGER.info("[OpSec] Token refresh succeeded with client ID: {}", clientId);
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        Opsec.LOGGER.warn("[OpSec] Token refresh failed with all known client IDs");
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryRefreshWithClientIdAndRetry(String clientId) {
        
        String[] scopes = {
                "XboxLive.signin%20XboxLive.offline_access",
                "XboxLive.signin%20offline_access", 
                "service::user.auth.xboxlive.com::MBI_SSL",
                "XboxLive.signin"
        };
        
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        for (String scope : scopes) {
            TokenResult result = tryRefreshWithParamsAndRetry(clientId, scope);
            if (result.token != null) {
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryRefreshWithParamsAndRetry(String clientId, String scope) {
        try {
            String body = "client_id=" + clientId +
                    "&refresh_token=" + refreshToken +
                    "&grant_type=refresh_token" +
                    "&redirect_uri=https://login.live.com/oauth20_desktop.srf" +
                    "&scope=" + scope;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.MS_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.debug("[OpSec] Refresh with client {} scope {} failed: HTTP {}", 
                        clientId, scope, response.statusCode());
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            
            
            if (json.has("refresh_token")) {
                this.refreshToken = json.get("refresh_token").getAsString();
            }
            
            Opsec.LOGGER.debug("[OpSec] Refresh succeeded with client {} scope {}", clientId, scope);
            return new TokenResult(json.get("access_token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Refresh with client {} scope {} error: {}", clientId, scope, e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    private TokenResult authenticateXboxLiveWithRetry(String msToken) {
        
        String[] ticketFormats = {
                "d=" + msToken,     
                "t=" + msToken,     
                msToken             
        };
        
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        for (String rpsTicket : ticketFormats) {
            TokenResult result = tryXboxAuthWithTicketAndRetry(rpsTicket);
            if (result.token != null) {
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryXboxAuthWithTicketAndRetry(String rpsTicket) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");
            
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", rpsTicket);
            body.add("Properties", properties);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.XBOX_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.debug("[OpSec] Xbox Live auth with ticket format failed: HTTP {} - {}", 
                        response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            Opsec.LOGGER.debug("[OpSec] Xbox Live auth succeeded");
            return new TokenResult(json.get("Token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Xbox Live auth error: {}", e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    private XSTSResult authenticateXSTSWithRetry(String xblToken) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            body.addProperty("TokenType", "JWT");
            
            JsonObject properties = new JsonObject();
            properties.addProperty("SandboxId", "RETAIL");
            com.google.gson.JsonArray userTokens = new com.google.gson.JsonArray();
            userTokens.add(xblToken);
            properties.add("UserTokens", userTokens);
            body.add("Properties", properties);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.XSTS_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.warn("[OpSec] XSTS auth failed: HTTP {}", response.statusCode());
                return new XSTSResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String token = json.get("Token").getAsString();
            String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0).getAsJsonObject()
                    .get("uhs").getAsString();
            
            return new XSTSResult(new String[]{token, userHash}, 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] XSTS auth error: {}", e.getMessage());
            return new XSTSResult(null, 0, false);
        }
    }
    
    private TokenResult authenticateMinecraftWithRetry(String xstsToken, String userHash) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.MC_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.warn("[OpSec] Minecraft auth failed: HTTP {}", response.statusCode());
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return new TokenResult(json.get("access_token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Minecraft auth error: {}", e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    




    public boolean revalidate() {
        return revalidateWithResult() == ValidationResult.VALID;
    }
    
    





    public ValidationResult revalidateWithResult() {
        ValidationResult result = fetchInfoWithResult();
        
        
        if (result == ValidationResult.VALID) {
            this.valid = true;
        } else if (result == ValidationResult.INVALID) {
            this.valid = false;
        }
        
        
        return result;
    }
    
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "session");
        json.addProperty("accessToken", accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            json.addProperty("refreshToken", refreshToken);
        }
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        json.addProperty("lastValidated", lastValidated);
        json.addProperty("valid", valid);
        return json;
    }
    
    public static SessionAccount fromJson(JsonObject json) {
        String token = json.has("accessToken") ? json.get("accessToken").getAsString() : "";
        String username = json.has("username") ? json.get("username").getAsString() : "";
        String uuid = json.has("uuid") ? json.get("uuid").getAsString() : "";
        
        SessionAccount account = new SessionAccount(token, username, uuid);
        if (json.has("refreshToken")) {
            account.refreshToken = json.get("refreshToken").getAsString();
        }
        if (json.has("lastValidated")) {
            account.lastValidated = json.get("lastValidated").getAsLong();
        }
        if (json.has("valid")) {
            account.valid = json.get("valid").getAsBoolean();
        }
        return account;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionAccount other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }
    
    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}
