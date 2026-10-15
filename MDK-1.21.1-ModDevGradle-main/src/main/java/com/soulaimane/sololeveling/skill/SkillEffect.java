package com.soulaimane.sololeveling.skill;

/**
 * What an active skill does when the player presses its key.
 * Return true when the skill really happened: the stamina / mana cost is charged and the cooldown starts only then.
 * Return false (after telling the player why, if you like) to cancel without any cost.
 */
@FunctionalInterface
public interface SkillEffect {
    boolean use(SkillContext ctx);
}
