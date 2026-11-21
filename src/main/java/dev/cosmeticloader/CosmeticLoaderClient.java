package dev.cosmeticloader;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class CosmeticLoaderClient implements ClientModInitializer {
    public static final String ID = "cosmeticloader";
    private static KeyMapping openKey;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        CosmeticStore.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.cosmeticloader.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.gui.setScreen(new CosmeticScreen(mc.gui.screen()));
            tickEffects(mc);
        });

        // Adds a "Cosmetics" button to the escape (pause) menu.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) addPauseMenuButton(client, screen, scaledWidth, scaledHeight);
        });

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer avatarRenderer) {
                helper.register(new CosmeticLayer((RenderLayerParent) avatarRenderer));
            }
        });
    }

    /**
     * Puts a full-width "Cosmetics" button directly under the vanilla button stack. The position is
     * measured from the buttons that are already on the screen, so it follows the pause menu layout
     * at any GUI scale. If there is no room below the stack it goes in the top-left corner instead.
     */
    private static void addPauseMenuButton(Minecraft client, Screen screen, int screenW, int screenH) {
        var buttons = Screens.getButtons(screen);
        if (buttons.isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxBottom = Integer.MIN_VALUE;
        for (var b : buttons) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX() + b.getWidth());
            maxBottom = Math.max(maxBottom, b.getY() + b.getHeight());
        }

        int h = 20;
        int x = minX, y = maxBottom + 8, w = maxX - minX;
        if (y + h > screenH - 4) { x = 8; y = 8; w = 120; }

        buttons.add(Button.builder(Component.translatable("cosmeticloader.menu.button"),
                        b -> client.gui.setScreen(new CosmeticScreen(screen)))
                .bounds(x, y, w, h).build());
    }

    /** Spawns the particle effects (angry villager, hearts, ...) around the player. */
    private static void tickEffects(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        long t = level.getGameTime();
        double yaw = Math.toRadians(p.yBodyRot);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);

        for (CosmeticStore.Item it : CosmeticStore.items()) {
            if (it.kind != CosmeticStore.Kind.EFFECT || it.particle == null) continue;
            if (t % Math.max(1, it.rate) != 0) continue;

            // local frame: x = left, y = up, z = forward; rotated by the body's yaw
            double lx = it.slot.ex + it.x, ly = it.slot.ey + it.y, lz = it.slot.ez + it.z;
            double wx = p.getX() + lx * cos - lz * sin + (level.random.nextDouble() - 0.5) * 0.15;
            double wz = p.getZ() + lx * sin + lz * cos + (level.random.nextDouble() - 0.5) * 0.15;
            level.addParticle(it.particle, wx, p.getY() + ly, wz, 0.0, 0.02, 0.0);
        }
    }
}
