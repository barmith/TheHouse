package com.thehouse.paperutility;

import java.awt.Desktop;
import com.hiddenhouse.security.LocalUiSecurity;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;


public final class ArrowBrowserGrid {
    private static final int PORT = 1337;
    private static volatile boolean started;
    private static volatile boolean externalUiOpened;
    private static volatile boolean blankRequested;
    private static volatile ServerSocket server;
    private static volatile LocalUiSecurity security;
    private static ThreadPoolExecutor workers;
    private static final Logger LOG = Logger.getLogger("HiddenHouseLocalUI");

    private ArrowBrowserGrid() {}

    public static synchronized void start() {
        blankRequested = false;
        if (started) return;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 16);
            security = new LocalUiSecurity(PORT);
            workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), work -> {
                Thread thread = new Thread(work, "HiddenHouse-LocalUI"); thread.setDaemon(true); return thread;
            });
            started = true;
            Thread t = new Thread(ArrowBrowserGrid::acceptLoop, "TheHouse-ArrowGrid");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            started = false;
            if (server != null) try { server.close(); } catch (IOException ignored) {}
            server = null;
            LOG.warning("Local UI could not bind to 127.0.0.1:1337; no browser was opened.");
        }
    }

    public static void openBrowser() {
        start();
        if (!started) return;
        LocalUiSecurity session = security;
        if (session == null) return;
        URI uri = URI.create(session.launchUrl());
        try {
            if (Desktop.isDesktopSupported()) { Desktop.getDesktop().browse(uri); return; }
        } catch (Exception ignored) {}
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) new ProcessBuilder("cmd", "/c", "start", "", uri.toString()).start();
            else if (os.contains("mac")) new ProcessBuilder("open", uri.toString()).start();
            else new ProcessBuilder("xdg-open", uri.toString()).start();
        } catch (IOException ignored) {}
    }

    
    public static void openExternalUiOnce() {
        if (externalUiOpened) return;
        externalUiOpened = true;
        Thread browser = new Thread(() -> {
            try { openBrowser(); }
            catch (RuntimeException e) { LOG.warning("External UI browser launch failed; use Insert to retry."); }
        }, "TheHouse-OpenBrowser");
        browser.setDaemon(true);
        browser.start();
    }

    private static void acceptLoop() {
        ServerSocket listener = server;
        ThreadPoolExecutor pool = workers;
        if (listener == null || pool == null) return;
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                if (!socket.getInetAddress().isLoopbackAddress()) { socket.close(); continue; }
                try { pool.execute(() -> handle(socket)); }
                catch (RejectedExecutionException e) { socket.close(); }
            } catch (IOException e) { if (!listener.isClosed()) LOG.fine("Local UI connection could not be accepted"); }
        }
    }

    private static void handle(Socket socket) {
        try (socket; OutputStream out = socket.getOutputStream()) {
            socket.setSoTimeout(1500);
            LocalUiSecurity session = security;
            if (session == null) return;
            if (!session.allowRequest()) {
                send(out, "429 Too Many Requests", "application/json", "{\"error\":\"Local UI rate limit\"}"); return;
            }
            LocalUiSecurity.Request request;
            try { request = LocalUiSecurity.read(socket.getInputStream()); }
            catch (IOException e) { send(out, "400 Bad Request", "application/json", "{\"error\":\"Invalid HTTP request\"}"); return; }
            int validationStatus = session.validate(request);
            if (validationStatus != 0) {
                String reason = switch (validationStatus) { case 404 -> "Not Found"; case 405 -> "Method Not Allowed"; default -> "Forbidden"; };
                send(out, validationStatus + " " + reason, "application/json", "{\"error\":\"Local UI authorization or request rejected\"}"); return;
            }
            String path = request.target();
            if (path.startsWith("/render-setting?")) {
                String error = dispatchRenderSetting(queryString(path, "key", ""), queryString(path, "value", ""));
                com.google.gson.JsonObject result = new com.google.gson.JsonObject();
                if (error == null) result.addProperty("ok", true); else result.addProperty("error", error);
                send(out, error == null ? "200 OK" : "400 Bad Request", "application/json", result.toString());
            } else if (path.startsWith("/paper-profile?")) {
                String operation=queryString(path,"op","");
                String name=queryString(path,"name","");
                String error=dispatchProfile(operation,name);
                send(out,error==null?"200 OK":"400 Bad Request","application/json",
                        error==null?"{\"ok\":true}":"{\"error\":\""+jsonEscape(error)+"\"}");
            } else if (path.startsWith("/select?")) {
                int x = queryInt(path, "x", ArrowState.getMaskTargetX());
                int z = queryInt(path, "z", ArrowState.getMaskTargetZ());
                boolean ok = dispatchSelection(x, z);
                send(out, ok ? "200 OK" : "400 Bad Request", "application/json", ok ? stateJson() : "{\"ok\":false}");
            } else if (path.startsWith("/set?")) {
                String key = queryString(path, "key", "");
                String value = queryString(path, "value", "");
                boolean ok = dispatchSet(key, value);
                send(out, ok ? "200 OK" : "400 Bad Request", "application/json", ok ? "{\"ok\":true}" : "{\"ok\":false}");
            } else if (path.startsWith("/action?")) {
                String action = queryString(path, "name", "");
                boolean ok = dispatchAction(action);
                send(out, ok ? "200 OK" : "400 Bad Request", "application/json", ok ? "{\"ok\":true}" : "{\"ok\":false}");
            } else if (path.equals("/state") || path.startsWith("/state?")) {
                send(out, "200 OK", "application/json", stateJson());
            } else if (path.equals("/items") || path.startsWith("/items?")) {
                send(out, "200 OK", "application/json", itemsJson());
            } else if (path.startsWith("/item-icon?")) {
                byte[] icon = itemIcon(queryString(path, "item", ""));
                if (icon == null) send(out, "404 Not Found", "text/plain; charset=utf-8", "Not found");
                else sendBytes(out, "200 OK", "image/png", icon);
            } else {
                send(out, "200 OK", "text/html; charset=utf-8", blankRequested ? "<!doctype html><html><body></body></html>" : html());
            }
        } catch (Exception ignored) {}
    }

    private static void executeAuthorized(MinecraftClient client, Runnable work) {
        client.execute(work);
    }
    private static boolean dispatchSelection(int x, int z) {
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        executeAuthorized(client, () -> result.complete(ArrowState.setMaskTarget(x, z)));
        try { return result.get(2, TimeUnit.SECONDS); }
        catch (Exception e) { return false; }
    }

    private static int queryInt(String path, String key, int fallback) {
        int q = path.indexOf('?'); if (q < 0) return fallback;
        for (String pair : path.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) try { return Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String queryString(String path, String key, String fallback) {
        int q = path.indexOf('?'); if (q < 0) return fallback;
        for (String pair : path.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], StandardCharsets.UTF_8); } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private static String dispatchProfile(String operation,String name) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client==null)return "Minecraft is unavailable.";
        if(!operation.equals("create")&&!operation.equals("switch")&&!operation.equals("delete"))return "Unknown profile action.";
        var result=new java.util.concurrent.CompletableFuture<String>();
        executeAuthorized(client, ()->{
            try {
                result.complete(switch(operation) {
                    case "create" -> PaperSlotProfiles.create(name);
                    case "switch" -> PaperSlotProfiles.switchTo(name);
                    default -> PaperSlotProfiles.delete(name);
                });
            } catch(Exception e){result.complete("Could not update profile.");}
        });
        try {return result.get(2,java.util.concurrent.TimeUnit.SECONDS);}
        catch(Exception e){return "Minecraft is busy. Check the selected profile before retrying.";}
    }

    private static String dispatchRenderSetting(String key, String value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return "Minecraft is unavailable.";
        var result = new java.util.concurrent.CompletableFuture<String>();
        executeAuthorized(client, () -> {
            try { result.complete(com.thehouse.paperutility.render.RenderModules.set(key, value)); }
            catch (Exception e) { result.complete("Could not update render setting."); }
        });
        try { return result.get(5, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception e) { return "Minecraft is busy. Check the current setting before retrying."; }
    }

    private static String profilesJson() {
        StringBuilder out=new StringBuilder("[");
        for(String name:PaperSlotProfiles.names()) {
            if(out.length()>1)out.append(',');
            out.append('"').append(jsonEscape(name)).append('"');
        }
        return out.append(']').toString();
    }

    private static boolean dispatchSet(String key, String value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        if (key.startsWith("paper-slot-")) {
            try {
                int slot = Integer.parseInt(key.substring("paper-slot-".length()));
                if (slot < 1 || slot > 9 || value == null || value.isBlank()) return false;
                executeAuthorized(client, () -> {
                    if (PaperItemCatalog.setItemById(slot, value)) {
                        PaperSimulationTracker.resetTransientState(client);
                        ModConfig.save();
                    }
                });
                return true;
            } catch (NumberFormatException ignored) { return false; }
        }
        return false;
    }

    private static boolean dispatchAction(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        switch (action) {
            
            case "toggle-enabled" -> executeAuthorized(client, ArrowState::toggleEnabled);
            case "toggle-outcome" -> {  }
            case "learn-board" -> executeAuthorized(client, () -> ArrowState.learnFromCrosshair(client));
            
            case "paper-hud" -> executeAuthorized(client, ModState::toggleHud);
            case "paper-enabled" -> executeAuthorized(client, () -> {
                ModState.toggleSimulation();
                if (!ModState.isSimulationEnabled()) { PaperSimulationTracker.restoreAll(client); FiftyFiftyTracker.restoreAll(client); }
            });
            case "paper-5050" -> executeAuthorized(client, ModState::toggleFiftyFiftyMode);
            case "paper-side" -> executeAuthorized(client, () -> {
                PaperSimulationTracker.restoreAll(client); FiftyFiftyTracker.restoreAll(client);
                ModState.cycleWinningSide(); ModState.clearRound();
            });
            case "paper-color-right" -> executeAuthorized(client, () -> HudColors.cyclePaperRight(1));
            case "paper-color-left" -> executeAuthorized(client, () -> HudColors.cyclePaperLeft(1));
            case "paper-colors-reset" -> executeAuthorized(client, HudColors::resetPaperDefaults);
            case "paper-slots-reset" -> executeAuthorized(client, () -> { PaperItemCatalog.resetDefaults(); PaperSimulationTracker.resetTransientState(client); ModConfig.save(); });
            
            case "blackjack-hud" -> executeAuthorized(client, BlackjackState::toggleHud);
            case "blackjack-enabled" -> executeAuthorized(client, () -> { BlackjackTracker.restoreAll(client); BlackjackState.toggleEnabled(); });
            case "blackjack-turn" -> executeAuthorized(client, () -> { if (BlackjackState.isEnabled()) BlackjackState.advanceHostTurnOrReset(); });
            case "blackjack-relearn" -> executeAuthorized(client, () -> { if (BlackjackDropperVisualMask.isRelearnArmed()) BlackjackDropperVisualMask.cancelRelearn(); else BlackjackDropperVisualMask.armRelearn(); });
            case "blackjack-color-viewer" -> executeAuthorized(client, () -> HudColors.cycleBlackjackViewer(1));
            case "blackjack-color-host" -> executeAuthorized(client, () -> HudColors.cycleBlackjackHost(1));
            case "blackjack-color-locked" -> executeAuthorized(client, () -> HudColors.cycleBlackjackLocked(1));
            case "blackjack-color-failure" -> executeAuthorized(client, () -> HudColors.cycleBlackjackFailure(1));
            case "blackjack-colors-reset" -> executeAuthorized(client, HudColors::resetBlackjackDefaults);
            default -> { return false; }
        }
        return true;
    }

    private static String stateJson() {
        StringBuilder slots = new StringBuilder("[");
        for (int i=1;i<=9;i++) { if(i>1) slots.append(','); slots.append('\"').append(jsonEscape(PaperItemCatalog.getItemId(i))).append('\"'); }
        slots.append(']');
        if (blankRequested) return "{\"disabled\":true}";
        return "{\"disabled\":false,\"render\":" + com.thehouse.paperutility.render.RenderModules.json() + ",\"selectedX\":" + ArrowState.getMaskTargetX() +
                ",\"selectedZ\":" + ArrowState.getMaskTargetZ() +
                ",\"hasTrue\":" + ArrowState.hasTrueLanding() +
                ",\"trueX\":" + ArrowState.getTrueLandingX() +
                ",\"trueZ\":" + ArrowState.getTrueLandingZ() +
                ",\"tracking\":" + ArrowState.isTrackingArrow() +
                ",\"targetReady\":" + ArrowTracker.isRoundTargetReady() +
                ",\"enabled\":" + ArrowState.isEnabled() +
                ",\"masking\":" + ArrowState.isMaskingEnabled() +
                ",\"hostWin\":" + ArrowState.isDesiredHostWin() +
                ",\"learned\":" + ArrowState.isBoardLearned() +
                ",\"paperHud\":" + ModState.isHudEnabled() +
                ",\"paperEnabled\":" + ModState.isSimulationEnabled() +
                ",\"fifty\":" + ModState.isFiftyFiftyModeEnabled() +
                ",\"side\":\"" + ModState.getWinningSide().name() + "\"" +
                ",\"paperRightColor\":\"" + jsonEscape(HudColors.paperRight().displayName()) + "\"" +
                ",\"paperLeftColor\":\"" + jsonEscape(HudColors.paperLeft().displayName()) + "\"" +
                ",\"paperSlots\":" + slots +
                ",\"paperProfiles\":" + profilesJson() +
                ",\"paperProfile\":\"" + jsonEscape(PaperSlotProfiles.activeName()) + "\"" +
                ",\"blackjackHud\":" + BlackjackState.isHudEnabled() +
                ",\"blackjackEnabled\":" + BlackjackState.isEnabled() +
                ",\"blackjackHostTurn\":" + BlackjackState.isHostTurn() +
                ",\"blackjackRelearn\":" + BlackjackDropperVisualMask.isRelearnArmed() +
                ",\"bjViewerColor\":\"" + jsonEscape(HudColors.blackjackViewer().displayName()) + "\"" +
                ",\"bjHostColor\":\"" + jsonEscape(HudColors.blackjackHost().displayName()) + "\"" +
                ",\"bjLockedColor\":\"" + jsonEscape(HudColors.blackjackLocked().displayName()) + "\"" +
                ",\"bjFailureColor\":\"" + jsonEscape(HudColors.blackjackFailure().displayName()) + "\"" +
                ",\"status\":\"" + jsonEscape(ArrowState.getLastTarget()+" | Mask: "+ArrowTracker.getMaskDiagnostic()) + "\"}";
    }


    private static String itemsJson() {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (Item item : PaperItemCatalog.allItems()) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            if (!first) out.append(',');
            first = false;
            String name = new ItemStack(item).getName().getString();
            out.append("{\"id\":\"").append(jsonEscape(id.toString()))
               .append("\",\"name\":\"").append(jsonEscape(name)).append("\"}");
        }
        return out.append(']').toString();
    }

    private static byte[] itemIcon(String rawId) {
        return BrowserItemIcons.get(rawId);
    }


    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void send(OutputStream out, String status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String policy = "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'";
        if (type.startsWith("text/html") && security != null) {
            LocalUiSecurity.Page page = security.decorate(body);
            b = page.body().getBytes(StandardCharsets.UTF_8);
            policy = page.contentSecurityPolicy();
        }
        String h = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\n" + LocalUiSecurity.responseHeaders()
                + "Content-Security-Policy: " + policy + "\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII)); out.write(b); out.flush();
    }

    private static void sendBytes(OutputStream out, String status, String type, byte[] body) throws IOException {
        String h = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\n" + LocalUiSecurity.responseHeaders()
                + "Cache-Control: public, max-age=3600\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII)); out.write(body); out.flush();
    }

    private static String html() {
        return """
<!doctype html><html><head><meta charset="utf-8"><title>TheHouse</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}html,body{min-height:100%;overflow:auto}body{margin:0;background:#000;color:#fff;font:12px system-ui,Segoe UI,sans-serif;padding:14px}.wrap{width:min(1460px,97vw);height:100%;margin:auto}h1{font-size:20px;letter-spacing:.02em;margin:0}.sub{color:#aaa;margin:2px 0 10px}.modules{display:grid;grid-template-columns:1fr 1fr;gap:10px;align-items:start}.card{background:#050505;border:1px solid #fff;border-radius:8px;padding:9px;min-width:0}.card.arrow{grid-column:1/-1}.card h2{font-size:14px;margin:0 0 7px}.controls,.status{display:flex;gap:5px;flex-wrap:wrap}.control,.pill,input,select{background:#000;border:1px solid #777;border-radius:5px;color:#fff;padding:5px 8px;font-size:11px}.control{cursor:pointer}.control.on{background:#fff;color:#000;border-color:#fff;font-weight:700}.control:hover{border-color:#fff}.section{font-size:9px;text-transform:uppercase;letter-spacing:.1em;color:#aaa;margin:8px 0 4px}.arrowLayout{display:grid;grid-template-columns:minmax(390px,1fr) auto minmax(250px,.75fr);gap:18px;align-items:center}.arrowInfo{align-self:start}.grid{display:grid;grid-template-columns:repeat(7,34px);gap:3px;justify-content:center}.cell,.center{width:34px;height:34px;border:1px solid #777;background:#000;color:#fff;border-radius:4px;display:grid;place-items:center;text-align:center;font-size:8px;font-weight:650}.void{width:34px;height:34px;visibility:hidden}.center{color:#aaa;font-size:7px}.cell.selected{outline:2px solid #3b9cff}.cell.true{box-shadow:inset 0 0 0 2px #ff9f1a}.cell.both{outline:2px solid #3b9cff;box-shadow:inset 0 0 0 2px #ff9f1a}.note{color:#aaa;font-size:10px;margin-top:5px}.slotPanel{display:none;margin-top:7px;padding:7px;border:1px solid #fff;background:#000;border-radius:6px}.slotPanel.open{display:block}.slotHead{text-align:center;margin-bottom:6px}.slotHead b{display:block;font-size:12px}.slotHead span{color:#aaa;font-size:9px}.slotGrid{display:grid;grid-template-columns:repeat(3,1fr);gap:4px}.slotCell{min-height:40px;border:1px solid #777;background:#050505;border-radius:4px;padding:4px;cursor:pointer;position:relative}.slotCell:hover{border-color:#fff}.slotNum{position:absolute;left:4px;top:4px;width:14px;height:14px;border:1px solid #fff;display:grid;place-items:center;font-size:8px}.slotName{margin-left:19px;font-size:10px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.slotId{margin:3px 0 0 19px;color:#aaa;font:8px ui-monospace,monospace;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.modal{display:none;position:fixed;inset:0;background:#000c;align-items:center;justify-content:center;z-index:5}.modal.open{display:flex}.picker{width:520px;background:#000;border:1px solid #fff;border-radius:7px;padding:9px}.pickerTitle{text-align:center;font-weight:700;margin-bottom:6px}.search{width:100%;margin-bottom:6px}.itemGrid{display:grid;grid-template-columns:repeat(10,1fr);gap:3px;height:258px}.item{height:40px;background:#050505;border:1px solid #555;border-radius:3px;padding:3px;cursor:pointer;overflow:hidden}.item:hover,.item.sel{border-color:#fff;background:#151515}.itemName{font-size:8px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.itemId{font:7px ui-monospace,monospace;color:#aaa;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:3px}.pickerFoot{display:flex;justify-content:space-between;align-items:center;margin-top:6px}.tiny{font-size:9px;color:#aaa}@media(max-width:1100px){html,body{overflow:auto}.modules{grid-template-columns:1fr}.card.arrow{grid-column:auto}.arrowLayout{grid-template-columns:1fr}.grid{grid-template-columns:repeat(7,30px)}.cell,.center,.void{width:30px;height:30px}}
[hidden]{display:none!important}.categories{display:flex;gap:6px;margin:10px 0}.categories button{font-size:12px;padding:7px 16px}.renderGrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:10px;align-items:start}.renderGrid h2{display:flex;align-items:center;justify-content:space-between}.renderSettings{display:grid;gap:9px;margin-top:12px}.renderSettings label{display:flex;align-items:center;justify-content:space-between;gap:12px;color:#ddd}.renderSettings input[type=number]{width:85px}.renderSettings input[type=checkbox]{accent-color:#fff}.renderSettings input[type=color]{width:48px;height:29px;padding:2px}.renderSettings textarea{width:100%;min-height:130px;background:#000;color:#ddd;border:1px solid #777;border-radius:5px;padding:7px;resize:vertical;font:11px ui-monospace,monospace}.renderMessage{min-height:20px;color:#ffbf69;margin:6px 0}.control:disabled{opacity:.5;cursor:wait}
.wrap{max-width:100%}#privacyGrid label:has(textarea){justify-content:stretch;grid-template-columns:minmax(0,1fr)}.center{color:#aaa;font-size:5px;line-height:6px;overflow:hidden}.modules{grid-template-columns:max-content max-content;justify-content:start}.card{width:fit-content;max-width:100%}.card.arrow{width:fit-content}.arrowLayout{grid-template-columns:max-content max-content max-content;min-width:0}.arrowInfo{min-width:0}.arrowInfo .pill{white-space:normal;max-width:430px}.renderGrid{display:flex;flex-wrap:wrap;align-items:flex-start;justify-content:flex-start;gap:10px}.renderGrid .card{width:fit-content;flex:0 0 auto}.slotModal{display:none;position:fixed;inset:0;background:#000c;align-items:center;justify-content:center;z-index:5}.slotModal.open{display:flex}.dropperPanel{width:270px;background:#000;border:1px solid #fff;border-radius:7px;padding:9px}.dropperPanel .slotGrid{width:210px;margin:8px auto}.slotGrid{grid-template-columns:repeat(3,1fr)}.slotCell{height:62px;min-height:62px;padding:3px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px}.slotNum{left:3px;top:3px;width:13px;height:13px}.slotIcon{width:30px;height:30px;image-rendering:pixelated;object-fit:contain}.slotName{margin:0;width:100%;text-align:center;font-size:8px}.slotId{display:none}.item{height:48px;display:flex;align-items:center;gap:4px}.itemIcon{width:28px;height:28px;flex:none;image-rendering:pixelated;object-fit:contain}.itemName{font-size:8px;line-height:10px;white-space:normal;overflow:hidden;text-overflow:ellipsis}
.picker .itemGrid{grid-template-columns:repeat(5,minmax(0,1fr));grid-auto-rows:48px;height:min(306px,calc(100vh - 160px));align-content:start}
.picker .itemName{min-width:0;font-size:10px;line-height:12px}
.dropperPanel .slotGrid{grid-template-columns:repeat(3,minmax(0,1fr));grid-auto-rows:78px;max-width:100%}
.dropperPanel .slotCell{min-width:0;width:100%;overflow:hidden}
.dropperPanel .slotName{min-width:0;max-width:100%}
</style></head><body><div class="wrap"><h1>TheHouse</h1><div class="sub">External controls · HUD indicators remain in Minecraft</div>
<nav class="categories" aria-label="Module categories"><button id="utilitiesTab" class="control on" aria-pressed="true">Utilities</button><button id="renderTab" class="control" aria-pressed="false">Render</button></nav>
<div id="utilityModules" class="modules">
<section class="card"><h2>Paper / 50-50</h2><div class="controls"><button id="paperHud" class="control">HUD —</button><button id="paperEnabled" class="control">Paper —</button><button id="fifty" class="control">50/50 —</button><button id="side" class="control">Winning Side —</button></div><div class="section">HUD colors</div><div class="controls"><button id="prc" class="control">Right —</button><button id="plc" class="control">Left —</button><button class="control" data-hh-action="paper-colors-reset">Reset colors</button></div><div class="section">Configuration</div><button id="slotToggle" class="control">Slot Configuration</button><div id="slotPanel" class="slotModal"><div class="dropperPanel"><div class="slotHead"><b>Dropper</b><span>Shared by both droppers</span></div><div class="controls" style="margin-bottom:6px"><select id="profileSelect" aria-label="Paper slot profile" style="flex:1;min-width:100px"></select><button id="profileDelete" class="control">Delete</button></div><div class="controls" style="margin-bottom:6px"><input id="profileName" maxlength="32" placeholder="New profile name" aria-label="New profile name" style="flex:1;min-width:100px"><button id="profileCreate" class="control">Save as</button></div><div id="profileMessage" class="note" role="status" style="margin-bottom:6px">Slot edits save to the selected profile.</div><div id="slotGrid" class="slotGrid"></div><div class="controls" style="margin-top:6px"><button class="control" data-hh-action="paper-slots-reset">Reset slots</button><button id="closeSlots" class="control">Close</button></div><div class="note">Click a slot to search the full item registry.</div></div></div></section>
<section class="card"><h2>Blackjack</h2><div class="controls"><button id="bjHud" class="control">HUD —</button><button id="bjEnabled" class="control">Enabled —</button><button id="bjTurn" class="control">Host Turn —</button><button id="bjRelearn" class="control">Relearn Dropper —</button></div><div class="section">HUD colors</div><div class="controls"><button id="bvc" class="control">Viewer —</button><button id="bhc" class="control">Host —</button><button id="blc" class="control">Locked —</button><button id="bfc" class="control">Failure —</button><button class="control" data-hh-action="blackjack-colors-reset">Reset colors</button></div></section>
<section class="card arrow"><h2>Arrow</h2><div class="arrowLayout"><div class="arrowInfo"><div class="controls"><button id="enabled" class="control">Arrow —</button><button id="outcome" class="control">Desired —</button><button id="learn" class="control">Learn Board</button></div><div class="section">Live status</div><div class="status"><div class="pill">Board: <b id="board">—</b></div><div class="pill">Round: <b id="round">WAITING</b></div></div><b id="sel" hidden></b><b id="real" hidden></b><div class="pill" style="margin-top:5px">Tracker: <b id="tracker">—</b></div></div><div><div class="section" style="text-align:center;margin-top:0">Arrow Grid</div><div id="grid" class="grid"></div><div class="note">Blue = masked target · Orange = true landing</div></div></div></section>
</div>
<section id="renderModules" hidden><div class="note">Render settings save automatically. Freecam and Zoom reset when leaving a world. Toggle keybinds are available in Minecraft Controls.</div><div id="renderMessage" class="renderMessage" role="status"></div><div class="renderGrid"><section class="card"><h2>Freecam<button class="control" data-render-toggle="freecam" aria-pressed="false">OFF</button></h2><div class="note">Move the camera with your movement keys. Jump / sneak move vertically; sprint moves faster. Your player stays in the world.</div><div class="renderSettings"><label>Speed (blocks / tick)<input type="number" data-render-key="freecamSpeed" min="0.1" max="5" step="0.1"></label><label title="Only the block face your real player is already aiming at, within normal reach.">Vanilla block interaction<input type="checkbox" data-render-key="freecamInteract"></label></div></section><section class="card"><h2>Fullbright<button class="control" data-render-toggle="fullbright" aria-pressed="false">OFF</button></h2><div class="note">Full lighting without changing your brightness setting or potion effects.</div><div class="renderSettings"></div></section><section class="card"><h2>NoRender<button class="control" data-render-toggle="norender" aria-pressed="false">OFF</button></h2><div class="note">Hide selected visual overlays.</div><div class="renderSettings"><label>Fire overlay<input type="checkbox" data-render-key="noFire"></label><label>Underwater overlay<input type="checkbox" data-render-key="noWater"></label><label>Inside-block overlay<input type="checkbox" data-render-key="noWall"></label><label>Portal overlay<input type="checkbox" data-render-key="noPortal"></label><label>Vignette<input type="checkbox" data-render-key="noVignette"></label><label>Weather<input type="checkbox" data-render-key="noWeather"></label><label>Boss bars<input type="checkbox" data-render-key="noBossbar"></label><label>Scoreboard<input type="checkbox" data-render-key="noScoreboard"></label></div></section><section class="card"><h2>ESP<button class="control" data-render-toggle="esp" aria-pressed="false">OFF</button></h2><div class="note">Colored entity outlines visible through walls.</div><div class="renderSettings"><label>Players<input type="checkbox" data-render-key="espPlayers"></label><label>Mobs<input type="checkbox" data-render-key="espMobs"></label><label>Dropped items<input type="checkbox" data-render-key="espItems"></label><label>Range (blocks)<input type="number" data-render-key="espRange" min="8" max="256" step="8"></label><label>Outline color<input type="color" data-render-key="espColor"></label></div></section><section class="card"><h2>Zoom<button class="control" data-render-toggle="zoom" aria-pressed="false">OFF</button></h2><div class="note">Hold Left Alt to zoom, or use this toggle to keep zoom on. Rebind the key in Minecraft Controls.</div><div class="renderSettings"><label>Magnification<input type="number" data-render-key="zoomFactor" min="1" max="20" step="0.5"></label><label>Smooth transition<input type="checkbox" data-render-key="zoomSmooth"></label><span id="zoomLive" class="tiny"></span></div></section><section class="card"><h2>Nametags<button class="control" data-render-toggle="nametags" aria-pressed="false">OFF</button></h2><div class="note">Enhanced player labels with optional health and distance.</div><div class="renderSettings"><label>Health + absorption<input type="checkbox" data-render-key="nameHealth"></label><label>Distance<input type="checkbox" data-render-key="nameDistance"></label><label>Range (blocks)<input type="number" data-render-key="nameRange" min="8" max="256" step="8"></label></div></section></div></section></div>
<div id="modal" class="modal"><div class="picker"><div id="pickerTitle" class="pickerTitle">Choose item</div><input id="search" class="search" placeholder="Search items..."><div id="itemGrid" class="itemGrid"></div><div class="pickerFoot"><button id="prev" class="control">&lt; Prev</button><span id="pageInfo" class="tiny"></span><button id="next" class="control">Next &gt;</button><button id="closePicker" class="control">Close</button></div></div></div>
<script>
const grid=document.getElementById('grid'),slotGrid=document.getElementById('slotGrid'),slotPanel=document.getElementById('slotPanel'),modal=document.getElementById('modal');let state=null,items=[],filtered=[],editSlot=0,page=0;const PER=60;function s(n){return n>=0?'+'+n:String(n)}
for(let z=-3;z<=3;z++)for(let x=-3;x<=3;x++){if(Math.abs(x)===3&&Math.abs(z)===3){let v=document.createElement('div');v.className='void';grid.appendChild(v);continue}if(x===0&&z===0){let c=document.createElement('div');c.className='center';c.append(document.createTextNode('CENTER'));c.appendChild(document.createElement('br'));c.append(document.createTextNode('DISPENSER'));grid.appendChild(c);continue}let b=document.createElement('div');b.className='cell';b.dataset.x=x;b.dataset.z=z;let win=((x+z)&1)!==0;b.textContent=win?'W':'L';grid.appendChild(b)}
async function action(name){await fetch('/action?name='+encodeURIComponent(name));setTimeout(refresh,40)}async function setv(key,value){await fetch('/set?key='+encodeURIComponent(key)+'&value='+encodeURIComponent(value));setTimeout(refresh,60)}
[['paperHud','paper-hud'],['paperEnabled','paper-enabled'],['fifty','paper-5050'],['side','paper-side'],['prc','paper-color-right'],['plc','paper-color-left'],['bjHud','blackjack-hud'],['bjEnabled','blackjack-enabled'],['bjTurn','blackjack-turn'],['bjRelearn','blackjack-relearn'],['bvc','blackjack-color-viewer'],['bhc','blackjack-color-host'],['blc','blackjack-color-locked'],['bfc','blackjack-color-failure'],['enabled','toggle-enabled'],['outcome','toggle-outcome'],['learn','learn-board']].forEach(([id,a])=>document.getElementById(id).onclick=()=>action(a));
document.getElementById('slotToggle').onclick=()=>slotPanel.classList.toggle('open');document.getElementById('closeSlots').onclick=()=>slotPanel.classList.remove('open');slotPanel.onclick=e=>{if(e.target===slotPanel)slotPanel.classList.remove('open')};document.getElementById('closePicker').onclick=()=>modal.classList.remove('open');modal.onclick=e=>{if(e.target===modal)modal.classList.remove('open')};
function btn(id,text,on){let e=document.getElementById(id);e.textContent=text;e.className='control'+(on?' on':'')}
let profileBusy=false,profileListKey='';
function renderProfiles(){if(!state)return;const names=state.paperProfiles||[],select=document.getElementById('profileSelect'),key=JSON.stringify(names);if(key!==profileListKey){select.replaceChildren(...names.map(name=>{let option=document.createElement('option');option.value=name;option.textContent=name;return option}));profileListKey=key}if(!profileBusy)select.value=state.paperProfile;select.disabled=profileBusy;document.getElementById('profileCreate').disabled=profileBusy;document.getElementById('profileDelete').disabled=profileBusy||names.length<=1}
async function profileAction(op,name){if(profileBusy)return;profileBusy=true;renderProfiles();const message=document.getElementById('profileMessage');try{const response=await fetch('/paper-profile?op='+encodeURIComponent(op)+'&name='+encodeURIComponent(name));const result=await response.json();if(!response.ok)throw Error(result.error||'Could not update profile.');message.textContent='Slot edits save to the selected profile.';if(op==='create')document.getElementById('profileName').value='';modal.classList.remove('open')}catch(error){message.textContent=error.message}finally{profileBusy=false;await refresh()}}
document.getElementById('profileSelect').onchange=e=>profileAction('switch',e.target.value);
document.getElementById('profileCreate').onclick=()=>profileAction('create',document.getElementById('profileName').value);
document.getElementById('profileName').onkeydown=e=>{if(e.key==='Enter')document.getElementById('profileCreate').click()};
document.getElementById('profileDelete').onclick=()=>{if(state&&confirm('Delete profile "'+state.paperProfile+'"?'))profileAction('delete',state.paperProfile)};
function shortName(id){let x=items.find(v=>v.id===id);if(x)return x.name;return id.replace(/^[^:]+:/,'').split('_').map((word,i)=>i>0&&['of','the','and'].includes(word)?word:word.charAt(0).toUpperCase()+word.slice(1)).join(' ')}
function iconUrl(id){return '/item-icon?item='+encodeURIComponent(id)}
const iconCache=new Map(),iconQueue=[];let iconActive=0;
function pumpIcons(){while(iconActive<3&&iconQueue.length){let task=iconQueue.shift();iconActive++;task().finally(()=>{iconActive--;pumpIcons()})}}
function cachedIcon(id){if(iconCache.has(id))return iconCache.get(id);let promise=new Promise((resolve,reject)=>{iconQueue.push(async()=>{try{for(let attempt=0;attempt<3;attempt++){let response=await fetch(iconUrl(id));if(response.ok){let blob=await response.blob();resolve(URL.createObjectURL(blob));return}if(attempt===2)throw Error('Icon request failed: '+response.status);await new Promise(done=>setTimeout(done,400*(attempt+1)))} }catch(error){iconCache.delete(id);reject(error)}});pumpIcons()});iconCache.set(id,promise);return promise}
async function loadIcon(img,id){if(!id)return;try{img.src=await cachedIcon(id)}catch(error){img.title='Could not load '+shortName(id)+': '+error.message}}
window.addEventListener('pagehide',()=>{for(const promise of iconCache.values())promise.then(url=>URL.revokeObjectURL(url),()=>{})});
let renderedSlotsKey='';
function renderSlots(vals){let key=JSON.stringify(vals);if(key===renderedSlotsKey)return;renderedSlotsKey=key;slotGrid.replaceChildren();for(let i=1;i<=9;i++){let id=vals[i-1]||'';let c=document.createElement('div');c.className='slotCell';let number=document.createElement('span');number.className='slotNum';number.textContent=i;let img=document.createElement('img');img.className='slotIcon';img.alt='';let name=document.createElement('div');name.className='slotName';name.textContent=shortName(id);c.append(number,img,name);loadIcon(img,id);c.onclick=()=>openPicker(i,id);slotGrid.appendChild(c)}}
function esc(x){return String(x).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
async function ensureItems(){if(items.length)return;items=await(await fetch('/items',{cache:'no-store'})).json()}async function openPicker(slot,current){await ensureItems();editSlot=slot;document.getElementById('pickerTitle').textContent='Choose item for slot '+slot;document.getElementById('search').value='';filtered=items;let ix=filtered.findIndex(x=>x.id===current);page=ix<0?0:Math.floor(ix/PER);renderPicker();modal.classList.add('open');document.getElementById('search').focus()}
function renderPicker(){let q=document.getElementById('search').value.trim().toLowerCase();filtered=q?items.filter(x=>x.id.toLowerCase().includes(q)||x.name.toLowerCase().includes(q)):items;let max=Math.max(0,Math.ceil(filtered.length/PER)-1);page=Math.min(page,max);let g=document.getElementById('itemGrid');g.replaceChildren();for(const it of filtered.slice(page*PER,page*PER+PER)){let d=document.createElement('div');d.className='item'+(state&&state.paperSlots[editSlot-1]===it.id?' sel':'');let img=document.createElement('img');img.className='itemIcon';img.alt='';let name=document.createElement('div');name.className='itemName';name.textContent=it.name;d.append(img,name);loadIcon(img,it.id);d.onclick=async()=>{await setv('paper-slot-'+editSlot,it.id);modal.classList.remove('open')};g.appendChild(d)}document.getElementById('pageInfo').textContent=filtered.length?(page+1)+' / '+(max+1)+' · '+filtered.length+' items':'0 results';document.getElementById('prev').disabled=page<=0;document.getElementById('next').disabled=page>=max}
document.getElementById('search').oninput=()=>{page=0;renderPicker()};document.getElementById('prev').onclick=()=>{if(page>0){page--;renderPicker()}};document.getElementById('next').onclick=()=>{if((page+1)*PER<filtered.length){page++;renderPicker()}};

const renderPending=new Set();
function selectCategory(category){for(const [id,name] of [['utilityModules','utilities'],['renderModules','render']])document.getElementById(id).hidden=category!==name;for(const [id,name] of [['utilitiesTab','utilities'],['renderTab','render']]){const b=document.getElementById(id),on=category===name;b.classList.toggle('on',on);b.setAttribute('aria-pressed',String(on))}modal.classList.remove('open')}
document.getElementById('utilitiesTab').onclick=()=>selectCategory('utilities');document.getElementById('renderTab').onclick=()=>selectCategory('render');
const privacyPending=new Set();let privacyState=null,privacyLoading=false;
const privacyGroups=[['Client privacy',[['spoofAsVanilla','Vanilla brand'],['translationProtection','Translation protection'],['fakeDefaultKeybinds','Default keybind privacy'],['meteorFix','Meteor compatibility'],['disableTelemetry','Disable telemetry'],['signingMode','Chat signing',[['SIGN','Sign'],['OFF','Unsigned']]]]],['Resource packs',[['isolatePackCache','Separate account cache'],['blockLocalPackUrls','Block local pack URLs'],['stripModShaders','Strip mod shaders'],['packStripMode','Server pack handling',[['MANUAL','Manual'],['ASK','Ask'],['ALWAYS_ON','Always strip']]]]],['Mod whitelist',[['whitelistMode','Mode',[['OFF','Block all'],['AUTO','Automatic'],['CUSTOM','Custom']]],['whitelistedMods','Mod IDs','list']]],['Notifications',[['showAlerts','Chat alerts'],['showToasts','Toasts'],['logDetections','Log detections'],['debugAlerts','Debug alerts'],['debugCommand','Debug command']]]];
const privacyGrid=document.getElementById('privacyGrid');if(privacyGrid)for(const [title,fields] of privacyGroups){const section=document.createElement('section');section.className='card';const heading=document.createElement('h2');heading.textContent=title;section.appendChild(heading);const controls=document.createElement('div');controls.className='renderSettings';for(const [key,label,options] of fields){const row=document.createElement('label');row.textContent=label;let input;if(Array.isArray(options)){input=document.createElement('select');for(const [value,text] of options){const option=document.createElement('option');option.value=value;option.textContent=text;input.appendChild(option)}}else{input=document.createElement(options==='list'?'textarea':'input');if(options==='list'){input.maxLength=8192;input.spellcheck=false;input.oninput=()=>input.dataset.dirty='true'}else input.type='checkbox'}input.dataset.privacyKey=key;input.disabled=true;input.setAttribute('aria-label',label);row.appendChild(input);controls.appendChild(row);if(options==='list'){row.style.display='grid';const button=document.createElement('button');button.className='control';button.textContent='Apply mod list';button.dataset.privacyApply=key;button.disabled=true;button.onclick=async()=>{if(await privacySet(key,input.value)){delete input.dataset.dirty;renderPrivacy()}};controls.appendChild(button)}else input.onchange=()=>privacySet(key,input.type==='checkbox'?input.checked:input.value)}section.appendChild(controls);privacyGrid.appendChild(section)}
function renderPrivacy(){if(!privacyState)return;for(const input of document.querySelectorAll('[data-privacy-key]')){const key=input.dataset.privacyKey,external=(key==='signingMode'&&privacyState.chatSigningManagedExternally)||(key==='disableTelemetry'&&privacyState.telemetryManagedExternally);input.disabled=privacyPending.has(key)||external;input.title=external?'Managed by another installed mod':'';if(document.activeElement===input||input.dataset.dirty||privacyPending.has(key))continue;if(input.type==='checkbox')input.checked=privacyState[key]===true;else input.value=Array.isArray(privacyState[key])?privacyState[key].join(', '):privacyState[key]}for(const b of document.querySelectorAll('[data-privacy-apply]'))b.disabled=privacyPending.has(b.dataset.privacyApply);document.getElementById('privacyRestart').hidden=!privacyState.restartRequired}
async function refreshPrivacy(){if(privacyLoading)return;privacyLoading=true;try{const response=await fetch('/privacy-state',{cache:'no-store'}),result=await response.json();if(!response.ok||result.error)throw Error(result.error||'Privacy settings are unavailable.');privacyState=result;renderPrivacy()}catch(e){document.getElementById('privacyMessage').textContent=e.message}finally{privacyLoading=false}}
async function privacySet(key,value){if(privacyPending.has(key))return false;privacyPending.add(key);renderPrivacy();const message=document.getElementById('privacyMessage');message.textContent='';try{const response=await fetch('/privacy-setting?key='+encodeURIComponent(key)+'&value='+encodeURIComponent(value)),result=await response.json();if(!response.ok||result.error)throw Error(result.error||'Could not update privacy setting.');return true}catch(e){message.textContent=e.message;return false}finally{privacyPending.delete(key);await refreshPrivacy()}}
setInterval(()=>{if(!document.getElementById('privacyModules').hidden)refreshPrivacy()},1000);
async function renderSet(key,value){if(renderPending.has(key))return false;renderPending.add(key);renderSettings();const message=document.getElementById('renderMessage');message.textContent='';try{const response=await fetch('/render-setting?key='+encodeURIComponent(key)+'&value='+encodeURIComponent(value));const result=await response.json();if(!response.ok)throw Error(result.error||'Could not update setting.');return true}catch(e){message.textContent=e.message;return false}finally{renderPending.delete(key);await refresh()}}
for(const button of document.querySelectorAll('[data-render-toggle]'))button.onclick=()=>{if(state&&state.render)renderSet(button.dataset.renderToggle,state.render[button.dataset.renderToggle]!=='true')};
for(const input of document.querySelectorAll('[data-render-key]'))input.onchange=()=>{if(input.checkValidity())renderSet(input.dataset.renderKey,input.type==='checkbox'?input.checked:input.value);else input.reportValidity()};
function renderSettings(){if(!state||!state.render)return;const r=state.render;for(const b of document.querySelectorAll('[data-render-toggle]')){const k=b.dataset.renderToggle,on=r[k]==='true';b.textContent=on?'ON':'OFF';b.classList.toggle('on',on);b.setAttribute('aria-pressed',String(on));b.disabled=renderPending.has(k)}for(const input of document.querySelectorAll('[data-render-key]')){const k=input.dataset.renderKey;input.disabled=renderPending.has(k);if(document.activeElement===input||renderPending.has(k))continue;if(input.type==='checkbox')input.checked=r[k]==='true';else input.value=r[k]}document.getElementById('zoomLive').textContent=r.zoomActive?'Zoom active':'Zoom inactive'}

async function refresh(){try{let response=await fetch('/state',{cache:'no-store'});if(!response.ok)throw Error('State request failed ('+response.status+')');state=await response.json();if(!state||state.error)throw Error(state&&state.error||'Invalid state response');if(state.disabled){document.documentElement.innerHTML='<head><title></title></head><body></body>';return}renderSettings();btn('paperHud','HUD '+(state.paperHud?'ON':'OFF'),state.paperHud);btn('paperEnabled','Paper '+(state.paperEnabled?'ON':'OFF'),state.paperEnabled);btn('fifty','50/50 '+(state.fifty?'ON':'OFF'),state.fifty);btn('side','Winning Side '+(state.fifty?(state.side==='RIGHT'?'HOST':'VIEWER'):state.side),true);btn('prc','Right '+state.paperRightColor,false);btn('plc','Left '+state.paperLeftColor,false);renderSlots(Array.isArray(state.paperSlots)?state.paperSlots:[]);renderProfiles();btn('bjHud','HUD '+(state.blackjackHud?'ON':'OFF'),state.blackjackHud);btn('bjEnabled','Enabled '+(state.blackjackEnabled?'ON':'OFF'),state.blackjackEnabled);btn('bjTurn','Host Turn '+(state.blackjackHostTurn?'HOST':'VIEWER'),state.blackjackHostTurn);btn('bjRelearn','Relearn Dropper '+(state.blackjackRelearn?'ARMED':'READY'),state.blackjackRelearn);btn('bvc','Viewer '+state.bjViewerColor,false);btn('bhc','Host '+state.bjHostColor,false);btn('blc','Locked '+state.bjLockedColor,false);btn('bfc','Failure '+state.bjFailureColor,false);btn('enabled','Arrow '+(state.enabled?'ON':'OFF'),state.enabled);btn('outcome','Desired HOST WIN',true);document.getElementById('board').textContent=state.learned?'LEARNED':'NOT LEARNED';document.getElementById('sel').textContent=(state.tracking&&!state.targetReady)?'CALCULATING…':s(state.selectedX)+','+s(state.selectedZ);document.getElementById('real').textContent=state.hasTrue?s(state.trueX)+','+s(state.trueZ):'—';document.getElementById('round').textContent=state.tracking?'TRACKING':'WAITING';document.getElementById('tracker').textContent=state.status||'—';for(const b of grid.querySelectorAll('.cell')){let x=+b.dataset.x,z=+b.dataset.z,sel=x===state.selectedX&&z===state.selectedZ,real=state.hasTrue&&x===state.trueX&&z===state.trueZ;b.className='cell'+(sel&&real?' both':sel?' selected':real?' true':'')}}catch(e){document.getElementById('round').textContent='DISCONNECTED';document.getElementById('tracker').textContent=e.message||'State unavailable'}}refresh();setInterval(refresh,300);
</script></body></html>
""".replace("</style>", ".dropperPanel{width:320px;max-width:calc(100vw - 28px);padding:12px}.dropperPanel .slotGrid{width:264px;margin:10px auto}.dropperPanel .slotCell{height:78px;min-height:78px;padding:5px;gap:3px}.dropperPanel .slotIcon{width:40px;height:40px}.dropperPanel .slotName{font-size:9px;line-height:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.dropperPanel .slotNum{left:4px;top:4px;width:15px;height:15px}.renderGrid .card > .note{display:none}.picker{width:min(520px,calc(100vw - 24px));max-height:calc(100vh - 24px);overflow:hidden}.itemGrid{height:306px;overflow-y:auto}.pickerFoot{gap:6px;flex-wrap:wrap}.pickerFoot .control{flex:0 0 auto}</style>");
    }

}
