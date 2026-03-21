package com.ecolibrarianfinder;

import com.ecolibrarianfinder.command.EcoFinderCommand;
import com.ecolibrarianfinder.config.EcoFinderConfig;
import com.ecolibrarianfinder.search.SearchState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EcoLibrarianFinderMod implements ClientModInitializer {

    public static final String MOD_ID = "ecolibrarianfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding startStopKey;
    public static KeyBinding selectLecternKey;

    public static EcoFinderConfig config;
    public static SearchState searchState;

    @Override
    public void onInitializeClient() {
        config = EcoFinderConfig.load();
        searchState = new SearchState();

        // Register keybindings
        startStopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ecolibrarianfinder.start_stop",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.ecolibrarianfinder"
        ));

        selectLecternKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ecolibrarianfinder.select",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.ecolibrarianfinder"
        ));

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            EcoFinderCommand.register(dispatcher);
        });

        // Tick loop – drives the automated search
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeybinds(client);
            searchState.tick(client);
        });

        LOGGER.info("EcoLibrarianFinder loaded!");
    }

    private void handleKeybinds(MinecraftClient client) {
        if (client.player == null) return;

        while (startStopKey.wasPressed()) {
            if (searchState.isRunning()) {
                searchState.stop(client);
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§cEcoLibrarianFinder: Search stopped."), true);
            } else {
                searchState.start(client);
            }
        }

        while (selectLecternKey.wasPressed()) {
            searchState.selectTarget(client);
        }
    }
}
