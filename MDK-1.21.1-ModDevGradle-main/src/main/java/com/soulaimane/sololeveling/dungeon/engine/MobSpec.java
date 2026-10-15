package com.soulaimane.sololeveling.dungeon.engine;

import com.soulaimane.sololeveling.util.SLUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A recipe for a dungeon mob: which creature, how strong, what it is worth and what it drops.
 * Set only what you want to change; everything else stays as the creature normally is.
 *
 *   MobSpec.of(EntityType.ZOMBIE).name("Goblin").health(20).damage(3).xp(15).weight(3)
 *   MobSpec.of(ModEntities.GOBLIN)                          (a mob of your own, registered with a DeferredHolder)
 *
 * Dungeon mobs never despawn, never pick up items and drop nothing but what is listed here.
 */
public final class MobSpec {

    /** One possible drop. */
    public record Drop(Supplier<? extends ItemLike> item, int min, int max, double chance) {
        ItemStack roll(RandomSource random) {
            if (chance < 1.0 && random.nextDouble() >= chance) return ItemStack.EMPTY;
            int count = max <= min ? min : min + random.nextInt(max - min + 1);
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(item.get(), count);
        }
    }

    private final Supplier<? extends EntityType<?>> type;
    private String name;
    private double health = -1;
    private double damage = -1;
    private double speed = -1;
    private double scale = -1;
    private double knockbackResistance = -1;
    private int xp = -1;
    private int weight = 1;
    private boolean finalizeSpawn;
    private boolean keepVanillaDrops;
    private final Map<EquipmentSlot, Supplier<ItemStack>> gear = new EnumMap<>(EquipmentSlot.class);
    private final List<Drop> drops = new ArrayList<>();

    private MobSpec(Supplier<? extends EntityType<?>> type) {
        this.type = type;
    }

    public static MobSpec of(EntityType<?> type) {
        return new MobSpec(() -> type);
    }

    public static MobSpec of(Supplier<? extends EntityType<?>> type) {
        return new MobSpec(type);
    }

    // ─── Recipe ──────────────────────────────────────────────────────

    /** A custom name (shown when you look at the mob, and in the boss bar of a WaveLevel boss). */
    public MobSpec name(String name) {
        this.name = name;
        return this;
    }

    public MobSpec health(double health) {
        this.health = health;
        return this;
    }

    /** Melee damage. */
    public MobSpec damage(double damage) {
        this.damage = damage;
        return this;
    }

    /** Movement speed (a zombie walks at 0.23). */
    public MobSpec speed(double speed) {
        this.speed = speed;
        return this;
    }

    /** Size: 1 = normal, 2.4 = the Zombie King. */
    public MobSpec scale(double scale) {
        this.scale = scale;
        return this;
    }

    /** 0 = none, 1 = cannot be pushed back. */
    public MobSpec knockbackResistance(double value) {
        this.knockbackResistance = value;
        return this;
    }

    /** System XP given to the killer. Without this the mod's normal XP for that creature is used. */
    public MobSpec xp(int xp) {
        this.xp = xp;
        return this;
    }

    /** How often this mob is chosen in a WaveLevel compared to the others (default 1). */
    public MobSpec weight(int weight) {
        this.weight = Math.max(1, weight);
        return this;
    }

    /**
     * Lets the game finish the spawn like a natural spawn: skeletons get their bow, creatures pick a variant.
     * Zombies may then also turn out to be babies, so it is off by default.
     */
    public MobSpec finalizeSpawn() {
        this.finalizeSpawn = true;
        return this;
    }

    /** Keeps what the creature normally drops (rotten flesh, bones...) next to the listed drops. */
    public MobSpec keepVanillaDrops() {
        this.keepVanillaDrops = true;
        return this;
    }

    /** Puts an item in an equipment slot: gear for show, or a weapon (it adds its own damage). */
    public MobSpec equip(EquipmentSlot slot, Supplier<ItemStack> stack) {
        gear.put(slot, stack);
        return this;
    }

    /** A drop that always falls. */
    public MobSpec drop(Supplier<? extends ItemLike> item, int count) {
        return drop(item, count, count, 1.0);
    }

    /** A drop with a chance (1.0 = always) and a random amount between min and max. */
    public MobSpec drop(Supplier<? extends ItemLike> item, int min, int max, double chance) {
        drops.add(new Drop(item, min, max, chance));
        return this;
    }

    // ─── Read by the engine ──────────────────────────────────────────

    public String displayName() { return name; }

    public int xp() { return xp; }

    public int weight() { return weight; }

    public boolean keepsVanillaDrops() { return keepVanillaDrops; }

    public List<Drop> drops() { return drops; }

    /** The mob, not yet added to the world (or null if the creature cannot be made). */
    public Mob create(ServerLevel world, Vec3 pos) {
        Entity entity = type.get().create(world);
        if (!(entity instanceof Mob mob)) {
            return null;
        }

        mob.moveTo(pos.x, pos.y, pos.z, world.random.nextFloat() * 360.0f, 0.0f);
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);

        if (finalizeSpawn) {
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        }
        if (name != null) {
            mob.setCustomName(Component.literal(name));
        }

        if (health > 0) {
            SLUtil.setBase(mob, Attributes.MAX_HEALTH, health);
            mob.setHealth((float) health);
        }
        if (damage >= 0) {
            SLUtil.setBase(mob, Attributes.ATTACK_DAMAGE, damage);
        }
        if (speed > 0) {
            SLUtil.setBase(mob, Attributes.MOVEMENT_SPEED, speed);
        }
        if (scale > 0) {
            SLUtil.setBase(mob, Attributes.SCALE, scale);
        }
        if (knockbackResistance >= 0) {
            SLUtil.setBase(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResistance);
        }
        SLUtil.setBase(mob, Attributes.FOLLOW_RANGE, 48.0);

        for (Map.Entry<EquipmentSlot, Supplier<ItemStack>> entry : gear.entrySet()) {
            mob.setItemSlot(entry.getKey(), entry.getValue().get());
        }
        return mob;
    }
}
