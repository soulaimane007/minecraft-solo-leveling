package com.soulaimane.sololeveling;

import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.data.PlayerDungeonData;
import com.soulaimane.sololeveling.data.PlayerDungeonProgress;
import com.soulaimane.sololeveling.data.PlayerMagicData;
import com.soulaimane.sololeveling.data.PlayerSkillData;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SoloLeveling.MOD_ID);

    public static final Supplier<AttachmentType<PlayerSystemData>> PLAYER_SYSTEM_DATA =
            ATTACHMENT_TYPES.register("player_system_data", () ->
                    AttachmentType.builder(() -> PlayerSystemData.DEFAULT)
                            .serialize(PlayerSystemData.CODEC)
                            .copyOnDeath()
                            .build());

    /** Dungeon bookkeeping: starting key flag + return position for the System Key. */
    public static final Supplier<AttachmentType<PlayerDungeonData>> PLAYER_DUNGEON_DATA =
            ATTACHMENT_TYPES.register("player_dungeon_data", () ->
                    AttachmentType.builder(() -> PlayerDungeonData.DEFAULT)
                            .serialize(PlayerDungeonData.CODEC)
                            .copyOnDeath()
                            .build());

    /** Skill tree: the ranks of the learned skills and the skill points spent. */
    public static final Supplier<AttachmentType<PlayerSkillData>> PLAYER_SKILL_DATA =
            ATTACHMENT_TYPES.register("player_skill_data", () ->
                    AttachmentType.builder(() -> PlayerSkillData.DEFAULT)
                            .serialize(PlayerSkillData.CODEC)
                            .copyOnDeath()
                            .build());

    /** Magic: the magics learned by burning Magic Papers. */
    public static final Supplier<AttachmentType<PlayerMagicData>> PLAYER_MAGIC_DATA =
            ATTACHMENT_TYPES.register("player_magic_data", () ->
                    AttachmentType.builder(() -> PlayerMagicData.DEFAULT)
                            .serialize(PlayerMagicData.CODEC)
                            .copyOnDeath()
                            .build());

    /** The action bar shared by skills and magics. */
    public static final Supplier<AttachmentType<PlayerActionBar>> PLAYER_ACTION_BAR =
            ATTACHMENT_TYPES.register("player_action_bar", () ->
                    AttachmentType.builder(() -> PlayerActionBar.DEFAULT)
                            .serialize(PlayerActionBar.CODEC)
                            .copyOnDeath()
                            .build());

    /** Levels cleared in each dungeon of the dungeon framework. */
    public static final Supplier<AttachmentType<PlayerDungeonProgress>> PLAYER_DUNGEON_PROGRESS =
            ATTACHMENT_TYPES.register("player_dungeon_progress", () ->
                    AttachmentType.builder(() -> PlayerDungeonProgress.DEFAULT)
                            .serialize(PlayerDungeonProgress.CODEC)
                            .copyOnDeath()
                            .build());

    /** Combat state flag — used for poise regen reset. Not persisted. */
    public static final Supplier<AttachmentType<Integer>> LAST_HIT_TICK =
            ATTACHMENT_TYPES.register("last_hit_tick", () ->
                    AttachmentType.builder(() -> 0).build());
}
