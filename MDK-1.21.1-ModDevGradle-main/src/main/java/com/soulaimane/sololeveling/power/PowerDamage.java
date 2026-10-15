package com.soulaimane.sololeveling.power;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceKey;

/**
 * Damage sources for skills and magics.
 *
 * Use these instead of {@code damageSources().playerAttack(player)}: a player attack has the player as its
 * direct entity, and the mod's melee formula (WeaponDamageEvents) would replace the damage with the weapon's.
 * Here the player is only the "causing" entity: he still gets the kill (XP, drops), the formula leaves it alone.
 */
public final class PowerDamage {
    private PowerDamage() {}

    /** Physical damage from a skill (armour counts). */
    public static DamageSource skill(ServerPlayer player) {
        return of(player, DamageTypes.PLAYER_ATTACK);
    }

    /** Magic damage (ignores armour). */
    public static DamageSource magic(ServerPlayer player) {
        return of(player, DamageTypes.MAGIC);
    }

    public static DamageSource of(ServerPlayer player, ResourceKey<DamageType> type) {
        return new DamageSource(
                player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                null, player);
    }
}
