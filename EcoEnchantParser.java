package com.ecolibrarianfinder.search;

import com.ecolibrarianfinder.config.EcoFinderConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.List;

/**
 * Parses EcoEnchants custom enchantments from villager trade offer ItemStacks.
 *
 * EcoEnchants (Paper plugin) stores its enchantments on enchanted books via the
 * vanilla "stored_enchantments" component AND/OR a custom NBT compound under the
 * key "StoredEnchantments" with namespaced IDs like "ecoenchants:abrasion".
 *
 * In 1.21+ with the new data components system the enchantments live under:
 *   DataComponentTypes.STORED_ENCHANTMENTS  (vanilla)
 *   DataComponentTypes.CUSTOM_DATA          (custom NBT put there by EcoEnchants)
 *
 * The fallback approach: serialise the entire ItemStack NBT to a string and
 * scan for "ecoenchants:" substrings — same idea as LibrGetter's fallback mode
 * but we also do structured parsing first so we can match level + price.
 */
public class EcoEnchantParser {

    // EcoEnchants namespace prefix used in enchantment IDs
    public static final String ECO_NAMESPACE = "ecoenchants:";

    /**
     * Returns true if any trade in the list satisfies any goal in the config.
     * Populates {@code matchedGoal} with the first matching goal for display.
     */
    public static MatchResult checkTrades(TradeOfferList offers, List<EcoFinderConfig.GoalEntry> goals) {
        for (TradeOffer offer : offers) {
            ItemStack result = offer.getSellItem();
            if (!result.isOf(Items.ENCHANTED_BOOK)) continue;

            int price = offer.getAdjustedFirstBuyItem().getCount(); // emerald cost

            for (EcoFinderConfig.GoalEntry goal : goals) {
                if (price > goal.maxPrice) continue;

                if (itemMatchesGoal(result, goal)) {
                    return new MatchResult(true, goal, price);
                }
            }
        }
        return new MatchResult(false, null, 0);
    }

    // ── Structured parsing ────────────────────────────────────────────────────

    private static boolean itemMatchesGoal(ItemStack book, EcoFinderConfig.GoalEntry goal) {
        // 1. Try vanilla stored_enchantments component (EcoEnchants registers its
        //    enchantments into the vanilla registry on Paper, so they *may* appear here
        //    on servers with the correct registry sync).
        var storedEnchants = book.getEnchantments();
        for (var entry : storedEnchants.getEnchantments()) {
            String id = entry.value().getIdAsString(); // e.g. "ecoenchants:abrasion"
            if (id == null) continue;
            if (idMatchesGoal(id, storedEnchants.getLevel(entry), goal)) return true;
        }

        // 2. Try custom NBT data component (EcoEnchants sometimes stores a raw NBT
        //    compound via CustomData to survive cross-server transfer).
        NbtComponent customData = book.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            if (matchNbtCompound(nbt, goal)) return true;
        }

        // 3. Fallback: stringify the entire item NBT and search for the enchant id
        //    (catches any storage format we haven't anticipated).
        return fallbackStringSearch(book, goal);
    }

    private static boolean matchNbtCompound(NbtCompound nbt, EcoFinderConfig.GoalEntry goal) {
        // EcoEnchants may store under "StoredEnchantments" or "Enchantments" key
        for (String key : new String[]{"StoredEnchantments", "Enchantments", "stored_enchantments"}) {
            if (!nbt.contains(key, NbtElement.LIST_TYPE)) continue;
            NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound enchant = list.getCompound(i);
                String id = enchant.contains("id") ? enchant.getString("id") : "";
                int lvl = enchant.contains("lvl") ? enchant.getShort("lvl") : 1;
                if (idMatchesGoal(id, lvl, goal)) return true;
            }
        }
        return false;
    }

    private static boolean idMatchesGoal(String id, int level, EcoFinderConfig.GoalEntry goal) {
        if (id == null || id.isEmpty()) return false;
        String normalised = id.toLowerCase();
        String goalId = goal.enchantId.toLowerCase();

        // Accept either "ecoenchants:abrasion" or just "abrasion"
        boolean idMatch = normalised.equals(goalId)
                || normalised.equals(ECO_NAMESPACE + goalId)
                || (normalised.startsWith(ECO_NAMESPACE)
                    && normalised.substring(ECO_NAMESPACE.length()).equals(goalId));

        return idMatch && level >= goal.level;
    }

    private static boolean fallbackStringSearch(ItemStack book, EcoFinderConfig.GoalEntry goal) {
        // Serialise via SNBT (best-effort on 1.21 components)
        try {
            NbtComponent customData = book.get(DataComponentTypes.CUSTOM_DATA);
            String raw = customData != null ? customData.copyNbt().toString() : "";

            String goalId = goal.enchantId.toLowerCase();
            // Look for the id anywhere in the raw string
            return raw.toLowerCase().contains(ECO_NAMESPACE + goalId)
                    || raw.toLowerCase().contains("\"" + goalId + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public record MatchResult(boolean matched, EcoFinderConfig.GoalEntry goal, int price) {}
}
