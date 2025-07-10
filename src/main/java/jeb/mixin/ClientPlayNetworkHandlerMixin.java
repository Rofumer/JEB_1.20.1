package jeb.mixin;

import jeb.client.RecipeIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static jeb.client.JEBClient.existingResultItems;
import static jeb.client.JEBClient.nonexistingResultItems;
import static jeb.client.RecipeIndex.buildRecipeIndex;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onSynchronizeRecipes", at = @At("TAIL"))
    private void onSyncRecipes(SynchronizeRecipesS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RecipeBook book = mc.player.getRecipeBook();

        List<RecipeResultCollection> originalList;

        originalList = new ArrayList<>();

        for (RecipeBookGroup group : RecipeBookGroup.CRAFTING) {
            originalList.addAll(mc.player.getRecipeBook().getResultsForGroup(group));
        }


        for (RecipeResultCollection collection : originalList) {

            for (Recipe<?> entry : collection.getAllRecipes()) {
                ItemStack stack = entry.getOutput(mc.world.getRegistryManager());
                if (!stack.isEmpty()) {
                    existingResultItems.add(stack.getItem());
                }
            }

        }
        // Здесь он уже заполнен — безопасно использовать

        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;
            nonexistingResultItems.add(item);
        }

        RecipeIndex.fillItemIndex(MinecraftClient.getInstance());
        buildRecipeIndex();


    }

}

