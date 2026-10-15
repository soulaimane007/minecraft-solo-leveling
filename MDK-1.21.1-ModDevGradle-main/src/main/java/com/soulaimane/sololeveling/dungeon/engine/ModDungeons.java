package com.soulaimane.sololeveling.dungeon.engine;

/**
 * ALL THE DUNGEONS OF THE MOD ARE REGISTERED HERE.
 *
 * To add a dungeon: write it (see {@link ExampleDungeon}, a small two-level dungeon) and register it here:
 *
 *   public static final DungeonDefinition GOBLIN_CAVE = DungeonRegistry.register(
 *           DungeonDefinition.builder("goblin_cave")
 *                   .name("Goblin Cave")
 *                   .requires(Requirements.atLevel(5))
 *                   .level(...)          // level 1
 *                   .level(...)          // level 2
 *                   .build());
 *
 * That is all: a "Dungeon Key" item for it appears in the creative tab, /sl dungeon give goblin_cave gives one,
 * and the key opens the gate. Nothing else has to be edited (no item, no texture, no language line).
 *
 * There is NO real dungeon yet: the example is compiled but not registered. To try it, add the line
 *   ExampleDungeon.register();
 * in init() below, restart, then /sl dungeon give example_crypt and right-click the key.
 */
public final class ModDungeons {
    private ModDungeons() {}

    /** Called once while the mod starts. Add new dungeons above. */
    public static void init() {
        DungeonRegistry.validate();
    }
}
