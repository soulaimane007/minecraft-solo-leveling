package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.data.PlayerActionBar;
import com.soulaimane.sololeveling.magic.Magic;
import com.soulaimane.sololeveling.magic.MagicRegistry;
import com.soulaimane.sololeveling.skill.Skill;
import com.soulaimane.sololeveling.skill.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What an action bar entry ("skill:sololeveling:power_strike" / "magic:sololeveling:flame_bolt") looks like:
 * icon, colour and name. Used by the action bar HUD and by the skill tree screen.
 */
public record PowerEntry(String entry, ItemStack icon, int color, Component name) {

    /** @return null for an empty slot or an entry that does not exist any more */
    public static PowerEntry of(String entry) {
        if (entry == null || entry.isEmpty()) return null;

        if (entry.startsWith(PlayerActionBar.SKILL_PREFIX)) {
            Skill skill = SkillRegistry.byId(entry.substring(PlayerActionBar.SKILL_PREFIX.length()));
            return skill == null ? null : new PowerEntry(entry, skill.iconStack(), skill.branch().color(), skill.displayName());
        }
        if (entry.startsWith(PlayerActionBar.MAGIC_PREFIX)) {
            Magic magic = MagicRegistry.byId(entry.substring(PlayerActionBar.MAGIC_PREFIX.length()));
            return magic == null ? null : new PowerEntry(entry, magic.iconStack(), magic.color(), magic.displayName());
        }
        return null;
    }
}
