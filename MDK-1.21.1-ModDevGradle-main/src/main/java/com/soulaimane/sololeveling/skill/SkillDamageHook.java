package com.soulaimane.sololeveling.skill;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A passive skill that changes damage. Change the amount with event.setAmount(...), or cancel the hit.
 *
 * For {@code onDealDamage} the context player is the attacker, for {@code onTakeDamage} it is the victim.
 * The hook runs after the mod's own melee damage formula, so event.getAmount() is already the final melee number.
 */
@FunctionalInterface
public interface SkillDamageHook {
    void apply(SkillContext ctx, LivingIncomingDamageEvent event);
}
