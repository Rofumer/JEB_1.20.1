package jeb.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    // JEB показывает все рецепты, включая 3x3 в инвентаре 2x2. Не отправляем такие рецепты на сервер,
    // иначе он может разложить в сетку только часть ингредиентов.
    @Inject(method = "clickRecipe", at = @At("HEAD"), cancellable = true)
    private void jeb$skipTooBigRecipe(int syncId, Recipe<?> recipe, boolean craftAll, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler.syncId == syncId && handler instanceof AbstractRecipeScreenHandler<?> recipeHandler
                && !recipe.fits(recipeHandler.getCraftingWidth(), recipeHandler.getCraftingHeight())) {
            ci.cancel();
        }
    }
}
