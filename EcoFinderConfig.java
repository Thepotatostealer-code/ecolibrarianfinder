package com.ecolibrarianfinder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent configuration: list of target enchantments + per-goal max price.
 * Stored at .minecraft/config/ecolibrarianfinder.json
 */
public class EcoFinderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("ecolibrarianfinder.json");

    /** Each entry is "namespace:enchant_id:level:maxPrice", e.g. "ecoenchants:abrasion:3:32" */
    public List<GoalEntry> goals = new ArrayList<>();
    public boolean notifyOnFind = true;
    public boolean stopOnFind = true;

    // ── persistence ──────────────────────────────────────────────────────────

    public static EcoFinderConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                EcoFinderConfig cfg = GSON.fromJson(r, EcoFinderConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                // fall through to default
            }
        }
        return new EcoFinderConfig();
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            // non-fatal
        }
    }

    // ── goal management ───────────────────────────────────────────────────────

    public boolean addGoal(String enchantId, int level, int maxPrice) {
        for (GoalEntry g : goals) {
            if (g.enchantId.equalsIgnoreCase(enchantId) && g.level == level) return false; // already present
        }
        goals.add(new GoalEntry(enchantId.toLowerCase(), level, maxPrice));
        save();
        return true;
    }

    public boolean removeGoal(String enchantId, int level) {
        boolean removed = goals.removeIf(g ->
                g.enchantId.equalsIgnoreCase(enchantId) && g.level == level);
        if (removed) save();
        return removed;
    }

    public void clearGoals() {
        goals.clear();
        save();
    }

    // ── inner ─────────────────────────────────────────────────────────────────

    public static class GoalEntry {
        public String enchantId;
        public int level;
        public int maxPrice;

        public GoalEntry() {}

        public GoalEntry(String enchantId, int level, int maxPrice) {
            this.enchantId = enchantId;
            this.level = level;
            this.maxPrice = maxPrice;
        }

        @Override
        public String toString() {
            return enchantId + " " + toRoman(level) + " (≤" + maxPrice + "🪙)";
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX";
            case 10 -> "X"; default -> String.valueOf(n);
        };
    }
}
