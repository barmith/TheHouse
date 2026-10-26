package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;





public class AccountManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ACCOUNTS_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("opsec-accounts.json");
    
    private static volatile AccountManager INSTANCE;
    private static final Object LOCK = new Object();
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OpSec-Account-Refresh");
        t.setDaemon(true);
        return t;
    });

    private final List<Account> accounts = new CopyOnWriteArrayList<>();
    private String activeAccountUuid = null;

    
    private Account originalAccount = null;

    
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    
    private AccountManager() {
        load();
        captureOriginalAccount();
    }
    
    private void captureOriginalAccount() {
        User user = Minecraft.getInstance().getUser();
        if (user != null) {
            originalAccount = new SessionAccount(
                    user.getAccessToken(),
                    user.getName(),
                    user.getProfileId().toString()
            );
            Opsec.LOGGER.debug("[OpSec] Captured original account: {}", user.getName());
        }
    }
    
    public static AccountManager getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new AccountManager();
                }
            }
        }
        return INSTANCE;
    }
    
    



    public void add(Account account) {
        if (account == null || !account.hasValidInfo()) {
            Opsec.LOGGER.warn("[OpSec] Cannot add invalid account");
            return;
        }
        
        
        accounts.removeIf(a -> a.getUuid().equals(account.getUuid()));
        accounts.add(account);
        save();
        
        Opsec.LOGGER.info("[OpSec] Added account: {} ({})", account.getUsername(), account.getUuid());
    }
    
    



    public void remove(Account account) {
        if (account == null) return;
        
        boolean wasActive = account.getUuid().equals(activeAccountUuid);
        
        if (accounts.removeIf(a -> a.getUuid().equals(account.getUuid()))) {
            
            if (wasActive) {
                logout();
            }
            save();
            Opsec.LOGGER.info("[OpSec] Removed account: {} ({})", account.getUsername(), account.getUuid());
        }
    }
    
    


    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }
    
    


    public Optional<Account> getByUuid(String uuid) {
        if (uuid == null) return Optional.empty();
        return accounts.stream()
                .filter(a -> uuid.equals(a.getUuid()))
                .findFirst();
    }
    
    


    public String getActiveAccountUuid() {
        return activeAccountUuid;
    }
    
    


    public void setActiveAccountUuid(String uuid) {
        this.activeAccountUuid = uuid;
        save();
    }
    
    


    public boolean isActive(Account account) {
        if (account == null || activeAccountUuid == null) return false;
        return activeAccountUuid.equals(account.getUuid());
    }
    
    


    public int size() {
        return accounts.size();
    }
    
    


    public boolean isLoggedIntoAltAccount() {
        return activeAccountUuid != null;
    }
    
    


    public boolean logout() {
        if (originalAccount == null) {
            Opsec.LOGGER.warn("[OpSec] Cannot logout: no original account captured");
            return false;
        }
        
        if (originalAccount.login()) {
            activeAccountUuid = null;
            save();
            Opsec.LOGGER.info("[OpSec] Logged out to original account: {}", originalAccount.getUsername());
            return true;
        }
        return false;
    }
    
    


    public String exportToJson() {
        JsonObject json = new JsonObject();
        JsonArray accountsArray = new JsonArray();
        for (Account account : accounts) {
            accountsArray.add(account.toJson());
        }
        json.add("accounts", accountsArray);
        return GSON.toJson(json);
    }

    



    public int importFromJson(String jsonContent) {
        int imported = 0;
        try {
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            if (json.has("accounts")) {
                JsonArray accountsArray = json.getAsJsonArray("accounts");
                for (int i = 0; i < accountsArray.size(); i++) {
                    try {
                        JsonObject accountJson = accountsArray.get(i).getAsJsonObject();
                        Account account = Account.fromJson(accountJson);
                        if (account.hasValidInfo()) {
                            
                            boolean exists = accounts.stream()
                                    .anyMatch(a -> a.getUuid().equals(account.getUuid()));
                            if (!exists) {
                                accounts.add(account);
                                imported++;
                            }
                        }
                    } catch (Exception e) {
                        Opsec.LOGGER.warn("[OpSec] Failed to import account at index {}: {}", i, e.getMessage());
                    }
                }
            }
            if (imported > 0) {
                save();
                Opsec.LOGGER.info("[OpSec] Imported {} accounts", imported);
            }
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to parse import JSON: {}", e.getMessage());
        }
        return imported;
    }
    
    


    public boolean isRefreshing() {
        return isRefreshing.get();
    }
    
    





    public void refreshAllAccounts(java.util.function.BiConsumer<Integer, Integer> callback) {
        
        if (!isRefreshing.compareAndSet(false, true)) {
            Opsec.LOGGER.warn("[OpSec] Refresh already in progress, ignoring request");
            return;
        }
        
        REFRESH_EXECUTOR.execute(() -> {
            try {
                int valid = 0;
                int invalid = 0;
                int skipped = 0;
                
                for (int i = 0; i < accounts.size(); i++) {
                    Account account = accounts.get(i);

                    
                    if (account.isCracked()) {
                        valid++;
                        continue;
                    }

                    
                    if (i > 0) {
                        try {
                            Thread.sleep(OpsecConstants.Retry.ACCOUNT_REFRESH_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    SessionAccount.ValidationResult result = ((SessionAccount) account).revalidateWithResult();
                    switch (result) {
                        case VALID -> valid++;
                        case INVALID -> invalid++;
                        case RATE_LIMITED, ERROR -> {
                            skipped++;
                            
                        }
                    }
                }
                
                save();
                
                final int v = valid;
                final int inv = invalid;
                final int sk = skipped;
                
                if (sk > 0) {
                    Opsec.LOGGER.info("[OpSec] Refreshed accounts: {} valid, {} invalid, {} skipped (rate limited)", v, inv, sk);
                } else {
                    Opsec.LOGGER.info("[OpSec] Refreshed accounts: {} valid, {} invalid", v, inv);
                }
                
                if (callback != null) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> callback.accept(v, inv));
                }
            } finally {
                isRefreshing.set(false);
            }
        });
    }
    
    


    public void load() {
        if (!Files.exists(ACCOUNTS_PATH)) {
            Opsec.LOGGER.debug("[OpSec] No accounts file found, starting fresh");
            return;
        }
        
        try {
            String content = Files.readString(ACCOUNTS_PATH);
            if (content == null || content.trim().isEmpty()) {
                return;
            }
            
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            accounts.clear();
            
            if (json.has("accounts")) {
                JsonArray accountsArray = json.getAsJsonArray("accounts");
                for (int i = 0; i < accountsArray.size(); i++) {
                    try {
                        JsonObject accountJson = accountsArray.get(i).getAsJsonObject();
                        Account account = Account.fromJson(accountJson);
                        if (account.hasValidInfo()) {
                            accounts.add(account);
                        }
                    } catch (Exception e) {
                        Opsec.LOGGER.warn("[OpSec] Failed to load account at index {}: {}", i, e.getMessage());
                    }
                }
            }
            
            if (json.has("activeAccountUuid")) {
                activeAccountUuid = json.get("activeAccountUuid").getAsString();
                if (activeAccountUuid.isEmpty()) {
                    activeAccountUuid = null;
                }
            }
            
            Opsec.LOGGER.info("[OpSec] Loaded {} accounts", accounts.size());
            
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to load accounts: {}", e.getMessage());
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to parse accounts file: {}", e.getMessage());
        }
    }
    
    


    public void save() {
        try {
            JsonObject json = new JsonObject();
            
            JsonArray accountsArray = new JsonArray();
            for (Account account : accounts) {
                accountsArray.add(account.toJson());
            }
            json.add("accounts", accountsArray);

            json.addProperty("activeAccountUuid", activeAccountUuid != null ? activeAccountUuid : "");
            
            
            Files.createDirectories(ACCOUNTS_PATH.getParent());
            Path tempFile = ACCOUNTS_PATH.resolveSibling(ACCOUNTS_PATH.getFileName() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(json));
            Files.move(tempFile, ACCOUNTS_PATH, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            
            Opsec.LOGGER.debug("[OpSec] Saved {} accounts", accounts.size());
            
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to save accounts: {}", e.getMessage());
        }
    }
}
