package dev.cosmeticloader;

import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** The menu. Built only from stock widgets so it doesn't depend on Screen draw-method names. */
public class CosmeticScreen extends Screen {
    private enum Cat {
        MODELS("Models"), BLOCKS("Blocks"), EFFECTS("Effects"), TEXTURES("Textures"), CAPES("Capes"), ELYTRA("Elytra");
        final String label;
        Cat(String label) { this.label = label; }
        Cat next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final int[] COLORS = {0xFFFFFFFF, 0xFFFF6B8B, 0xFF4A3FE0, 0xFF4CD964, 0xFFFFD23F, 0xFF35C2FF, 0xFF222222, 0xFFFF8A3D};
    private static final String[] BLOCKS = {"diamond_block", "gold_block", "tnt", "glass", "oak_planks", "grass_block",
            "obsidian", "redstone_block", "sea_lantern", "pumpkin", "beacon", "cake"};
    private static final String[] EFFECTS = {"angry_villager", "happy_villager", "heart", "flame", "soul_fire_flame", "note",
            "enchant", "end_rod", "cloud", "crit", "snowflake", "glow", "witch", "smoke", "totem_of_undying", "firework"};
    private static final String[] TEXTURES = {"item/diamond", "item/emerald", "item/nether_star", "item/golden_apple",
            "item/apple", "item/blaze_rod", "block/diamond_block", "block/gold_block", "block/tnt_side", "block/glass",
            "block/obsidian", "block/sea_lantern"};

    private final Screen parent;
    private Cat cat = Cat.MODELS;
    private List<String> files = List.of();
    private List<String> texFiles = List.of();
    private int modelIdx, texIdx, editIdx, blockIdx, effectIdx, textureIdx;
    private String idText = "";
    private String status = "";

    public CosmeticScreen(Screen parent) {
        super(Component.literal("Cosmetic Loader"));
        this.parent = parent;
    }

    private CosmeticStore.Item current() {
        List<CosmeticStore.Item> items = CosmeticStore.items();
        if (items.isEmpty()) return null;
        editIdx = Math.floorMod(editIdx, items.size());
        return items.get(editIdx);
    }

    private void added(CosmeticStore.Item it, String errorText) {
        if (it == null) {
            status = errorText;
        } else {
            status = "";
            editIdx = CosmeticStore.items().size() - 1;
        }
        rebuildWidgets();
    }

    private void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
        } catch (Exception e) {
            status = "Couldn't open the folder.";
            rebuildWidgets();
        }
    }

    private EditBox idBox(int x, int y, int w, int h, String hint) {
        EditBox box = new EditBox(font, x, y, w, h, Component.literal(hint));
        box.setMaxLength(100);
        box.setHint(Component.literal(hint));
        box.setValue(idText);
        box.setResponder(s -> idText = s);
        return box;
    }

    private Button presetButton(int x, int y, int w, int h, String label, Runnable onPress) {
        return Button.builder(Component.literal(label), b -> onPress.run()).bounds(x, y, w, h).build();
    }

    /** The picked png from config/cosmetic_loader/textures/, or null for "none". */
    private String pickedTexFile() {
        int n = texFiles.size() + 1;
        int i = Math.floorMod(texIdx, n);
        return i == 0 ? null : texFiles.get(i - 1);
    }

    @Override
    protected void init() {
        files = CosmeticStore.available();
        texFiles = CosmeticStore.availableTextures();
        int left = width / 2 - 230, right = width / 2 + 10, w = 220, h = 20, step = 22, half = 108;
        int y = 24;

        addRenderableWidget(new StringWidget(left, 8, w * 2 + 20, 12,
                Component.literal("Cosmetic Loader (F5 for third person)"), font).alignCenter());

        // ---- left column: add things ------------------------------------
        addRenderableWidget(presetButton(left, y, w, h, "Add: " + cat.label + " (click to change)", () -> {
            cat = cat.next(); idText = ""; status = ""; rebuildWidgets();
        }));
        y += step;

        switch (cat) {
            case MODELS -> {
                addRenderableWidget(presetButton(left, y, w, h, "Open models folder", () -> openFolder(CosmeticStore.modelsDir())));
                y += step;
                String modelLabel = files.isEmpty() ? "No .obj files yet" : "Model: " + files.get(Math.floorMod(modelIdx, files.size()));
                Button pick = presetButton(left, y, 148, h, modelLabel, () -> { modelIdx++; rebuildWidgets(); });
                pick.active = !files.isEmpty();
                addRenderableWidget(pick);
                Button equip = presetButton(left + 152, y, 68, h, "Equip", () ->
                        added(CosmeticStore.equip(files.get(Math.floorMod(modelIdx, files.size()))), "That file couldn't be read as an .obj."));
                equip.active = !files.isEmpty();
                addRenderableWidget(equip);
            }
            case BLOCKS -> {
                String name = BLOCKS[Math.floorMod(blockIdx, BLOCKS.length)];
                addRenderableWidget(presetButton(left, y, w, h, "Block preset: " + name, () -> { idText = name; blockIdx++; rebuildWidgets(); }));
                y += step;
                addRenderableWidget(idBox(left, y, w, h, "Any block id, e.g. diamond_block"));
                y += step;
                addRenderableWidget(presetButton(left, y, w, h, "Add block", () ->
                        added(CosmeticStore.addBlock(idText), "Unknown block: " + idText)));
            }
            case EFFECTS -> {
                String name = EFFECTS[Math.floorMod(effectIdx, EFFECTS.length)];
                addRenderableWidget(presetButton(left, y, w, h, "Effect preset: " + name, () -> { idText = name; effectIdx++; rebuildWidgets(); }));
                y += step;
                addRenderableWidget(idBox(left, y, w, h, "Any particle id, e.g. heart"));
                y += step;
                addRenderableWidget(presetButton(left, y, w, h, "Add effect", () ->
                        added(CosmeticStore.addEffect(idText), "Unknown or unsupported effect: " + idText)));
            }
            case TEXTURES, CAPES, ELYTRA -> {
                if (cat == Cat.TEXTURES) {
                    String name = TEXTURES[Math.floorMod(textureIdx, TEXTURES.length)];
                    addRenderableWidget(presetButton(left, y, w, h, "Texture preset: " + name, () -> { idText = name; textureIdx++; rebuildWidgets(); }));
                    y += step;
                }
                String picked = pickedTexFile();
                addRenderableWidget(presetButton(left, y, 148, h, "My png: " + (picked == null ? "none" : picked), () -> { texIdx++; rebuildWidgets(); }));
                addRenderableWidget(presetButton(left + 152, y, 68, h, "Folder", () -> openFolder(CosmeticStore.texturesDir())));
                y += step;
                String hint = switch (cat) {
                    case CAPES -> "Or a resource pack texture id";
                    case ELYTRA -> "Empty = the vanilla elytra";
                    default -> "Or any texture, e.g. item/diamond";
                };
                addRenderableWidget(idBox(left, y, w, h, hint));
                y += step;
                addRenderableWidget(presetButton(left, y, w, h, "Add " + cat.label.toLowerCase(), () -> {
                    CosmeticStore.Kind kind = cat == Cat.CAPES ? CosmeticStore.Kind.CAPE
                            : cat == Cat.ELYTRA ? CosmeticStore.Kind.ELYTRA : CosmeticStore.Kind.TEXTURE;
                    String err = kind == CosmeticStore.Kind.CAPE && idText.isBlank() && pickedTexFile() == null
                            ? "Pick a png or type a texture id for the cape."
                            : "Texture not found: " + idText;
                    added(CosmeticStore.addTextured(kind, idText, pickedTexFile()), err);
                }));
            }
        }

        // ---- right column: edit the selected thing ------------------------
        CosmeticStore.Item it = current();
        int ry = 24;
        if (it != null) {
            boolean back = it.kind == CosmeticStore.Kind.CAPE || it.kind == CosmeticStore.Kind.ELYTRA;
            addRenderableWidget(presetButton(right, ry, w, h, "Editing: " + it.label(), () -> { editIdx++; rebuildWidgets(); }));
            ry += step;
            if (!back) {
                addRenderableWidget(presetButton(right, ry, w, h, "Attach to: " + it.slot.label, () -> { it.slot = it.slot.next(); rebuildWidgets(); }));
                ry += step;
            }

            addRenderableWidget(new Param(right, ry, w, h, "Left / right", -0.6f, 0.6f, it.x, false, v -> it.x = v)); ry += step;
            addRenderableWidget(new Param(right, ry, w, h, "Up / down", -0.6f, 0.6f, it.y, false, v -> it.y = v)); ry += step;
            addRenderableWidget(new Param(right, ry, w, h, "Forward", -0.6f, 0.6f, it.z, false, v -> it.z = v)); ry += step;

            if (it.kind == CosmeticStore.Kind.EFFECT) {
                addRenderableWidget(new Param(right, ry, w, h, "Ticks between bursts", 2f, 40f, it.rate, true, v -> it.rate = Math.round(v)));
                ry += step;
            } else {
                if (!back) {
                    addRenderableWidget(new Param(right, ry, w, h, "Turn", -180f, 180f, it.rotY, false, v -> it.rotY = v));
                    ry += step;
                }
                addRenderableWidget(new Param(right, ry, w, h, "Size", 0.2f, 3f, it.scale, false, v -> it.scale = v));
                ry += step;
            }

            boolean tintable = it.kind == CosmeticStore.Kind.MODEL || it.isTextured();
            if (tintable) {
                addRenderableWidget(presetButton(right, ry, half, h, "Color", () -> {
                    int i = 0;
                    for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == it.color) i = k;
                    it.color = COLORS[(i + 1) % COLORS.length];
                }));
                addRenderableWidget(presetButton(right + w - half, ry, half, h, "Remove", () -> { CosmeticStore.remove(it); rebuildWidgets(); }));
            } else {
                addRenderableWidget(presetButton(right, ry, w, h, "Remove", () -> { CosmeticStore.remove(it); rebuildWidgets(); }));
            }
        } else {
            addRenderableWidget(new StringWidget(right, ry, w, 12, Component.literal("Nothing equipped yet."), font).alignCenter());
        }

        if (!status.isEmpty()) {
            addRenderableWidget(new StringWidget(left, height - 52, w, 12, Component.literal(status), font).alignCenter());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, h).build());
    }

    @Override
    public void onClose() {
        CosmeticStore.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; } // keep the world running so you can watch effects

    private static final class Param extends AbstractSliderButton {
        private final String label;
        private final float min, max;
        private final boolean whole;
        private final Consumer<Float> set;

        Param(int x, int y, int w, int h, String label, float min, float max, float cur, boolean whole, Consumer<Float> set) {
            super(x, y, w, h, Component.empty(), (cur - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.whole = whole; this.set = set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float v = min + (max - min) * (float) value;
            setMessage(Component.literal(label + ": " + (whole ? String.valueOf(Math.round(v)) : String.format("%.2f", v))));
        }

        @Override
        protected void applyValue() { set.accept((float) (min + (max - min) * value)); }
    }
}
