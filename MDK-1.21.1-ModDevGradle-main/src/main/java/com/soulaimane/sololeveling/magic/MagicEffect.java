package com.soulaimane.sololeveling.magic;

/**
 * What a magic does when the player casts it.
 * Return true when the spell really happened: the mana cost is charged and the cooldown starts only then.
 * Return false (after telling the player why, if you like) to cancel without any cost.
 */
@FunctionalInterface
public interface MagicEffect {
    boolean cast(MagicContext ctx);
}
