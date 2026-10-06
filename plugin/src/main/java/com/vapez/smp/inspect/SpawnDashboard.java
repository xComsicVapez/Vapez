package com.vapez.smp.inspect;

import com.vapez.smp.VapezPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Phone-friendly read-only spawn page. Open http://SERVER_IP:25580/ from anywhere
 * the port is reachable (same as joining, plus this extra forward).
 */
public final class SpawnDashboard {

    private final VapezPlugin plugin;
    private HttpServer server;

    public SpawnDashboard(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().dashboardEnabled()) {
            return;
        }
        try {
            InetSocketAddress addr = new InetSocketAddress(
                    plugin.settings().dashboardBind(), plugin.settings().dashboardPort());
            server = HttpServer.create(addr, 0);
            server.createContext("/", this::index);
            server.createContext("/api/spawn", this::api);
            server.createContext("/map.png", this::map);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "vapez-spawn-dashboard");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            plugin.getLogger().info("Spawn dashboard http://" + plugin.settings().dashboardBind()
                    + ":" + plugin.settings().dashboardPort() + "/  (forward this port to check spawn from your phone)");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not bind spawn dashboard: " + ex.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static String renderHtml(SpawnReport report) {
        boolean ok = report != null && report.allOk();
        String status = report == null ? "WAITING" : (ok ? "OK" : "CHECK");
        String color = ok ? "#3DDC97" : "#FFB020";
        String summary = report == null ? "Waiting for the first spawn scan." : report.summary;
        String world = report == null ? "—" : report.world + " @ " + report.spawnX + ", " + report.spawnY + ", " + report.spawnZ;
        String sig = report == null ? "—" : report.signature + " (" + report.signatureScore + ")";
        int mobs = report == null ? 0 : report.hostileMobs;
        int players = report == null ? 0 : report.playersInSpawn;
        String playersList = report == null || report.playerNames.isEmpty() ? "none" : String.join(", ", report.playerNames);
        String mobsList = report == null || report.hostileTypes.isEmpty() ? "none" : String.join(", ", report.hostileTypes);
        String flag = report != null && report.citadelFlag ? "yes" : "no";
        String prot = report == null ? "—" : ("r=" + report.protectionRadius
                + " break=" + yn(report.denyBreak)
                + " death=" + yn(report.denyDamage)
                + " mobs=" + yn(report.denyMobs));
        String time = report == null ? "—" : report.timestamp;
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <meta http-equiv="refresh" content="15"/>
                  <title>Vapez spawn</title>
                  <style>
                    :root { color-scheme: dark; }
                    body { margin:0; font-family: ui-sans-serif, system-ui, sans-serif;
                           background:#070A12; color:#E8F1FF; }
                    header { padding:20px 16px 8px; }
                    h1 { margin:0; font-size:1.4rem; }
                    .sub { color:#9AA3B2; margin-top:4px; }
                    .pill { display:inline-block; margin-top:12px; padding:6px 12px; border-radius:999px;
                            background:%s22; color:%s; font-weight:700; letter-spacing:.04em; }
                    img { width:100%%; max-width:720px; display:block; margin:12px auto; border-radius:16px;
                          border:1px solid #1C2436; }
                    .grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; padding:0 16px 24px; }
                    .card { background:#101624; border:1px solid #1C2436; border-radius:14px; padding:12px; }
                    .k { color:#9AA3B2; font-size:.78rem; text-transform:uppercase; letter-spacing:.08em; }
                    .v { margin-top:4px; font-size:1.05rem; }
                    .full { grid-column:1 / -1; }
                    a { color:#7BFFE9; }
                    @media (max-width: 640px) { .grid { grid-template-columns:1fr; } }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>Vapez spawn</h1>
                    <div class="sub">Live look without joining Minecraft. Refreshes every 15s.</div>
                    <div class="pill">%s</div>
                  </header>
                  <img src="/map.png" alt="Top-down spawn map"/>
                  <div class="grid">
                    <div class="card full"><div class="k">What we see</div><div class="v">%s</div></div>
                    <div class="card"><div class="k">World spawn</div><div class="v">%s</div></div>
                    <div class="card"><div class="k">Structure</div><div class="v">%s</div></div>
                    <div class="card"><div class="k">Hostile mobs</div><div class="v">%d<br/><span class="sub">%s</span></div></div>
                    <div class="card"><div class="k">Players in spawn</div><div class="v">%d<br/><span class="sub">%s</span></div></div>
                    <div class="card"><div class="k">Can they break / die / spawn mobs?</div><div class="v">%s</div></div>
                    <div class="card"><div class="k">Citadel built flag</div><div class="v">%s</div></div>
                    <div class="card full"><div class="k">Last scan</div><div class="v">%s · <a href="/api/spawn">JSON</a></div></div>
                  </div>
                </body>
                </html>
                """.formatted(color, color, status, esc(summary), esc(world), esc(sig),
                mobs, esc(mobsList), players, esc(playersList), esc(prot), flag, esc(time));
    }

    private void index(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        byte[] body = renderHtml(plugin.inspector().report()).getBytes(StandardCharsets.UTF_8);
        send(ex, 200, "text/html; charset=utf-8", body);
    }

    private void api(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        byte[] body = plugin.inspector().report().toJson().getBytes(StandardCharsets.UTF_8);
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    private void map(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        byte[] png = plugin.inspector().mapPng();
        send(ex, 200, "image/png", png);
    }

    private boolean authorized(HttpExchange ex) throws IOException {
        String token = plugin.settings().dashboardToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        String query = ex.getRequestURI().getRawQuery();
        if (query != null && query.contains("token=" + token)) {
            return true;
        }
        send(ex, 401, "text/plain", "missing token".getBytes(StandardCharsets.UTF_8));
        return false;
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static String yn(boolean value) {
        return value ? "blocked" : "ALLOWED";
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
