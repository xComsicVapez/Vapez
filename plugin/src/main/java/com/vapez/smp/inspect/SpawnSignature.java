package com.vapez.smp.inspect;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classifies spawn from sampled block names so a transferred world, the Aether
 * Citadel, or a vanilla dirt spawn can be told apart without a client.
 */
public final class SpawnSignature {

    public enum Kind {
        CITADEL,
        CUSTOM,
        VANILLA,
        EMPTY
    }

    public record Result(Kind kind, int score, String summary, Map<String, String> samples) {
    }

    private static final Set<String> CITADEL_CORE = Set.of(
            "LODESTONE", "BEACON", "AMETHYST_BLOCK", "PURPUR_BLOCK", "CRYING_OBSIDIAN",
            "POLISHED_DEEPSLATE", "DEEPSLATE_TILES", "SMOOTH_QUARTZ", "QUARTZ_PILLAR",
            "POLISHED_BLACKSTONE", "GILDED_BLACKSTONE", "OXIDIZED_COPPER"
    );
    private static final Set<String> VANILLA_SURFACE = Set.of(
            "GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "SAND", "RED_SAND",
            "STONE", "GRAVEL", "SNOW", "SNOW_BLOCK", "ICE", "WATER", "OAK_LOG",
            "BIRCH_LOG", "SPRUCE_LOG", "OAK_LEAVES", "BIRCH_LEAVES", "SHORT_GRASS",
            "TALL_GRASS", "FERN", "MOSS_BLOCK"
    );
    private static final Set<String> EMPTY = Set.of("AIR", "CAVE_AIR", "VOID_AIR", "LIGHT");

    private SpawnSignature() {
    }

    public static Result analyze(Map<String, String> samples) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (samples != null) {
            samples.forEach((k, v) -> copy.put(k, normalize(v)));
        }
        int score = 0;
        int citadelHits = 0;
        int vanillaHits = 0;
        int emptyHits = 0;
        int customHits = 0;
        for (String mat : copy.values()) {
            if (CITADEL_CORE.contains(mat)) {
                citadelHits++;
            } else if (VANILLA_SURFACE.contains(mat)) {
                vanillaHits++;
            } else if (EMPTY.contains(mat)) {
                emptyHits++;
            } else if (!mat.isBlank()) {
                customHits++;
            }
        }
        if ("LODESTONE".equals(copy.get("0,64,0")) || "LODESTONE".equals(copy.get("center"))) {
            score += 40;
        }
        if ("BEACON".equals(copy.get("0,65,0")) || "BEACON".equals(copy.get("center+1"))) {
            score += 30;
        }
        score += Math.min(25, citadelHits * 5);
        if (vanillaHits > citadelHits) {
            score -= 15;
        }
        Kind kind;
        String summary;
        if (score >= 70 && citadelHits >= 3) {
            kind = Kind.CITADEL;
            summary = "Aether Citadel markers found (lodestone/beacon/plaza).";
        } else if (copy.isEmpty() || emptyHits == copy.size()) {
            kind = Kind.EMPTY;
            summary = "Spawn samples are air — chunks may not be loaded or spawn is missing.";
        } else if (vanillaHits >= Math.max(3, copy.size() / 2) && citadelHits == 0) {
            kind = Kind.VANILLA;
            summary = "Looks like a vanilla world spawn (grass/dirt/trees), not the Citadel.";
        } else {
            kind = Kind.CUSTOM;
            summary = "Spawn is a custom or transferred build, not the stock Aether Citadel.";
        }
        if (kind == Kind.CUSTOM && customHits == 0 && citadelHits > 0 && score < 70) {
            summary = "Partial Citadel blocks present — build may still be generating.";
        }
        return new Result(kind, Math.max(0, Math.min(100, score)), summary, copy);
    }

    public static String normalize(String material) {
        if (material == null) {
            return "AIR";
        }
        String trimmed = material.trim().toUpperCase(Locale.ROOT);
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.isEmpty() ? "AIR" : trimmed;
    }
}
