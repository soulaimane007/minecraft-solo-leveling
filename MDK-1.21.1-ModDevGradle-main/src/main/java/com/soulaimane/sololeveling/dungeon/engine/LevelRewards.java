package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import com.soulaimane.sololeveling.event.PlayerDataEvents;
import com.soulaimane.sololeveling.skill.SkillManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a player receives for clearing a level (or a whole dungeon): System XP, stat points (K), skill points (J), items.
 *
 *   LevelRewards.builder().xp(500).statPoints(3).skillPoints(1).item(ModItems.ZOMBIE_CRYSTAL, 10).build()
 */
public final class LevelRewards {

    public static final LevelRewards NONE = new LevelRewards(0, 0, 0, List.of());

    private final int xp;
    private final int statPoints;
    private final int skillPoints;
    private final List<Supplier<ItemStack>> items;

    private LevelRewards(int xp, int statPoints, int skillPoints, List<Supplier<ItemStack>> items) {
        this.xp = xp;
        this.statPoints = statPoints;
        this.skillPoints = skillPoints;
        this.items = items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return xp <= 0 && statPoints <= 0 && skillPoints <= 0 && items.isEmpty();
    }

    /** Pays the player and tells him what he got. */
    public void give(ServerPlayer p) {
        if (isEmpty()) return;

        MutableComponent message = Component.literal("§6[System] §aReward:");

        if (xp > 0) {
            PlayerDataEvents.giveSystemXp(p, xp);
            message.append(Component.literal(" §e+" + xp + " XP"));
        }
        if (statPoints > 0) {
            PlayerSystemData data = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
            p.setData(ModAttachments.PLAYER_SYSTEM_DATA, data.withStatPoints(data.statPoints() + statPoints));
            PlayerDataEvents.syncToClient(p);
            message.append(Component.literal(" §a+" + statPoints + " SP"));
        }
        if (skillPoints > 0) {
            SkillManager.grantPoints(p, skillPoints);
            message.append(Component.literal(" §b+" + skillPoints + " Skill Point" + (skillPoints > 1 ? "s" : "")));
        }
        for (Supplier<ItemStack> supplier : items) {
            ItemStack stack = supplier.get();
            if (stack.isEmpty()) continue;
            message.append(Component.literal(" §f+" + stack.getCount() + " ").append(stack.getHoverName()));
            ItemHandlerHelper.giveItemToPlayer(p, stack);
        }
        p.sendSystemMessage(message);
    }

    public static final class Builder {
        private int xp;
        private int statPoints;
        private int skillPoints;
        private final List<Supplier<ItemStack>> items = new ArrayList<>();

        /** System XP (the same XP as killing mobs: it can level you up). */
        public Builder xp(int amount) {
            this.xp = Math.max(0, amount);
            return this;
        }

        /** Stat points, spent with K. */
        public Builder statPoints(int amount) {
            this.statPoints = Math.max(0, amount);
            return this;
        }

        /** Skill points, spent with J. */
        public Builder skillPoints(int amount) {
            this.skillPoints = Math.max(0, amount);
            return this;
        }

        /** Items. A stack bigger than the item allows is split by the game when it is given. */
        public Builder item(Supplier<? extends ItemLike> item, int count) {
            if (count > 0) {
                items.add(() -> new ItemStack(item.get(), count));
            }
            return this;
        }

        /** An item with custom data (a Magic Paper, a Dungeon Key...): you build the stack yourself. */
        public Builder stack(Supplier<ItemStack> stack) {
            items.add(stack);
            return this;
        }

        public LevelRewards build() {
            return new LevelRewards(xp, statPoints, skillPoints, List.copyOf(items));
        }
    }
}
