package com.hiddenhouse.security;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public final class LocalUiSecurity {
    private static final Set<String> READ = Set.of("/state", "/items", "/item-icon", "/privacy-state");
    private static final Set<String> WRITE = Set.of("/privacy-setting", "/render-setting", "/paper-profile", "/select", "/set", "/action");
    private final String authority;
    private final String origin;
    private final String token = random(32);
    private double credits = 80;
    private long last = System.nanoTime();
    public LocalUiSecurity(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid localhost port");
        authority = "127.0.0.1:" + port;
        origin = "http://" + authority;
    }
    
    public String launchUrl() { return origin + "/#hh=" + token; }
    public record Request(String method, String target, Map<String, String> headers) {
        public String path() { int query = target.indexOf('?'); return query < 0 ? target : target.substring(0, query); }
        @Override public String toString() { return "Local UI request (headers and parameters redacted)"; }
    }
    public static final class BadRequest extends IOException {
        public BadRequest() { super("Invalid local UI request"); }
    }
    public static Request read(InputStream input) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        String line = line(input, 32768, deadline);
        String[] pieces = line.split(" ", -1);
        if (pieces.length != 3 || !pieces[2].equals("HTTP/1.1") || !pieces[1].startsWith("/")
                || pieces[1].startsWith("//") || pieces[1].indexOf('#') >= 0 || pieces[1].indexOf('\\') >= 0) throw new BadRequest();
        Map<String, String> headers = new LinkedHashMap<>();
        int total = 0;
        while (true) {
            line = line(input, 8192, deadline);
            if (line.isEmpty()) break;
            total += line.length();
            if (total > 16384 || headers.size() >= 40) throw new BadRequest();
            int colon = line.indexOf(':');
            if (colon < 1) throw new BadRequest();
            String key = line.substring(0, colon).toLowerCase(Locale.ROOT);
            if (!key.matches("[a-z0-9!#$%&'*+.^_`|~-]+") || headers.containsKey(key)) throw new BadRequest();
            headers.put(key, line.substring(colon + 1).strip());
        }
        if (headers.containsKey("transfer-encoding")) throw new BadRequest();
        if (headers.containsKey("content-length") && !headers.get("content-length").equals("0")) throw new BadRequest();
        return new Request(pieces[0], pieces[1], Map.copyOf(headers));
    }
    private static String line(InputStream input, int maximum, long deadline) throws IOException {
        StringBuilder out = new StringBuilder();
        while (out.length() <= maximum) {
            if (System.nanoTime() - deadline >= 0) throw new BadRequest();
            int c = input.read();
            if (c == -1) throw new EOFException();
            if (c == '\r') { if (input.read() != '\n') throw new BadRequest(); return out.toString(); }
            if (c < 32 && c != '\t' || c > 126 || c == '\n') throw new BadRequest();
            out.append((char) c);
        }
        throw new BadRequest();
    }
    
    public int validate(Request request) {
        if (!authority.equals(request.headers().get("host"))) return 403; 
        String sentOrigin = request.headers().get("origin");
        if (sentOrigin != null && !origin.equals(sentOrigin)) return 403;
        String site = request.headers().get("sec-fetch-site");
        if ("cross-site".equals(site)) return 403;
        if (!request.method().equals("GET") && !request.method().equals("POST")) return 405;
        String path = request.path();
        if (path.equals("/")) return request.method().equals("GET") ? 0 : 405;
        if (!READ.contains(path) && !WRITE.contains(path)) return 404;
        String provided = request.headers().getOrDefault("x-hiddenhouse-session", "");
        if (!provided.matches("[A-Za-z0-9_-]{43}") || !MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII), provided.getBytes(StandardCharsets.US_ASCII))) return 403;
        if (WRITE.contains(path) && !request.method().equals("POST")) return 405;
        if (READ.contains(path) && !request.method().equals("GET")) return 405;
        
        if (WRITE.contains(path) && !origin.equals(sentOrigin)) return 403;
        return 0;
    }
    
    public synchronized boolean allowRequest() {
        long now = System.nanoTime();
        credits = Math.min(80, credits + Math.max(0, now - last) / 1_000_000_000.0 * 40);
        last = now;
        if (credits < 1) return false;
        credits -= 1;
        return true;
    }
    public record Page(String body, String contentSecurityPolicy) {}
    public Page decorate(String html) {
        String nonce = random(18);
        String script = "<script nonce=\"" + nonce + "\">" + BOOTSTRAP + "</script>";
        String body = html.replace("<script>", "<script nonce=\"" + nonce + "\">");
        
        
        
        int headEnd = body.indexOf("</head>");
        if (headEnd >= 0) {
            body = body.substring(0, headEnd) + script + body.substring(headEnd);
        }
        String policy = "default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'unsafe-inline'; connect-src " + origin
                + "; img-src 'self' data: blob:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; object-src 'none'";
        return new Page(body, policy);
    }
    public static String responseHeaders() {
        return "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n"
                + "Referrer-Policy: no-referrer\r\nCross-Origin-Resource-Policy: same-origin\r\n"
                + "Cross-Origin-Opener-Policy: same-origin\r\nPermissions-Policy: camera=(), microphone=(), geolocation=()\r\n";
    }
    private static String random(int count) {
        byte[] bytes = new byte[count]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static final String BOOTSTRAP = """
(() => {
  'use strict';
  const incoming = new URLSearchParams(location.hash.slice(1)).get('hh');
  let token = incoming || '';
  try {
    if (incoming && /^[A-Za-z0-9_-]{43}$/.test(incoming)) sessionStorage.setItem('hiddenhouse.ui.session', incoming);
    token = sessionStorage.getItem('hiddenhouse.ui.session') || token;
  } catch (_) {  }
  if (location.hash) history.replaceState(null, '', location.pathname);
  const nativeFetch = window.fetch.bind(window);
  const write = new Set(['/privacy-setting','/render-setting','/paper-profile','/select','/set','/action']);
  function notice(message) {
    let box = document.getElementById('hhSecurityNotice');
    if (!box && document.body) {
      box = document.createElement('p'); box.id = 'hhSecurityNotice'; box.setAttribute('role','alert');
      document.body.prepend(box);
    }
    if (box) box.textContent = message;
  }
  window.fetch = async (input, options = {}) => {
    const url = new URL(typeof input === 'string' ? input : input.url, location.href);
    if (url.origin !== location.origin) throw new Error('External requests are not permitted by the local UI.');
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
      notice('Open the HiddenHouse UI from Minecraft to authorize this browser tab.');
      throw new Error('Local UI authorization required.');
    }
    const headers = new Headers(options.headers);
    headers.set('X-HiddenHouse-Session', token);
    const control = new AbortController();
    const timer = setTimeout(() => control.abort(), 7000);
    try {
      const response = await nativeFetch(url.href, { ...options, headers,
        method: write.has(url.pathname) ? 'POST' : 'GET', body: undefined,
        credentials: 'omit', cache: 'no-store', redirect: 'error', signal: control.signal });
      if (response.status === 403) notice('Authorization ended. Reopen the UI from an authorized Minecraft session.');
      return response;
    } finally { clearTimeout(timer); }
  };
  document.addEventListener('click', event => {
    const button = event.target instanceof Element ? event.target.closest('[data-hh-action]') : null;
    if (button && typeof window.action === 'function') window.action(button.dataset.hhAction);
  });
})();
""";
}
