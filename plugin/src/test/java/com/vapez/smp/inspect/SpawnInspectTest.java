package com.vapez.smp.inspect;

import com.vapez.smp.util.Positions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnInspectTest {

    @Test
    void citadelSignatureScoresHigh() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("0,64,0", "LODESTONE");
        samples.put("0,65,0", "BEACON");
        samples.put("center-floor", "CRYING_OBSIDIAN");
        samples.put("plaza", "AMETHYST_BLOCK");
        samples.put("north", "PURPUR_BLOCK");
        samples.put("south", "POLISHED_DEEPSLATE");
        SpawnSignature.Result result = SpawnSignature.analyze(samples);
        assertEquals(SpawnSignature.Kind.CITADEL, result.kind());
        assertTrue(result.score() >= 70, "score=" + result.score());
    }

    @Test
    void vanillaGrassIsNotCitadel() {
        Map<String, String> samples = Map.of(
                "0,64,0", "GRASS_BLOCK",
                "0,65,0", "AIR",
                "center-floor", "DIRT",
                "plaza", "STONE",
                "north", "OAK_LOG",
                "south", "SHORT_GRASS"
        );
        SpawnSignature.Result result = SpawnSignature.analyze(samples);
        assertEquals(SpawnSignature.Kind.VANILLA, result.kind());
    }

    @Test
    void transferredCustomBuildIsDetected() {
        Map<String, String> samples = Map.of(
                "0,64,0", "QUARTZ_BLOCK",
                "0,65,0", "GLASS",
                "center-floor", "OAK_PLANKS",
                "plaza", "CHEST",
                "north", "WHITE_CONCRETE",
                "south", "SEA_LANTERN"
        );
        SpawnSignature.Result result = SpawnSignature.analyze(samples);
        assertEquals(SpawnSignature.Kind.CUSTOM, result.kind());
    }

    @Test
    void emptyAirIsMissingSpawn() {
        Map<String, String> samples = Map.of(
                "0,64,0", "AIR",
                "0,65,0", "minecraft:cave_air"
        );
        assertEquals(SpawnSignature.Kind.EMPTY, SpawnSignature.analyze(samples).kind());
    }

    @Test
    void protectionRadiusIsSquareChebyshev() {
        assertTrue(Positions.inside(0, 0, 0, 0, 128));
        assertTrue(Positions.inside(128, 128, 0, 0, 128));
        assertFalse(Positions.inside(129, 0, 0, 0, 128));
        assertTrue(Positions.inside(50, 10, 40, 0, 16));
        assertFalse(Positions.inside(50, 10, 0, 0, 16));
    }

    @Test
    void reportJsonAndHtmlAndPng() throws Exception {
        SpawnReport report = new SpawnReport();
        report.timestamp = "2026-10-06T09:00:00Z";
        report.world = "world";
        report.spawnX = 0;
        report.spawnY = 67;
        report.spawnZ = 0;
        report.citadelFlag = true;
        report.signature = SpawnSignature.Kind.CITADEL;
        report.signatureScore = 95;
        report.summary = "Aether Citadel markers found (lodestone/beacon/plaza).";
        report.samples.put("0,64,0", "LODESTONE");
        report.samples.put("0,65,0", "BEACON");
        report.hostileMobs = 0;
        report.playersInSpawn = 1;
        report.playerNames.add("Steve");
        report.structureOk = true;
        report.mobsOk = true;
        report.protectionOk = true;
        report.denyBreak = true;
        report.denyDamage = true;
        report.denyMobs = true;
        report.motdLine = "Citadel OK · mobs 0 · safe";

        String json = report.toJson();
        assertTrue(json.contains("\"signature\":\"CITADEL\""));
        assertTrue(json.contains("\"hostileMobs\":0"));
        assertTrue(json.contains("\"all\":true"));
        assertTrue(report.allOk());

        String[][] grid = citadelGrid(48);
        byte[] png = SpawnMapRenderer.renderPng(grid, 4, report, List.of(new int[]{10, 10}), List.of(new int[]{24, 24}));
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);

        String html = SpawnDashboard.renderHtml(report);
        assertTrue(html.contains("Citadel OK") || html.contains("Aether Citadel"));
        assertTrue(html.contains("/map.png"));
        assertTrue(html.contains("blocked"));

        Path out = Path.of("target/spawn-inspect");
        Files.createDirectories(out);
        Files.write(out.resolve("spawn-map.png"), png);
        Files.writeString(out.resolve("spawn-status.json"), json);
        Files.writeString(out.resolve("index.html"), html.replace("src=\"/map.png\"", "src=\"spawn-map.png\""));
        assertTrue(Files.size(out.resolve("spawn-map.png")) > 100);
    }

    static String[][] citadelGrid(int radius) {
        int size = radius * 2 + 1;
        String[][] grid = new String[size][size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int dx = x - radius;
                int dz = z - radius;
                int d2 = dx * dx + dz * dz;
                String mat = "DEEPSLATE";
                if (d2 <= 4) {
                    mat = "CRYING_OBSIDIAN";
                } else if (d2 <= 16) {
                    mat = "PURPUR_BLOCK";
                } else if (d2 <= 36) {
                    mat = "AMETHYST_BLOCK";
                } else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    mat = "POLISHED_DEEPSLATE";
                } else if (d2 <= 12 * 12 && d2 >= 9 * 9) {
                    mat = "POLISHED_DEEPSLATE";
                } else if (d2 <= 20 * 20 && d2 >= 17 * 17) {
                    mat = "AMETHYST_BLOCK";
                } else if (d2 <= 28 * 28 && d2 >= 25 * 25) {
                    mat = "POLISHED_BLACKSTONE";
                } else if (d2 <= 36 * 36 && d2 >= 33 * 33) {
                    mat = "SMOOTH_QUARTZ";
                } else if (Math.abs(dx) == 40 || Math.abs(dz) == 40) {
                    mat = "DEEPSLATE_TILES";
                } else if (d2 > 42 * 42) {
                    mat = "WATER";
                }
                if (dx == 0 && dz == 0) {
                    mat = "LODESTONE";
                }
                grid[z][x] = mat;
            }
        }
        return grid;
    }
}
