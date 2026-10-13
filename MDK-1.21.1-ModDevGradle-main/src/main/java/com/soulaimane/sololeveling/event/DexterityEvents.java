package com.soulaimane.sololeveling.event;

import com.soulaimane.sololeveling.ModAttachments;
import com.soulaimane.sololeveling.data.ModDataMaps;
import com.soulaimane.sololeveling.data.PlayerSystemData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class DexterityEvents {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ─────────────────────────────────────────────────────────────────
    // 1. Cancel attack if weapon is restricted
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack held = p.getMainHandItem();
        if (isRestricted(p, held)) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal(
                    "§c[System] " + held.getHoverName().getString()
                            + " requires Dexterity " + getRequirement(held)), true);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. Cancel right-click use
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack held = p.getItemInHand(e.getHand());
        if (isRestricted(p, held)) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal(
                    "§c[System] " + held.getHoverName().getString()
                            + " requires Dexterity " + getRequirement(held)), true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack held = p.getItemInHand(e.getHand());
        if (isRestricted(p, held)) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal(
                    "§c[System] " + held.getHoverName().getString()
                            + " requires Dexterity " + getRequirement(held)), true);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. Immediate unequip when a restricted item lands in a slot
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        EquipmentSlot slot = e.getSlot();
        ItemStack stack = e.getTo();
        if (stack.isEmpty()) return;
        if (isRestricted(p, stack)) {
            unequip(p, slot, stack);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. Per-tick safety net
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (isRestricted(p, stack)) {
                unequip(p, slot, stack);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. Tooltip: show requirement + weight
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        if (stack.isEmpty()) return;
        int req = getRequirement(stack);
        Double weight = stack.getItemHolder().getData(ModDataMaps.WEIGHT);

        if (weight != null && weight > 0) {
            e.getToolTip().add(Component.literal("§7Weight: §f" + String.format("%.1f", weight)));
        }
        if (req > 0) {
            boolean restricted = true;
            if (e.getEntity() instanceof Player p) {
                PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
                restricted = d.dexterity() < req;
            }
            e.getToolTip().add(Component.literal(
                    (restricted ? "§c" : "§a") + "Requires Dexterity " + req));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    private static void unequip(ServerPlayer p, EquipmentSlot slot, ItemStack stack) {
        ItemStack toReturn = stack.copy();

        // Clear the slot first
        p.setItemSlot(slot, ItemStack.EMPTY);

        // Force menu sync so the client sees the empty hand immediately
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();

        // Return to inventory or drop
        if (!p.getInventory().add(toReturn)) {
            p.drop(toReturn, false);
        }

        p.displayClientMessage(Component.literal(
                "§c[System] " + toReturn.getHoverName().getString()
                        + " requires Dexterity " + getRequirement(toReturn)), true);
    }

    public static boolean isRestricted(Player p, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int req = getRequirement(stack);
        if (req <= 0) return false;
        PlayerSystemData d = p.getData(ModAttachments.PLAYER_SYSTEM_DATA);
        return d.dexterity() < req;
    }

    /**
     * Lookup order:
     *   1. Data map entry (dexterity_requirement.json)
     *   2. Hardcoded tier fallback
     *   3. 0 (no requirement)
     */
    public static int getRequirement(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        Integer fromMap = stack.getItemHolder().getData(ModDataMaps.DEXTERITY_REQUIREMENT);
        if (fromMap != null && fromMap > 0) return fromMap;

        return fallbackRequirement(stack);
    }

    private static int fallbackRequirement(ItemStack stack) {
        var item = stack.getItem();

        // ── Wooden tier ──
        if (item == Items.WOODEN_SWORD || item == Items.WOODEN_AXE
                || item == Items.WOODEN_PICKAXE || item == Items.WOODEN_SHOVEL
                || item == Items.WOODEN_HOE) return 1;

        // ── Stone tier ──
        if (item == Items.STONE_SWORD || item == Items.STONE_AXE
                || item == Items.STONE_PICKAXE || item == Items.STONE_SHOVEL
                || item == Items.STONE_HOE) return 1;

        // ── Iron tier ──
        if (item == Items.IRON_SWORD || item == Items.IRON_AXE
                || item == Items.IRON_PICKAXE || item == Items.IRON_SHOVEL
                || item == Items.IRON_HOE) return 5;

        // ── Gold tier ──
        if (item == Items.GOLDEN_SWORD || item == Items.GOLDEN_AXE
                || item == Items.GOLDEN_PICKAXE || item == Items.GOLDEN_SHOVEL
                || item == Items.GOLDEN_HOE) return 10;

        // ── Diamond tier ──
        if (item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE
                || item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SHOVEL
                || item == Items.DIAMOND_HOE) return 20;

        // ── Netherite tier ──
        if (item == Items.NETHERITE_SWORD || item == Items.NETHERITE_AXE
                || item == Items.NETHERITE_PICKAXE || item == Items.NETHERITE_SHOVEL
                || item == Items.NETHERITE_HOE) return 30;

        // ── Ranged / special ──
        if (item == Items.SHIELD) return 5;
        if (item instanceof BowItem || item instanceof CrossbowItem) return 8;
        if (item instanceof TridentItem) return 10;
        if (item instanceof MaceItem) return 20;

        // ── Armor — direct item comparison ──
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
                || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) return 1;

        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE
                || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS) return 3;

        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
                || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) return 5;

        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE
                || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) return 10;

        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) return 20;

        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) return 30;

        if (item == Items.TURTLE_HELMET) return 10;

        // ── Generic class fallbacks ──
        if (item instanceof SwordItem) return 1;
        if (item instanceof AxeItem)   return 1;
        if (item instanceof DiggerItem) return 1;
        if (item instanceof HoeItem)   return 1;
        if (item instanceof ShieldItem) return 5;

        return 0;
    }
}