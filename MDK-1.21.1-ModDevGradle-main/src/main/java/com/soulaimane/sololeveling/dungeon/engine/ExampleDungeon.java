package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.ModItems;
import com.soulaimane.sololeveling.item.MagicPaperItem;
import com.soulaimane.sololeveling.magic.ModMagics;
import com.soulaimane.sololeveling.power.Requirements;
import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.world.entity.EntityType;

/**
 * A small two-level dungeon that shows how a dungeon is written. It is NOT registered: the mod has no real
 * dungeon yet. To try it, add this line to {@link ModDungeons#init()}:
 *
 *     ExampleDungeon.register();
 *
 * then start the game, run  /sl dungeon give example_crypt  and right-click the key.
 * Copy this file, change the names and numbers, and you have a new dungeon.
 *
 * What it does:
 *   level 1  "The Entrance"  - 10 zombies and skeletons keep coming; kill them all
 *   level 2  "The Crypt Lord" - 15 more, then a boss
 *   after level 2 the players get a Flame Bolt paper and are taken out
 */
public final class ExampleDungeon {
    private ExampleDungeon() {}

    /** Adds the example dungeon to the registry. */
    public static DungeonDefinition register() {
        return DungeonRegistry.register(build());
    }

    /** The definition, not registered. */
    public static DungeonDefinition build() {

        // ── Level 1: waves only (no boss) ──
        WaveLevel entrance = WaveLevel.builder("The Entrance")
                .arena(ArenaKit.box(25, 25, 8, ArenaKit.Style.CRYPT))       // a 25 x 25 room, 8 blocks high
                .killTarget(10)                                             // kills needed to win
                .maxAlive(4)                                                // mobs alive at the same time
                .mob(MobSpec.of(EntityType.ZOMBIE)
                        .health(14).damage(2).xp(10).weight(3)
                        .drop(ModItems.ZOMBIE_CRYSTAL, 1, 1, 0.3))          // 30 % chance of a crystal
                .mob(MobSpec.of(EntityType.SKELETON)
                        .health(10).xp(12).weight(2)
                        .finalizeSpawn())                                   // gives the skeleton its bow
                .rewards(LevelRewards.builder().xp(100).skillPoints(1).build())
                .build();

        // ── Level 2: waves, then a boss ──
        WaveLevel throne = WaveLevel.builder("The Crypt Lord")
                .arena(ArenaKit.box(31, 31, 10, ArenaKit.Style.CRYPT))
                .killTarget(15)
                .maxAlive(5)
                .spawnInterval(25)
                .mob(MobSpec.of(EntityType.ZOMBIE)
                        .health(18).damage(3).xp(14).weight(3)
                        .drop(ModItems.ZOMBIE_CRYSTAL, 1, 2, 0.4))
                .mob(MobSpec.of(EntityType.SKELETON)
                        .health(12).xp(14).weight(2)
                        .finalizeSpawn())
                .boss(MobSpec.of(EntityType.ZOMBIE)
                        .name("Crypt Lord")
                        .health(160).damage(5).scale(2.2).knockbackResistance(0.8)
                        .xp(250)
                        .drop(ModItems.ZOMBIE_CRYSTAL, 4, 6, 1.0))
                .rewards(LevelRewards.builder().xp(300).statPoints(3).skillPoints(2).build())
                .build();

        return DungeonDefinition.builder("example_crypt")
                .name("Example Crypt")
                .description("A small two-level dungeon that shows how a dungeon is written.")
                .color(0xFF8FA3B8)
                .requires(Requirements.atLevel(1))
                .level(entrance)
                .level(throne)
                .completionRewards(LevelRewards.builder()
                        .stack(() -> MagicPaperItem.create(ModMagics.FLAME_BOLT))   // a Magic Paper as a prize
                        .build())
                .onEnter(player -> SLUtil.chat(player, "§7The air is cold and smells of dust."))
                .build();
    }
}
