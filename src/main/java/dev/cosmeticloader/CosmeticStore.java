package dev.cosmeticloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class CosmeticStore {
    /**
     * Base offsets are in blocks. b* = relative to the body part (models, blocks, textures),
     * e* = relative to the player's feet in "left / up / forward" terms (effects).
     */
    public enum Slot {
        HEAD("Head", 0f, 0.5f, 0f, 0f, 2.0f, 0f),
        BACK("Back", 0f, -0.375f, -0.2f, 0f, 1.2f, -0.4f),
        CHEST("Chest", 0f, -0.375f, 0.2f, 0f, 1.2f, 0.4f),
        LEFT_HAND("Left hand", 0f, -0.7f, 0f, 0.45f, 0.9f, 0.15f),
        RIGHT_HAND("Right hand", 0f, -0.7f, 0f, -0.45f, 0.9f, 0.15f);

        public final String label;
        public final float bx, by, bz, ex, ey, ez;
        Slot(String label, float bx, float by, float bz, float ex, float ey, float ez) {
            this.label = label; this.bx = bx; this.by = by; this.bz = bz;
            this.ex = ex; this.ey = ey; this.ez = ez;
        }
        public Slot next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Kind { MODEL, BLOCK, EFFECT, TEXTURE, CAPE, ELYTRA }

    public static final class Item {
        public Kind kind = Kind.MODEL;
        public String file;     // MODEL: .obj file name
        public String id;       // BLOCK / EFFECT: registry id; TEXTURE / CAPE / ELYTRA: texture id
        public String texFile;  // TEXTURE / CAPE / ELYTRA: png in config/cosmetic_loader/textures/
        public Slot slot = Slot.HEAD;
        public float x, y, z, rotY, scale = 1f;
        public int rate = 8;    // EFFECT: ticks between bursts
        public int color = 0xFFFFFFFF;
        public transient ObjModel model;
        public transient BlockState block;
        public transient SimpleParticleType particle;
        public transient Identifier tex;
        public transient boolean texFailed;

        public boolean isTextured() { return kind == Kind.TEXTURE || kind == Kind.CAPE || kind == Kind.ELYTRA; }

        public String label() {
            String src = texFile != null ? texFile : shortTexture(id);
            return switch (kind) {
                case MODEL -> file;
                case BLOCK -> "Block: " + shortId(id);
                case EFFECT -> "Effect: " + shortId(id);
                case TEXTURE -> "Texture: " + src;
                case CAPE -> "Cape: " + src;
                case ELYTRA -> "Elytra: " + src;
            };
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Item> ITEMS = new ArrayList<>();

    public static List<Item> items() { return ITEMS; }
    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("cosmetic_loader"); }
    public static Path modelsDir() { return root().resolve("models"); }
    public static Path texturesDir() { return root().resolve("textures"); }

    public static String shortId(String s) { return s != null && s.startsWith("minecraft:") ? s.substring(10) : s; }

    public static String shortTexture(String s) {
        if (s == null) return "";
        String t = shortId(s);
        if (t.startsWith("textures/")) t = t.substring(9);
        return t.endsWith(".png") ? t.substring(0, t.length() - 4) : t;
    }

    private static List<String> listFiles(Path dir, String ext) {
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                return s.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase().endsWith(ext))
                        .sorted().toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    public static List<String> available() { return listFiles(modelsDir(), ".obj"); }
    public static List<String> availableTextures() { return listFiles(texturesDir(), ".png"); }

    // ---- registry lookups -------------------------------------------------

    public static String normalize(String raw) {
        Identifier i = raw == null ? null : Identifier.tryParse(raw.trim().toLowerCase());
        return i == null ? null : i.toString();
    }

    public static BlockState blockState(String id) {
        String n = normalize(id);
        if (n == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(n))
                .map(Block::defaultBlockState)
                .filter(s -> !s.isAir())
                .orElse(null);
    }

    public static SimpleParticleType particle(String id) {
        String n = normalize(id);
        if (n == null) return null;
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.tryParse(n))
                .filter(p -> p instanceof SimpleParticleType)
                .map(p -> (SimpleParticleType) p)
                .orElse(null);
    }

    /**
     * Accepts "item/diamond", "block/stone", "stone" (= block/stone), or a full path like
     * "minecraft:textures/entity/steve.png" or "mypack:textures/hat.png".
     */
    public static Identifier texture(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return null;
        String ns = "minecraft";
        int c = s.indexOf(':');
        if (c >= 0) { ns = s.substring(0, c); s = s.substring(c + 1); }
        if (!s.startsWith("textures/")) {
            if (!s.contains("/")) s = "block/" + s;
            s = "textures/" + s;
        }
        if (!s.endsWith(".png")) s += ".png";
        return Identifier.tryParse(ns + ":" + s);
    }

    public static boolean textureExists(Identifier id) {
        return id != null && Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
    }

    private static Identifier defaultElytra() {
        for (String p : new String[]{"minecraft:textures/entity/equipment/wings/elytra.png", "minecraft:textures/entity/elytra.png"}) {
            Identifier id = Identifier.tryParse(p);
            if (textureExists(id)) return id;
        }
        return null;
    }

    /** The texture for a TEXTURE / CAPE / ELYTRA item. Png files are loaded the first time they're needed. */
    public static Identifier tex(Item it) {
        if (it.tex != null) return it.tex;
        if (it.texFile != null && !it.texFailed) {
            try {
                it.tex = FileTextures.load(texturesDir().resolve(it.texFile), it.texFile);
            } catch (Exception e) {
                it.texFailed = true;
            }
        }
        return it.tex;
    }

    // ---- adding / removing -----------------------------------------------

    public static Item equip(String file) {
        try {
            Item it = new Item();
            it.kind = Kind.MODEL;
            it.file = file;
            it.model = ObjModel.parse(modelsDir().resolve(file));
            ITEMS.add(it);
            save();
            return it;
        } catch (Exception e) {
            return null;
        }
    }

    public static Item addBlock(String id) {
        BlockState s = blockState(id);
        if (s == null) return null;
        Item it = new Item();
        it.kind = Kind.BLOCK;
        it.id = normalize(id);
        it.block = s;
        it.scale = 0.5f;
        it.y = 0.25f; // sits on top of the head by default
        ITEMS.add(it);
        save();
        return it;
    }

    public static Item addEffect(String id) {
        SimpleParticleType p = particle(id);
        if (p == null) return null;
        Item it = new Item();
        it.kind = Kind.EFFECT;
        it.id = normalize(id);
        it.particle = p;
        ITEMS.add(it);
        save();
        return it;
    }

    /** Adds a texture, cape or elytra. A typed texture id wins over a picked file; an elytra with neither is the vanilla one. */
    public static Item addTextured(Kind kind, String rawId, String file) {
        Item it = new Item();
        it.kind = kind;
        it.slot = kind == Kind.TEXTURE ? Slot.HEAD : Slot.BACK;
        if (rawId != null && !rawId.isBlank()) {
            Identifier id = texture(rawId);
            if (!textureExists(id)) return null;
            it.id = id.toString();
            it.tex = id;
        } else if (file != null) {
            if (!Files.exists(texturesDir().resolve(file))) return null;
            it.texFile = file;
        } else if (kind == Kind.ELYTRA) {
            Identifier id = defaultElytra();
            if (id == null) return null;
            it.id = id.toString();
            it.tex = id;
        } else {
            return null;
        }
        if (kind == Kind.TEXTURE) { it.scale = 0.5f; it.y = 0.3f; } // floats above the head
        ITEMS.add(it);
        save();
        return it;
    }

    public static void remove(Item it) { ITEMS.remove(it); save(); }

    public static void save() {
        try {
            Files.createDirectories(root());
            Files.writeString(root().resolve("loadout.json"), GSON.toJson(ITEMS));
        } catch (IOException ignored) { }
    }

    private static boolean resolve(Item it) {
        try {
            if (it.kind == null) it.kind = Kind.MODEL;
            switch (it.kind) {
                case MODEL -> it.model = ObjModel.parse(modelsDir().resolve(it.file));
                case BLOCK -> it.block = blockState(it.id);
                case EFFECT -> it.particle = particle(it.id);
                case TEXTURE, CAPE, ELYTRA -> {
                    if (it.texFile == null) it.tex = Identifier.tryParse(it.id);
                }
            }
            return switch (it.kind) {
                case MODEL -> it.model != null;
                case BLOCK -> it.block != null;
                case EFFECT -> it.particle != null;
                case TEXTURE, CAPE, ELYTRA -> it.texFile != null ? Files.exists(texturesDir().resolve(it.texFile)) : it.tex != null;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public static void load() {
        ITEMS.clear();
        Path f = root().resolve("loadout.json");
        if (!Files.exists(f)) return;
        try {
            List<Item> saved = GSON.fromJson(Files.readString(f), new TypeToken<List<Item>>() { }.getType());
            if (saved == null) return;
            for (Item it : saved) if (resolve(it)) ITEMS.add(it);
        } catch (Exception ignored) { }
    }
}
