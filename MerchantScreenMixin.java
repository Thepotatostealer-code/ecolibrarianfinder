package com.ecolibrarianfinder.mixin;

import com.ecolibrarianfinder.EcoLibrarianFinderMod;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the MerchantScreen constructor to capture trade offers as they arrive
 * from the server, feeding them into our SearchState without requiring the GUI to
 * stay open (we close it immediately after reading).
 */
@Mixin(MerchantScreen.class)
public class MerchantScreenMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onMerchantScreenOpen(
            net.minecraft.screen.MerchantScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory playerInventory,
            net.minecraft.text.Text title,
            CallbackInfo ci) {

        TradeOfferList offers = handler.getRecipes();
        if (offers != null && !offers.isEmpty()) {
            EcoLibrarianFinderMod.searchState.onTradesReceived(offers);
        }
    }
}
