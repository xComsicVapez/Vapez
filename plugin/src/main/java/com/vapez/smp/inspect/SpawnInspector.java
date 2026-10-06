package com.vapez.smp.inspect;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Positions;
import com.vapez.smp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class SpawnInspector {

    private final VapezPlugin plugin;
    private final SpawnDashboard dashboard;
    private final AtomicReference<SpawnReport> report = new AtomicReference<>(new SpawnReport());
    private final AtomicReference<byte[]> mapPng = new AtomicReference<>(emptyPng());
    private String lastSignature;

    public SpawnInspector(VapezPlugin plugin) {
        this.plugin = plugin;
        this.dashboard = new SpawnDashboard(plugin);
    }

    public void start() {
        dashboard.start();
        Tasks.repeat(40L, plugin.settings().inspectIntervalTicks(), this::scan);
    }

    public void shutdown() {
        dashboard.stop();
    }

    public SpawnReport report() {
        return report.get();
    }

    public byte[] mapPng() {
        return mapPng.get();
    }

    public void scan() {
        World world = Bukkit.getWorld(plugin.settings().overworldName());
        if (world == null) {
            return;
        }
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX();
        int cy = spawn.getBlockY();
        int cz = spawn.getBlockZ();
        int radius = plugin.settings().spawnRadius();
        int mapR = plugin.settings().mapRadius();

        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("center", world.getBlockAt(cx, Math.max(world.getMinHeight(), cy - 3), cz).getType().name());
        samples.put("0,64,0", world.getBlockAt(0, 64, 0).getType().name());
        samples.put("0,65,0", world.getBlockAt(0, 65, 0).getType().name());
        samples.put("center-floor", world.getBlockAt(cx, 64, cz).getType().name());
        samples.put("center+1", world.getBlockAt(cx, 65, cz).getType().name());
        samples.put("north", world.getBlockAt(cx, 64, cz - 5).getType().name());
        samples.put("south", world.getBlockAt(cx, 64, cz + 5).getType().name());
        samples.put("west", world.getBlockAt(cx - 5, 64, cz).getType().name());
        samples.put("east", world.getBlockAt(cx + 5, 64, cz).getType().name());
        samples.put("plaza", world.getBlockAt(3, 64, 3).getType().name());

        int size = mapR * 2 + 1;
        String[][] grid = new String[size][size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int wx = cx - mapR + x;
                int wz = cz - mapR + z;
                grid[z][x] = surface(world, wx, wz, cy);
            }
        }

        List<String> hostiles = new ArrayList<>();
        List<int[]> mobPixels = new ArrayList<>();
        List<Monster> toRemove = new ArrayList<>();
        for (Monster monster : world.getEntitiesByClass(Monster.class)) {
            Location loc = monster.getLocation();
            if (!Positions.inside(loc.getBlockX(), loc.getBlockZ(), cx, cz, radius)) {
                continue;
            }
            hostiles.add(monster.getType().name().toLowerCase() + "@" + loc.getBlockX() + "," + loc.getBlockZ());
            int px = loc.getBlockX() - cx + mapR;
            int pz = loc.getBlockZ() - cz + mapR;
            if (px >= 0 && pz >= 0 && px < size && pz < size) {
                mobPixels.add(new int[]{px, pz});
            }
            if (plugin.settings().purgeMobs()) {
                toRemove.add(monster);
            }
        }
        if (plugin.settings().purgeMobs()) {
            for (Monster monster : toRemove) {
                monster.remove();
            }
        }

        List<String> players = new ArrayList<>();
        List<int[]> playerPixels = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            if (!Positions.inside(loc.getBlockX(), loc.getBlockZ(), cx, cz, radius)) {
                continue;
            }
            players.add(player.getName());
            int px = loc.getBlockX() - cx + mapR;
            int pz = loc.getBlockZ() - cz + mapR;
            if (px >= 0 && pz >= 0 && px < size && pz < size) {
                playerPixels.add(new int[]{px, pz});
            }
        }

        Byte flag = world.getPersistentDataContainer().get(plugin.items().keys().citadelBuilt, PersistentDataType.BYTE);
        SpawnSignature.Result signature = SpawnSignature.analyze(samples);

        SpawnReport next = new SpawnReport();
        next.timestamp = Instant.now().toString();
        next.world = world.getName();
        next.spawnX = cx;
        next.spawnY = cy;
        next.spawnZ = cz;
        next.citadelFlag = flag != null && flag == (byte) 1;
        next.signature = signature.kind();
        next.signatureScore = signature.score();
        next.summary = signature.summary();
        next.samples = signature.samples();
        next.hostileMobs = hostiles.size();
        next.hostileTypes = hostiles;
        next.playersInSpawn = players.size();
        next.playerNames = players;
        next.protectionRadius = radius;
        next.guardEnabled = plugin.settings().fallbackGuard();
        next.denyBreak = plugin.settings().denyBreak();
        next.denyDamage = plugin.settings().denyDamage();
        next.denyMobs = plugin.settings().denyMobs();
        next.structureOk = signature.kind() == SpawnSignature.Kind.CITADEL
                || signature.kind() == SpawnSignature.Kind.CUSTOM;
        next.mobsOk = hostiles.isEmpty();
        next.protectionOk = next.guardEnabled && next.denyBreak && next.denyDamage && next.denyMobs;
        next.motdLine = motd(next);
        if (!hostiles.isEmpty() && plugin.settings().purgeMobs()) {
            next.summary = signature.summary() + " Removed " + hostiles.size() + " hostile mob(s).";
        }
        report.set(next);

        byte[] png = SpawnMapRenderer.renderPng(grid, plugin.settings().mapScale(), next, mobPixels, playerPixels);
        mapPng.set(png);
        Path dir = plugin.getDataFolder().toPath();
        Tasks.async(() -> {
            try {
                SpawnMapRenderer.writePng(dir.resolve("spawn-map.png"), png);
                java.nio.file.Files.writeString(dir.resolve("spawn-status.json"), next.toJson());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not write spawn snapshot files", ex);
            }
        });

        String sigKey = next.signature + ":" + next.hostileMobs + ":" + next.structureOk;
        if (lastSignature == null) {
            plugin.getLogger().info("Spawn inspect: " + next.motdLine + " — " + next.summary);
        } else if (!sigKey.equals(lastSignature)) {
            plugin.getLogger().warning("Spawn inspect changed: " + next.motdLine + " — " + next.summary);
            pingDiscord(next);
        }
        lastSignature = sigKey;
    }

    private static String surface(World world, int x, int z, int hintY) {
        int min = world.getMinHeight();
        int from = Math.min(world.getMaxHeight() - 1, hintY + 12);
        for (int y = from; y >= min; y--) {
            var type = world.getBlockAt(x, y, z).getType();
            if (!type.isAir()) {
                return type.name();
            }
        }
        return "AIR";
    }

    private static String motd(SpawnReport report) {
        String struct = switch (report.signature) {
            case CITADEL -> "Citadel OK";
            case CUSTOM -> "Custom spawn";
            case VANILLA -> "Vanilla spawn";
            case EMPTY -> "Spawn missing";
        };
        return struct + " · mobs " + report.hostileMobs + (report.protectionOk ? " · safe" : " · UNPROTECTED");
    }

    private void pingDiscord(SpawnReport next) {
        String url = plugin.settings().discordWebhook();
        if (url == null || url.isBlank()) {
            return;
        }
        String json = "{\"content\":\"Vapez spawn: " + SpawnReport.escape(next.motdLine)
                + " — " + SpawnReport.escape(next.summary) + "\"}";
        Tasks.async(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ex) {
                plugin.getLogger().warning("Discord spawn webhook failed: " + ex.getMessage());
            }
        });
    }

    private static byte[] emptyPng() {
        String[][] grid = new String[][]{{"AIR"}};
        return SpawnMapRenderer.renderPng(grid, 8, new SpawnReport(), List.of(), List.of());
    }
}
