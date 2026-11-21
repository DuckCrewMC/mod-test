package dev.cosmeticloader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws equipped models, blocks, textures, capes and elytra on the local player.
 * (Effects are particles, see CosmeticLoaderClient.) Capes and elytra are drawn as flat panels
 * using the standard 64x32 cape / elytra texture layout, so any normal cape or elytra png works.
 */
public class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/misc/white.png");

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    /** One flat quad. Each vertex is {x, y, z, u, v}; UVs are in pixels of a 64x32 texture unless texW/texH say otherwise. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buf, int light, int argb,
                             float nz, float texW, float texH, float[]... verts) {
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (float[] c : verts) {
            buf.addVertex(pose, c[0], c[1], c[2])
                    .setColor(r, g, b, a)
                    .setUv(c[3] / texW, c[4] / texH)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0f, 0f, nz);
        }
    }

    private static void flatTexture(PoseStack.Pose p, VertexConsumer buf, int light, int argb) {
        quad(p, buf, light, argb, 1f, 1f, 1f,
                new float[]{-0.5f, -0.5f, 0f, 0f, 1f}, new float[]{0.5f, -0.5f, 0f, 1f, 1f},
                new float[]{0.5f, 0.5f, 0f, 1f, 0f}, new float[]{-0.5f, 0.5f, 0f, 0f, 0f});
    }

    /** 10x16 px cape: outside at u 1..11 / v 1..17, inside at u 12..22. Hangs from y=0 down to y=-1. */
    private static void cape(PoseStack.Pose p, VertexConsumer buf, int light, int argb) {
        float hw = 0.3125f;
        quad(p, buf, light, argb, -1f, 64f, 32f,
                new float[]{hw, 0f, -0.03f, 1f, 1f}, new float[]{-hw, 0f, -0.03f, 11f, 1f},
                new float[]{-hw, -1f, -0.03f, 11f, 17f}, new float[]{hw, -1f, -0.03f, 1f, 17f});
        quad(p, buf, light, argb, 1f, 64f, 32f,
                new float[]{-hw, 0f, 0.03f, 12f, 1f}, new float[]{hw, 0f, 0.03f, 22f, 1f},
                new float[]{hw, -1f, 0.03f, 22f, 17f}, new float[]{-hw, -1f, 0.03f, 12f, 17f});
    }

    /** One 10x20 px wing: outside at u 24..34 / v 2..22, inside at u 36..46. s = +1 or -1 picks the side. */
    private static void wing(PoseStack.Pose p, VertexConsumer buf, int light, int argb, float s) {
        float w = 0.625f * s, h = -1.25f;
        quad(p, buf, light, argb, -1f, 64f, 32f,
                new float[]{0f, 0f, -0.02f, 24f, 2f}, new float[]{w, 0f, -0.02f, 34f, 2f},
                new float[]{w, h, -0.02f, 34f, 22f}, new float[]{0f, h, -0.02f, 24f, 22f});
        quad(p, buf, light, argb, 1f, 64f, 32f,
                new float[]{0f, 0f, 0.02f, 36f, 2f}, new float[]{w, 0f, 0.02f, 46f, 2f},
                new float[]{w, h, 0.02f, 46f, 22f}, new float[]{0f, h, 0.02f, 36f, 22f});
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        // Only your own player wears your cosmetics.
        if (mc.player == null || state.id != mc.player.getId()) return;

        PlayerModel m = getParentModel();
        for (CosmeticStore.Item it : CosmeticStore.items()) {
            final Identifier tex = it.isTextured() ? CosmeticStore.tex(it) : null;
            boolean ok = switch (it.kind) {
                case MODEL -> it.model != null;
                case BLOCK -> it.block != null;
                case EFFECT -> false;
                case TEXTURE, CAPE, ELYTRA -> tex != null;
            };
            if (!ok) continue;

            boolean back = it.kind == CosmeticStore.Kind.CAPE || it.kind == CosmeticStore.Kind.ELYTRA;
            pose.pushPose();
            switch (it.slot) {
                case HEAD -> m.head.translateAndRotate(pose);
                case BACK, CHEST -> m.body.translateAndRotate(pose);
                case LEFT_HAND -> m.leftArm.translateAndRotate(pose);
                case RIGHT_HAND -> m.rightArm.translateAndRotate(pose);
            }
            // Same orientation trick vanilla head items use: upright, facing forward.
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.scale(1f, -1f, -1f);
            if (back) {
                pose.translate(it.x, it.y, -0.14f + it.z); // just behind the shoulders
            } else {
                pose.translate(it.slot.bx + it.x, it.slot.by + it.y, it.slot.bz + it.z);
                pose.mulPose(Axis.YP.rotationDegrees(it.rotY));
            }
            pose.scale(it.scale, it.scale, it.scale);
            final int light2 = light, color = it.color;

            switch (it.kind) {
                case BLOCK -> {
                    pose.translate(-0.5f, -0.5f, -0.5f); // center the block on the anchor
                    out.submitBlock(pose, it.block, light, OverlayTexture.NO_OVERLAY, 0);
                }
                case TEXTURE -> out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(tex),
                        (p, buf) -> flatTexture(p, buf, light2, color));
                case CAPE -> {
                    pose.mulPose(Axis.XP.rotationDegrees(8f)); // hangs slightly away from the back
                    out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(tex),
                            (p, buf) -> cape(p, buf, light2, color));
                }
                case ELYTRA -> {
                    for (float s : new float[]{1f, -1f}) {
                        pose.pushPose();
                        pose.mulPose(Axis.XP.rotationDegrees(15f));
                        pose.mulPose(Axis.ZP.rotationDegrees(15f * s));
                        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(tex),
                                (p, buf) -> wing(p, buf, light2, color, s));
                        pose.popPose();
                    }
                }
                default -> {
                    ObjModel model = it.model;
                    out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(WHITE),
                            (p, buf) -> model.emit(p, buf, light2, color));
                }
            }
            pose.popPose();
        }
    }
}
