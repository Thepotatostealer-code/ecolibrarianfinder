package com.ecolibrarianfinder.search;

import com.ecolibrarianfinder.EcoLibrarianFinderMod;
import com.ecolibrarianfinder.config.EcoFinderConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;

/**
 * State machine that drives the automated librarian rerolling loop.
 *
 * Cycle:
 *   IDLE → (user starts) → OPEN_VILLAGER → WAIT_TRADES → CHECK_TRADES
 *       → [match] FOUND / [no match] BREAK_LECTERN → PLACE_LECTERN → OPEN_VILLAGER …
 *
 * The player must have:
 *   - Lecterns in their offhand (or any hotbar slot)
 *   - An axe in their main hand (for faster breaking)
 */
public class SearchState {

    private enum Phase {
        IDLE,
        OPEN_VILLAGER,    // send interact packet to open trade screen
        WAIT_TRADES,      // wait for trade list to arrive from server
        CHECK_TRADES,     // analyse the trade list
        BREAK_LECTERN,    // break the lectern block
        PLACE_LECTERN,    // place a new lectern
        FOUND,            // match! waiting for user to stop
    }

    private Phase phase = Phase.IDLE;
    private int tickDelay = 0;
    private int attempts = 0;

    // Targets set by the user
    private BlockPos lecternPos = null;
    private VillagerEntity targetVillager = null;

    // Trades received from the mixin callback
    private volatile TradeOfferList pendingTrades = null;

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isRunning() {
        return phase != Phase.IDLE && phase != Phase.FOUND;
    }

    /** Called by the mixin when a merchant screen is opened with a trade list. */
    public void onTradesReceived(TradeOfferList trades) {
        if (phase == Phase.WAIT_TRADES) {
            pendingTrades = trades;
        }
    }

    /**
     * Select the lectern and nearest villager the player is looking at.
     * Mirrors /tradefinder select logic.
     */
    public void selectTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) return;

        // Find lectern via crosshair ray-cast
        var hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit)) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: Look at a lectern to select it."), true);
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (!world.getBlockState(pos).isOf(Blocks.LECTERN)) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: That's not a lectern."), true);
            return;
        }

        lecternPos = pos;

        // Find nearest librarian within 5 blocks
        var villagers = world.getEntitiesByClass(
                VillagerEntity.class,
                player.getBoundingBox().expand(8),
                v -> v.getVillagerData().getProfession().toString().contains("librarian")
        );

        if (villagers.isEmpty()) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: No librarian found nearby."), true);
            return;
        }

        targetVillager = villagers.stream()
                .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(Vec3d.ofCenter(lecternPos)),
                        b.squaredDistanceTo(Vec3d.ofCenter(lecternPos))))
                .orElse(null);

        player.sendMessage(Text.literal(
                "§aEcoLibrarianFinder: Lectern selected at " + lecternPos +
                " | Villager: " + (targetVillager != null ? "found" : "none")), true);
    }

    public void start(MinecraftClient client) {
        if (client.player == null) return;
        if (lecternPos == null || targetVillager == null) {
            client.player.sendMessage(
                    Text.literal("§cEcoLibrarianFinder: Select a lectern first (default key: I or /ecofind select)."), true);
            return;
        }
        if (EcoLibrarianFinderMod.config.goals.isEmpty()) {
            client.player.sendMessage(
                    Text.literal("§cEcoLibrarianFinder: No goals set. Use /ecofind add <enchant> <level> [maxPrice]."), true);
            return;
        }
        attempts = 0;
        phase = Phase.OPEN_VILLAGER;
        tickDelay = 2;
        client.player.sendMessage(Text.literal("§aEcoLibrarianFinder: Search started!"), true);
    }

    public void stop(MinecraftClient client) {
        phase = Phase.IDLE;
        pendingTrades = null;
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("§7EcoLibrarianFinder: Stopped after §e" + attempts + "§7 attempts."), false);
        }
    }

    // ── Tick loop ─────────────────────────────────────────────────────────────

    public void tick(MinecraftClient client) {
        if (phase == Phase.IDLE || phase == Phase.FOUND) return;
        if (tickDelay > 0) { tickDelay--; return; }

        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) { stop(client); return; }

        // Safety: abort if we moved too far
        if (lecternPos != null && player.squaredDistanceTo(Vec3d.ofCenter(lecternPos)) > 64) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: Too far from lectern. Stopping."), false);
            stop(client);
            return;
        }

        switch (phase) {
            case OPEN_VILLAGER -> tickOpenVillager(client, player, world);
            case WAIT_TRADES   -> tickWaitTrades(client, player);
            case CHECK_TRADES  -> tickCheckTrades(client, player);
            case BREAK_LECTERN -> tickBreakLectern(client, player, world);
            case PLACE_LECTERN -> tickPlaceLectern(client, player, world);
            default -> {}
        }
    }

    // ── Phase handlers ────────────────────────────────────────────────────────

    private void tickOpenVillager(MinecraftClient client, ClientPlayerEntity player, World world) {
        if (targetVillager == null || !targetVillager.isAlive()) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: Villager gone. Stopping."), false);
            stop(client);
            return;
        }
        // Rotate player to face villager
        Vec3d villagerEyes = targetVillager.getEyePos();
        Vec3d diff = villagerEyes.subtract(player.getEyePos());
        double yaw = Math.toDegrees(Math.atan2(-diff.x, diff.z));
        double pitch = Math.toDegrees(-Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        player.setYaw((float) yaw);
        player.setPitch((float) pitch);

        // Send interact packet
        client.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.interact(targetVillager, Hand.MAIN_HAND, player.isSneaking())
        );

        pendingTrades = null;
        phase = Phase.WAIT_TRADES;
        tickDelay = 10; // give the server time to respond
    }

    private void tickWaitTrades(MinecraftClient client, ClientPlayerEntity player) {
        if (pendingTrades != null) {
            // Close the screen immediately so the villager can relock
            client.setScreen(null);
            phase = Phase.CHECK_TRADES;
            tickDelay = 1;
        } else {
            // Timeout after ~3 seconds
            tickDelay = 60;
            // Re-attempt open next tick if still null
            phase = Phase.OPEN_VILLAGER;
        }
    }

    private void tickCheckTrades(MinecraftClient client, ClientPlayerEntity player) {
        attempts++;
        TradeOfferList trades = pendingTrades;
        pendingTrades = null;

        EcoEnchantParser.MatchResult result =
                EcoEnchantParser.checkTrades(trades, EcoLibrarianFinderMod.config.goals);

        if (result.matched()) {
            // ✅ Found it!
            phase = Phase.FOUND;
            String msg = "§a§lEcoLibrarianFinder: FOUND §r§a" + result.goal() +
                         " §7(price: " + result.price() + "🪙) after §e" + attempts + "§7 attempts!";
            player.sendMessage(Text.literal(msg), false);

            // Play ding sound
            if (EcoLibrarianFinderMod.config.notifyOnFind) {
                client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1f));
            }
        } else {
            // Keep looking — break the lectern
            phase = Phase.BREAK_LECTERN;
            tickDelay = 1;
        }
    }

    private void tickBreakLectern(MinecraftClient client, ClientPlayerEntity player, World world) {
        if (lecternPos == null) { stop(client); return; }

        // Look at the lectern
        aimAt(player, Vec3d.ofCenter(lecternPos));

        // Mine it instantly (creative) or start/complete mining
        // We use the block-break packet sequence for survival
        var net = client.getNetworkHandler();
        net.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                lecternPos, Direction.UP, 0));
        net.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                lecternPos, Direction.UP, 1));

        phase = Phase.PLACE_LECTERN;
        tickDelay = 5;
    }

    private void tickPlaceLectern(MinecraftClient client, ClientPlayerEntity player, World world) {
        // Find a lectern in inventory and place it
        int lecternSlot = findLecternSlot(player);
        if (lecternSlot == -1) {
            player.sendMessage(Text.literal("§cEcoLibrarianFinder: No lecterns left! Add more to inventory."), false);
            stop(client);
            return;
        }

        // Switch to lectern slot
        if (lecternSlot < 9) {
            client.getNetworkHandler().sendPacket(
                    new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(lecternSlot));
            player.getInventory().selectedSlot = lecternSlot;
        }

        // Place block at lectern position
        aimAt(player, Vec3d.ofCenter(lecternPos).add(0, -0.5, 0));
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(lecternPos),
                Direction.UP,
                lecternPos.down(),
                false);

        client.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND, hit, 0));

        phase = Phase.OPEN_VILLAGER;
        tickDelay = 8; // wait for block to register server-side
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void aimAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        double yaw = Math.toDegrees(Math.atan2(-diff.x, diff.z));
        double pitch = Math.toDegrees(-Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        player.setYaw((float) yaw);
        player.setPitch((float) pitch);
    }

    private static int findLecternSlot(ClientPlayerEntity player) {
        var inv = player.getInventory();
        // Check offhand first
        if (inv.offHand.get(0).isOf(Items.LECTERN)) return -2; // special offhand flag
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.LECTERN)) return i;
        }
        // Check rest of inventory
        for (int i = 9; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(Items.LECTERN)) return i; // can't hotbar-switch but noted
        }
        return -1;
    }

    public int getAttempts() { return attempts; }
    public BlockPos getLecternPos() { return lecternPos; }
    public Phase getPhase() { return phase; }
}
