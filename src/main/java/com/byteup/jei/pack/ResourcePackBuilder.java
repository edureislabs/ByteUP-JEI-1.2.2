package com.byteup.jei.pack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Monta o resource pack do JEI.
 * Regras do cliente que o desenho respeita:
 *  - glifo bitmap nao pode passar de 256x256 (fundo em tiles);
 *  - avanco do glifo = (ultima coluna com tinta) * escala + 1, por isso toda imagem recebe 1 pixel
 *    quase invisivel (alpha 1) na ultima coluna: assim o avanco e sempre largura + 1;
 *  - ascent nao pode passar da altura da imagem.
 */
public final class ResourcePackBuilder {
    public static final int SCREEN_GLYPH = 0xF000;
    public static final int TILE_COLS = 3, TILE_ROWS = 2, TILE_W = 220, TILE_H = 185;
    public static final int GLYPH_ADV = 1;
    public static final int SEL_GLYPH = 0xF103;
    public static final int LINE = 9;
    private static final int ITEM_GLYPH_BASE = 0xE000;
    private static final int[] SPACE_POS_CODES = {0xF200,0xF201,0xF202,0xF203,0xF204,0xF205,0xF206,0xF207,0xF208,0xF209,0xF20A};
    private static final int[] SPACE_NEG_CODES = {0xF210,0xF211,0xF212,0xF213,0xF214,0xF215,0xF216,0xF217,0xF218,0xF219,0xF21A};
    private static final int[] SPACE_VALUES = {1,2,4,8,16,32,64,128,256,512,1024};
    private static final String ROOT = "assets/minecraft/textures/";

    private final JavaPlugin plugin;
    private final List<Material> materials;
    private final Map<Material, Integer> glyphs = new EnumMap<>(Material.class);

    public ResourcePackBuilder(JavaPlugin plugin, List<Material> materials) {
        this.plugin = plugin;
        this.materials = List.copyOf(materials);
        int code = ITEM_GLYPH_BASE;
        for (Material material : materials) {
            if (code >= 0xF000) throw new IllegalStateException("Material count exceeds private-use glyph range");
            glyphs.put(material, code++);
        }
    }

    public Map<Material, Integer> glyphMap() { return Collections.unmodifiableMap(glyphs); }

    public PackResult build() throws Exception {
        Path data = plugin.getDataFolder().toPath();
        Files.createDirectories(data);
        Path working = data.resolve("pack-work");
        if (Files.exists(working)) deleteTree(working);
        Files.createDirectories(working);

        sliceBackground(working);
        copyResource("assets/byteup/textures/gui/sel.png", working.resolve("assets/byteup/textures/gui/sel.png"));
        copyResource("assets/byteup/textures/gui/placeholder.png", working.resolve("assets/byteup/textures/gui/placeholder.png"));

        write(working.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":46,\"supported_formats\":[22,99],\"description\":\"ByteUP JEI\"}}");
        write(working.resolve("assets/byteup/font/screen_top.json"), tileFont(0));
        write(working.resolve("assets/byteup/font/screen_bot.json"), tileFont(1));
        write(working.resolve("assets/byteup/font/space.json"), spaceFont());

        Map<Material, String> icons = prepareIcons(working);
        for (int r = 0; r < LINE; r++) write(working.resolve("assets/byteup/font/row_" + r + ".json"), rowFont(r, icons));

        Path zip = data.resolve("byteup-jei-resource-pack.zip");
        zipDirectory(working, zip);
        byte[] bytes = Files.readAllBytes(zip);
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : sha1) hex.append(String.format(Locale.ROOT, "%02x", b));
        return new PackResult(zip, bytes.length, sha1, hex.toString(), Map.copyOf(glyphs), Set.of());
    }

    // ---------------------------------------------------------------- fundo

    private String tileFont(int row) {
        int ascent = 7 - row * TILE_H;
        StringBuilder out = new StringBuilder("{\"providers\":[");
        for (int c = 0; c < TILE_COLS; c++) {
            int idx = row * TILE_COLS + c;
            if (c > 0) out.append(',');
            out.append("{\"type\":\"bitmap\",\"file\":\"byteup:gui/tile_").append(idx)
                    .append(".png\",\"height\":").append(TILE_H).append(",\"ascent\":").append(ascent)
                    .append(",\"chars\":[\"").append(ch(SCREEN_GLYPH + idx)).append("\"]}");
        }
        return out.append("]}").toString();
    }

    private void sliceBackground(Path working) throws IOException {
        BufferedImage full;
        try (InputStream in = plugin.getResource("assets/byteup/textures/gui/jei_screen.png")) {
            if (in == null) throw new FileNotFoundException("jei_screen.png");
            full = ImageIO.read(in);
        }
        Path dir = working.resolve("assets/byteup/textures/gui");
        Files.createDirectories(dir);
        for (int r = 0; r < TILE_ROWS; r++) for (int c = 0; c < TILE_COLS; c++) {
            BufferedImage tile = new BufferedImage(TILE_W, TILE_H, BufferedImage.TYPE_INT_ARGB);
            tile.getGraphics().drawImage(full.getSubimage(c * TILE_W, r * TILE_H, TILE_W, TILE_H), 0, 0, null);
            ensureLastColumnInk(tile);
            ImageIO.write(tile, "png", dir.resolve("tile_" + (r * TILE_COLS + c) + ".png").toFile());
        }
    }

    /** Garante tinta (alpha 1, invisivel) na ultima coluna: o avanco do glifo vira largura + 1. */
    private static void ensureLastColumnInk(BufferedImage img) {
        int x = img.getWidth() - 1;
        for (int y = 0; y < img.getHeight(); y++) if ((img.getRGB(x, y) >>> 24) != 0) return;
        img.setRGB(x, 0, 0x01000000);
    }

    // ---------------------------------------------------------------- espacos e linhas de icones

    private String spaceFont() {
        StringBuilder out = new StringBuilder("{\"providers\":[{\"type\":\"space\",\"advances\":{");
        for (int i = 0; i < SPACE_VALUES.length; i++) {
            if (i > 0) out.append(',');
            out.append('"').append(ch(SPACE_POS_CODES[i])).append("\":").append(SPACE_VALUES[i]);
            out.append(",\"").append(ch(SPACE_NEG_CODES[i])).append("\":").append(-SPACE_VALUES[i]);
        }
        return out.append("}}]}").toString();
    }

    /** Fonte "row_r": glifos 16x16 cujo topo cai r pixels abaixo do topo da linha de texto. */
    private String rowFont(int r, Map<Material, String> icons) {
        int ascent = 7 - r;
        StringBuilder out = new StringBuilder(160_000).append("{\"providers\":[");
        for (Material material : materials) {
            Integer glyph = glyphs.get(material);
            if (glyph == null) continue;
            String file = icons.getOrDefault(material, "byteup:gui/placeholder.png");
            out.append("{\"type\":\"bitmap\",\"file\":\"").append(file)
                    .append("\",\"height\":16,\"ascent\":").append(ascent)
                    .append(",\"chars\":[\"").append(ch(glyph)).append("\"]},");
        }
        out.append("{\"type\":\"bitmap\",\"file\":\"byteup:gui/sel.png\",\"height\":8,\"ascent\":").append(ascent)
                .append(",\"chars\":[\"").append(ch(SEL_GLYPH)).append("\"]}");
        return out.append("]}").toString();
    }

    // ---------------------------------------------------------------- icones a partir do client.jar

    private Map<Material, String> prepareIcons(Path working) {
        Map<Material, String> out = new EnumMap<>(Material.class);
        Path jar;
        try { jar = resolveClientJar(); }
        catch (Exception ex) { plugin.getLogger().warning("Falha ao obter client.jar: " + ex); jar = null; }
        if (jar == null) {
            plugin.getLogger().warning("client.jar indisponivel: todos os icones usam o placeholder. Configure resource-pack.client-jar.");
            return out;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Set<String> names = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String n = entries.nextElement().getName();
                if (n.endsWith(".png") && (n.startsWith(ROOT + "item/") || n.startsWith(ROOT + "block/")))
                    names.add(n.substring(ROOT.length(), n.length() - 4));
            }
            Path dir = working.resolve("assets/byteup/textures/item");
            Files.createDirectories(dir);
            for (Material material : materials) {
                String key = firstExisting(material, names);
                if (key == null) continue;
                BufferedImage icon;
                try (InputStream in = zip.getInputStream(zip.getEntry(ROOT + key + ".png"))) { icon = firstFrame(in); }
                if (icon == null) continue;
                String file = material.name().toLowerCase(Locale.ROOT) + ".png";
                ImageIO.write(icon, "png", dir.resolve(file).toFile());
                out.put(material, "byteup:item/" + file);
            }
            plugin.getLogger().info("Icones: " + out.size() + "/" + materials.size() + " com textura vanilla (o resto usa placeholder).");
        } catch (Exception ex) {
            plugin.getLogger().warning("Falha ao ler client.jar: " + ex);
        }
        return out;
    }

    /** Recorta o primeiro quadro (texturas animadas sao tiras verticais) e garante tinta na ultima coluna. */
    private static BufferedImage firstFrame(InputStream in) throws IOException {
        BufferedImage src = ImageIO.read(in);
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        if (h < w || h % w != 0) return null;
        BufferedImage out = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src.getSubimage(0, 0, w, w), 0, 0, null);
        ensureLastColumnInk(out);
        return out;
    }

    private static String firstExisting(Material material, Set<String> names) {
        String name = material.name().toLowerCase(Locale.ROOT);
        String base = blockBase(name);
        for (String c : List.of("item/" + name, "block/" + name, "block/" + base, "block/" + base + "_side",
                "block/" + base + "_front", "block/" + base + "_top", "block/" + base + "_planks", "item/" + base)) {
            if (names.contains(c)) return c;
        }
        return null;
    }

    private static String blockBase(String name) {
        String texture = name;
        for (String suffix : new String[]{"_stairs", "_slab", "_fence_gate", "_fence", "_button",
                "_pressure_plate", "_door", "_trapdoor", "_wall"}) {
            if (texture.endsWith(suffix)) { texture = texture.substring(0, texture.length() - suffix.length()); break; }
        }
        if (name.endsWith("_carpet")) texture = name.substring(0, name.length() - 7) + "_wool";
        if (name.startsWith("potted_")) texture = "flower_pot";
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("stone_brick", "stone_bricks"), Map.entry("mossy_stone_brick", "mossy_stone_bricks"),
                Map.entry("nether_brick", "nether_bricks"), Map.entry("red_nether_brick", "red_nether_bricks"),
                Map.entry("end_stone_brick", "end_stone_bricks"), Map.entry("prismarine_brick", "prismarine_bricks"),
                Map.entry("deepslate_brick", "deepslate_bricks"), Map.entry("deepslate_tile", "deepslate_tiles"),
                Map.entry("polished_blackstone_brick", "polished_blackstone_bricks"), Map.entry("brick", "bricks"),
                Map.entry("quartz", "quartz_block_side"), Map.entry("purpur", "purpur_block"));
        texture = aliases.getOrDefault(texture, texture);
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak")) {
            if (texture.equals(wood)) return wood + "_planks";
        }
        return texture;
    }

    private Path resolveClientJar() throws Exception {
        String configured = plugin.getConfig().getString("resource-pack.client-jar", "").trim();
        if (!configured.isEmpty()) {
            Path p = Path.of(configured);
            if (Files.isRegularFile(p)) return p;
            plugin.getLogger().warning("resource-pack.client-jar nao existe: " + configured);
        }
        Path cached = plugin.getDataFolder().toPath().resolve("client.jar");
        if (Files.isRegularFile(cached)) return cached;
        if (!plugin.getConfig().getBoolean("resource-pack.download-client-jar", true)) return null;

        String version = Bukkit.getMinecraftVersion();
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        JsonObject manifest = getJson(http, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        String versionUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (v.get("id").getAsString().equals(version)) { versionUrl = v.get("url").getAsString(); break; }
        }
        if (versionUrl == null) { plugin.getLogger().warning("Versao " + version + " nao encontrada no manifest da Mojang."); return null; }
        String clientUrl = getJson(http, versionUrl).getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
        plugin.getLogger().info("Baixando client.jar da Mojang (uma vez so)...");
        Path tmp = cached.resolveSibling("client.jar.part");
        http.send(HttpRequest.newBuilder(URI.create(clientUrl)).timeout(Duration.ofMinutes(10)).build(),
                HttpResponse.BodyHandlers.ofFile(tmp));
        Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        return cached;
    }

    private static JsonObject getJson(HttpClient http, String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    // ---------------------------------------------------------------- utilidades

    private void copyResource(String resource, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) throw new FileNotFoundException("Resource missing in plugin: " + resource);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String ch(int codePoint) { return new String(Character.toChars(codePoint)); }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void zipDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String name = source.relativize(file).toString().replace(File.separatorChar, '/');
                    out.putNextEntry(new ZipEntry(name));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public record PackResult(Path file, long size, byte[] sha1, String sha1Hex,
                             Map<Material, Integer> glyphs, Set<Integer> yRows) { }
}
