package com.vapez.smp.inspect;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Top-down spawn map from a grid of material names. No Minecraft client required.
 */
public final class SpawnMapRenderer {

    private static final Map<String, Integer> COLORS = new HashMap<>();

    static {
        color("AIR", 0x0B1020);
        color("CAVE_AIR", 0x0B1020);
        color("LODESTONE", 0xC5CDD4);
        color("BEACON", 0x7BFFE9);
        color("LIGHT", 0xE8F7FF);
        color("AMETHYST_BLOCK", 0xC084FC);
        color("PURPUR_BLOCK", 0xC9A0DC);
        color("PURPUR_PILLAR", 0xB084C8);
        color("CRYING_OBSIDIAN", 0x4B1E6D);
        color("OBSIDIAN", 0x1B1028);
        color("POLISHED_DEEPSLATE", 0x4A5158);
        color("DEEPSLATE", 0x2C3036);
        color("DEEPSLATE_TILES", 0x3A4048);
        color("POLISHED_ANDESITE", 0x7A7E7A);
        color("POLISHED_BLACKSTONE", 0x2A242C);
        color("GILDED_BLACKSTONE", 0x8A6A2A);
        color("SMOOTH_QUARTZ", 0xE8E0D8);
        color("QUARTZ_PILLAR", 0xF2EAE0);
        color("OXIDIZED_COPPER", 0x4FA387);
        color("WAXED_COPPER_BULB", 0xC47A3A);
        color("IRON_BLOCK", 0xD0D5D8);
        color("NETHERITE_BLOCK", 0x2B2B2F);
        color("WATER", 0x3B6FA0);
        color("LAVA", 0xE25822);
        color("GRASS_BLOCK", 0x5D9B4B);
        color("DIRT", 0x8B5A2B);
        color("STONE", 0x888888);
        color("OAK_PLANKS", 0xB8945A);
        color("OAK_LOG", 0x6B4A2A);
        color("GLASS", 0xA8D2E8);
        color("SEA_LANTERN", 0xC8F2E8);
        color("SOUL_LANTERN", 0x4AB0C8);
        color("LANTERN", 0xE0A040);
        color("CAMPFIRE", 0xD07030);
        color("CHEST", 0xC8A050);
        color("PLAYER_HEAD", 0xFF4D6D);
        color("ZOMBIE_HEAD", 0xFF6B35);
    }

    private SpawnMapRenderer() {
    }

    public static byte[] renderPng(String[][] materials, int scale, SpawnReport report,
                                  List<int[]> mobPixels, List<int[]> playerPixels) {
        try {
            BufferedImage image = render(materials, scale, report, mobPixels, playerPixels);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode spawn map", ex);
        }
    }

    public static void writePng(Path path, byte[] png) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, png);
    }

    public static void writePng(OutputStream out, byte[] png) throws IOException {
        out.write(png);
    }

    public static BufferedImage render(String[][] materials, int scale, SpawnReport report,
                                      List<int[]> mobPixels, List<int[]> playerPixels) {
        int h = materials.length;
        int w = h == 0 ? 0 : materials[0].length;
        int pad = 48;
        int imgW = Math.max(1, w * scale) + pad * 2;
        int imgH = Math.max(1, h * scale) + pad * 2 + 36;
        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x070A12));
        g.fillRect(0, 0, imgW, imgH);
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                g.setColor(new Color(colorOf(materials[z][x])));
                g.fillRect(pad + x * scale, pad + z * scale, scale, scale);
            }
        }
        int cx = pad + (w / 2) * scale + scale / 2;
        int cz = pad + (h / 2) * scale + scale / 2;
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x7BFFE9));
        g.drawOval(cx - 6, cz - 6, 12, 12);
        if (mobPixels != null) {
            g.setColor(new Color(0xFF6B35));
            for (int[] p : mobPixels) {
                g.fillOval(pad + p[0] * scale, pad + p[1] * scale, Math.max(4, scale), Math.max(4, scale));
            }
        }
        if (playerPixels != null) {
            g.setColor(new Color(0x4D96FF));
            for (int[] p : playerPixels) {
                g.fillRect(pad + p[0] * scale, pad + p[1] * scale, Math.max(4, scale), Math.max(4, scale));
            }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(new Color(0xE8F1FF));
        String title = report == null ? "Vapez spawn" : "Vapez spawn — " + report.signature;
        g.drawString(title, 16, 28);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(0x9AA3B2));
        String footer = report == null ? "" : report.motdLine + "   mobs " + report.hostileMobs;
        g.drawString(footer, 16, imgH - 14);
        g.setColor(new Color(0x7BFFE9));
        g.drawString("spawn", cx + 10, cz - 8);
        g.dispose();
        return image;
    }

    public static int colorOf(String material) {
        String key = SpawnSignature.normalize(material);
        Integer mapped = COLORS.get(key);
        if (mapped != null) {
            return mapped;
        }
        int hash = key.hashCode();
        int r = 40 + (Math.abs(hash) % 180);
        int gr = 40 + (Math.abs(hash >> 8) % 180);
        int b = 40 + (Math.abs(hash >> 16) % 180);
        return (r << 16) | (gr << 8) | b;
    }

    private static void color(String name, int rgb) {
        COLORS.put(name.toUpperCase(Locale.ROOT), rgb);
    }
}
