package dev.cosmeticloader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal Wavefront .obj reader. Centers the model and scales it to fit half a block. */
public final class ObjModel {
    private final float[] pos; // 9 floats per triangle
    private final float[] nrm;

    private ObjModel(float[] pos, float[] nrm) { this.pos = pos; this.nrm = nrm; }

    private static int idx(String s, int size) {
        int i = Integer.parseInt(s);
        return i > 0 ? i - 1 : size + i;
    }

    public static ObjModel parse(Path file) throws IOException {
        List<float[]> v = new ArrayList<>(), n = new ArrayList<>();
        List<float[][]> tris = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 4) continue;
            switch (t[0]) {
                case "v" -> v.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                case "vn" -> n.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                case "f" -> {
                    int k = t.length - 1;
                    int[] vi = new int[k], ni = new int[k];
                    for (int i = 0; i < k; i++) {
                        String[] p = t[i + 1].split("/");
                        vi[i] = idx(p[0], v.size());
                        ni[i] = p.length > 2 && !p[2].isEmpty() ? idx(p[2], n.size()) : -1;
                    }
                    for (int i = 1; i < k - 1; i++) {
                        float[] a = v.get(vi[0]), b = v.get(vi[i]), c = v.get(vi[i + 1]);
                        float[] fn = faceNormal(a, b, c);
                        tris.add(new float[][]{a, b, c,
                                ni[0] >= 0 ? n.get(ni[0]) : fn,
                                ni[i] >= 0 ? n.get(ni[i]) : fn,
                                ni[i + 1] >= 0 ? n.get(ni[i + 1]) : fn});
                    }
                }
                default -> { }
            }
        }
        if (tris.isEmpty()) throw new IOException("No faces found in " + file.getFileName());

        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[][] tr : tris) for (int i = 0; i < 3; i++) for (int a = 0; a < 3; a++) {
            min[a] = Math.min(min[a], tr[i][a]);
            max[a] = Math.max(max[a], tr[i][a]);
        }
        float size = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        float k = size > 1e-6f ? 0.5f / size : 1f;

        float[] pos = new float[tris.size() * 9], nrm = new float[tris.size() * 9];
        for (int t = 0; t < tris.size(); t++) {
            for (int i = 0; i < 3; i++) for (int a = 0; a < 3; a++) {
                pos[t * 9 + i * 3 + a] = (tris.get(t)[i][a] - (min[a] + max[a]) / 2f) * k;
                nrm[t * 9 + i * 3 + a] = tris.get(t)[3 + i][a];
            }
        }
        return new ObjModel(pos, nrm);
    }

    private static float[] faceNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-9f ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l};
    }

    /** Draws every triangle as a degenerate quad (entity render types use QUADS). */
    public void emit(PoseStack.Pose pose, VertexConsumer buf, int light, int argb) {
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (int t = 0; t < pos.length; t += 9) {
            for (int k = 0; k < 4; k++) {
                int c = t + Math.min(k, 2) * 3;
                buf.addVertex(pose, pos[c], pos[c + 1], pos[c + 2])
                        .setColor(r, g, b, a)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nrm[c], nrm[c + 1], nrm[c + 2]);
            }
        }
    }
}
