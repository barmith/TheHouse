package aurick.opsec.mod.accounts;

import com.google.gson.JsonObject;




public interface Account {

    



    boolean login();

    String getUsername();

    String getUuid();

    boolean isValid();

    String getLastError();

    boolean hasValidInfo();

    JsonObject toJson();

    


    boolean isCracked();

    



    static Account fromJson(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "session";
        return switch (type) {
            case "cracked" -> CrackedAccount.fromJson(json);
            default -> SessionAccount.fromJson(json);
        };
    }
}
