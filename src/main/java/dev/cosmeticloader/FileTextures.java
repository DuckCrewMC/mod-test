package dev.cosmeticloader;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads a .png from config/cosmetic_loader/textures/ and registers it as a game texture. */
final class FileTextures {
    private FileTextures() { }

    static Identifier load(Path png, String name) throws IOException {
        String key = name.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        Identifier id = Identifier.fromNamespaceAndPath(CosmeticLoaderClient.ID, "file/" + key);
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "cosmeticloader/" + key, img));
        }
        return id;
    }
}
