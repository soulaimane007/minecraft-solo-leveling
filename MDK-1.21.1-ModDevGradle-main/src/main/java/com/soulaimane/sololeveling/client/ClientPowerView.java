package com.soulaimane.sololeveling.client;

import com.soulaimane.sololeveling.power.PowerView;

/** The player as the client knows him (the numbers the server synced). Used by the skill tree and tooltips. */
public final class ClientPowerView implements PowerView {
    public static final ClientPowerView INSTANCE = new ClientPowerView();

    private ClientPowerView() {}

    @Override public int level() { return ClientSystemDataCache.level; }

    @Override public int rankOrdinal() { return ClientSystemDataCache.rankOrdinal; }

    @Override public int stat(String name) { return ClientSystemDataCache.getStat(name); }

    @Override public int skillRank(String skillId) { return ClientPowerCache.rank(skillId); }

    @Override public boolean knowsMagic(String magicId) { return ClientPowerCache.magics.contains(magicId); }
}
