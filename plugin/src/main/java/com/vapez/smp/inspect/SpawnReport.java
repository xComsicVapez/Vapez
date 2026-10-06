package com.vapez.smp.inspect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpawnReport {

    public String timestamp = Instant.EPOCH.toString();
    public String world = "world";
    public int spawnX;
    public int spawnY;
    public int spawnZ;
    public boolean citadelFlag;
    public SpawnSignature.Kind signature = SpawnSignature.Kind.EMPTY;
    public int signatureScore;
    public String summary = "No scan yet.";
    public Map<String, String> samples = new LinkedHashMap<>();
    public int hostileMobs;
    public List<String> hostileTypes = new ArrayList<>();
    public int playersInSpawn;
    public List<String> playerNames = new ArrayList<>();
    public int protectionRadius = 128;
    public boolean guardEnabled = true;
    public boolean denyBreak = true;
    public boolean denyDamage = true;
    public boolean denyMobs = true;
    public boolean structureOk;
    public boolean mobsOk;
    public boolean protectionOk;
    public String motdLine = "Spawn: scanning…";

    public boolean allOk() {
        return structureOk && mobsOk && protectionOk;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        field(sb, "timestamp", timestamp, true);
        field(sb, "world", world, true);
        sb.append("\"spawn\":{\"x\":").append(spawnX).append(",\"y\":").append(spawnY)
                .append(",\"z\":").append(spawnZ).append("},");
        sb.append("\"citadelFlag\":").append(citadelFlag).append(',');
        field(sb, "signature", signature.name(), true);
        sb.append("\"signatureScore\":").append(signatureScore).append(',');
        field(sb, "summary", summary, true);
        sb.append("\"samples\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : samples.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
        }
        sb.append("},");
        sb.append("\"hostileMobs\":").append(hostileMobs).append(',');
        sb.append("\"hostileTypes\":");
        strings(sb, hostileTypes);
        sb.append(',');
        sb.append("\"playersInSpawn\":").append(playersInSpawn).append(',');
        sb.append("\"playerNames\":");
        strings(sb, playerNames);
        sb.append(',');
        sb.append("\"protection\":{");
        sb.append("\"radius\":").append(protectionRadius).append(',');
        sb.append("\"guardEnabled\":").append(guardEnabled).append(',');
        sb.append("\"denyBreak\":").append(denyBreak).append(',');
        sb.append("\"denyDamage\":").append(denyDamage).append(',');
        sb.append("\"denyMobs\":").append(denyMobs).append("},");
        sb.append("\"ok\":{");
        sb.append("\"structure\":").append(structureOk).append(',');
        sb.append("\"mobs\":").append(mobsOk).append(',');
        sb.append("\"protection\":").append(protectionOk).append(',');
        sb.append("\"all\":").append(allOk()).append("},");
        field(sb, "motdLine", motdLine, false);
        sb.append('}');
        return sb.toString();
    }

    public List<String> consoleLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Spawn @ " + world + " " + spawnX + ", " + spawnY + ", " + spawnZ);
        lines.add("Structure: " + signature + " (" + signatureScore + ") — " + summary);
        lines.add("Citadel flag: " + citadelFlag);
        lines.add("Hostile mobs in spawn: " + hostileMobs + (hostileTypes.isEmpty() ? "" : " " + hostileTypes));
        lines.add("Players in spawn: " + playersInSpawn + (playerNames.isEmpty() ? "" : " " + playerNames));
        lines.add("Protection r=" + protectionRadius
                + " break=" + denyBreak
                + " death=" + denyDamage
                + " mobs=" + denyMobs
                + " guard=" + guardEnabled);
        lines.add(allOk() ? "STATUS: OK" : "STATUS: CHECK NEEDED");
        if (!samples.isEmpty()) {
            lines.add("Blocks: " + samples);
        }
        return lines;
    }

    private static void field(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        if (comma) {
            sb.append(',');
        }
    }

    private static void strings(StringBuilder sb, List<String> values) {
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        sb.append(']');
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
