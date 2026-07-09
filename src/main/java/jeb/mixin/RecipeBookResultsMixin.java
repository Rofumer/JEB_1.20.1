package jeb.mixin;

import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RecipeBookDataC2SPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(RecipeBookResults.class)
public class RecipeBookResultsMixin {


    @Unique
    private RecipeBookWidget jeb$widget;

    @Shadow
    private RecipeAlternativesWidget alternatesWidget;

    @Shadow
    private Recipe<?> lastClickedRecipe;



    @Shadow
    @Nullable
    private RecipeResultCollection resultCollection;

    @Shadow
    private AnimatedResultButton hoveredResultButton;

    @Shadow private MinecraftClient client;

    @Inject(method = "setGui", at = @At("HEAD"))
    private void captureWidget(RecipeBookWidget widget, CallbackInfo ci) {
        this.jeb$widget = widget;
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/AnimatedResultButton;mouseClicked(DDI)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onRightClickInject(
            double mouseX, double mouseY, int button, int areaLeft, int areaTop, int areaWidth, int areaHeight, CallbackInfoReturnable<Boolean> cir
    ) {

        AnimatedResultButton hovered = hoveredResultButton;

        if (hovered != null) {

            //if (hovered.mouseClicked(mouseX, mouseY, button)) {


                if (button == 2) {
                    ItemStack stack = hovered.currentRecipe().getOutput(this.client.world.getRegistryManager());
                    String itemName = stack.getItem().toString(); // Локализованное имя (например, "Булыжник")
                    String searchText = "~" + itemName.toLowerCase(Locale.ROOT);

                    ((RecipeBookWidgetBridge) jeb$widget).jeb$pushHistory(
                            ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().getText(),
                            ((RecipeBookWidgetAccessor) jeb$widget).getSelectedTab()
                    );

// Устанавливаем в поиск
                    ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().setText(searchText);
                    ((RecipeBookWidgetAccessor) jeb$widget).setSelectedTab((RecipeGroupButtonWidget) ((RecipeBookWidgetAccessor) jeb$widget).getTabButtons().get(0));
                    ((RecipeBookWidgetAccessor) jeb$widget).invokeReset();

                    cir.setReturnValue(true);
                    cir.cancel();
                }



                if (button == 1) {
                    ItemStack stack = hovered.currentRecipe().getOutput(this.client.world.getRegistryManager());
                    ///String itemName = stack.getItem().getName().getString(); // Локализованное имя (например, "Булыжник")
                    String itemName = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

                    ((RecipeBookWidgetBridge) jeb$widget).jeb$pushHistory(
                            ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().getText(),
                            ((RecipeBookWidgetAccessor) jeb$widget).getSelectedTab()
                    );

// Устанавливаем в поиск
                    ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().setText(searchText);
                    ((RecipeBookWidgetAccessor) jeb$widget).setSelectedTab((RecipeGroupButtonWidget) ((RecipeBookWidgetAccessor) jeb$widget).getTabButtons().get(0));
                    ((RecipeBookWidgetAccessor) jeb$widget).invokeReset();

                    cir.setReturnValue(true);
                    cir.cancel();
                }


                if (button == 0) {


                    //System.out.println(animatedResultButton.getCurrentId().toString());

                    MinecraftClient client = MinecraftClient.getInstance();
                    ClientRecipeBook recipeBook = client.player.getRecipeBook();

                    RecipeResultCollection entry = hovered.getResultCollection();

                    if (entry != null) {


                        if (!hovered.currentRecipe().getIngredients().isEmpty()) {

                            if (!canDisplay(hovered.currentRecipe())) {
                                alternatesWidget.showAlternativesForResult(this.client, entry, hovered.getX(), hovered.getY(), areaLeft + areaWidth / 2, areaTop + 13 + areaHeight / 2, (float) hovered.getWidth());
                            } else {

                                this.lastClickedRecipe = hovered.currentRecipe();
                                this.resultCollection = hovered.getResultCollection();
                                //recipeBook.shouldDisplay(animatedResultButton.currentRecipe());
                                recipeBook.onRecipeDisplayed(hovered.currentRecipe());
                                ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                                networkHandler.sendPacket(new RecipeBookDataC2SPacket(hovered.currentRecipe()));
                        /*this.lastClickedRecipe = animatedResultButton.getCurrentId();
                        this.resultCollection = animatedResultButton.getResultCollection();
                        recipeBook.unmarkHighlighted(animatedResultButton.getCurrentId());
                        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        networkHandler.sendPacket(new RecipeBookDataC2SPacket(animatedResultButton.getCurrentId()));*/
                            }
                        }

                    }

                    cir.setReturnValue(true);
                    cir.cancel();
                }
            //}
        }

    }

    @Final
    @Shadow
    private List<RecipeDisplayListener> recipeDisplayListeners;


    @Unique
    private boolean canDisplay(Recipe<?> display) {

        AbstractRecipeScreenHandler<?> handler = null;

        for(RecipeDisplayListener recipeDisplayListener : this.recipeDisplayListeners) {
            handler = ((RecipeBookWidgetAccessor) recipeDisplayListener).getCraftingScreenHandler();
        }

     return display.fits(handler.getCraftingWidth(),handler.getCraftingHeight());

    }

}
