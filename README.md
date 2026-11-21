# Cosmetic Loader (Fabric, Minecraft 26.3)

Client-side mod. Open the menu with the **Cosmetics...** button in the escape (pause) menu, or press **K**. Press F5 to see yourself.
The "Add:" button at the top of the menu switches between what you can add.

- **Models**: drop `.obj` files in `.minecraft/config/cosmetic_loader/models/`
  (the "Open models folder" button opens it), pick one, press Equip.
- **Blocks**: pick a block preset or type any block id (e.g. `diamond_block`).
- **Effects**: pick a preset (angry_villager, heart, flame, ...) or type a particle id.
  Only particles without extra options work (no dust or block-crack particles).
- **Textures**: a flat square showing a texture: `item/diamond`, `block/stone`, a resource pack path like
  `mypack:textures/hat.png`, or one of your own pngs.
- **Capes**: pick a png (standard 64x32 cape layout, HD capes work too) or type a resource pack texture id.
- **Elytra**: leave everything empty for the vanilla elytra, or pick a png / type a texture id
  (standard elytra texture layout).

Your own pngs go in `config/cosmetic_loader/textures/` (the "Folder" button opens it).
Models, blocks, textures and effects can be attached to the head, back, chest, or either hand,
and moved, turned, and resized. Capes and elytra always sit on the back. Your loadout is saved in
`config/cosmetic_loader/loadout.json`.

## Build
Needs JDK 25. Easiest: generate a blank 26.3 project at https://fabricmc.net/develop/template
(mod id `cosmeticloader`), then replace its `src/` folder with this one and run:

    ./gradlew build

The jar lands in `build/libs/` (use the one without `-sources`). Copy it and Fabric API into `mods/`.

## Build without installing anything (GitHub)
Quickest: after the build finishes, open the repo's **Releases** page (right sidebar) and download the
`.jar` directly, no unzipping. Details:

1. Create a new GitHub repository and upload everything in this folder (keep `.github/` and `src/`).
2. Open the repo's **Actions** tab. The "Build mod jar" workflow runs on its own (or press "Run workflow").
3. When it finishes, open the run and download the **cosmetic-loader-jar** artifact.
   Use the jar that doesn't end in `-sources`.
