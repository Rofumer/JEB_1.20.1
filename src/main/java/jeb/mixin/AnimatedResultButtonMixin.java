package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


@Mixin(AnimatedResultButton.class)
public abstract class AnimatedResultButtonMixin implements AnimatedResultButtonExtension {


    @Unique
    private long jeb$flashUntil = 0L; // время до которого будет подсветка

    @Unique
    public void jeb$flash() {
        this.jeb$flashUntil = System.currentTimeMillis() + 300; // подсветка 300 мс
    }

    @Unique
    private boolean jeb$isFlashing() {
        return System.currentTimeMillis() < this.jeb$flashUntil;
    }

    @Inject(method = "renderButton", at = @At("TAIL"))
    private void jeb$renderFlash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$isFlashing()) {
            AnimatedResultButton self = (AnimatedResultButton) (Object) this;
            context.drawBorder(self.getX(), self.getY(), self.getWidth(), self.getHeight(), 0xFFFFFF00); // Жёлтая рамка
        }
    }

    /*@Redirect(
            method = "showResultCollection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;getResults(Z)Ljava/util/List;"
            )
    )
    private List<Recipe<?>> redirectFilter(RecipeResultCollection instance, boolean craftableOnly) {
        // Возвращаем все рецепты, без фильтрации
        return instance.getAllRecipes();
    }*/

    @Shadow private RecipeResultCollection resultCollection;

    /**
     * Replaces the return value of getResults() with all recipes from the collection.
     */
    @Inject(method = "getResults", at = @At("TAIL"), cancellable = true)
    private void injectGetResults(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        cir.setReturnValue(this.resultCollection.getAllRecipes());
    }

    @Unique
    private static final Text MORE_RECIPES_TEXT = Text.translatable("items.craftsfromitem");

    /*@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void injectAlwaysAddTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        List<Text> list = cir.getReturnValue();
        try{
        list.add(MORE_RECIPES_TEXT); // всегда добавляем
        } catch (Exception e) {
            e.printStackTrace();
            // Можно также записать лог или безопасно проигнорировать ошибку
        }
        cir.setReturnValue(list);    // возвращаем изменённый список
    }*/

    @Shadow
    private int currentResultIndex;



    @Shadow
    protected abstract List<Recipe<?>> getResults();

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Text>> cir) {
        try {
            ItemStack itemStack = ((Recipe)this.getResults().get(this.currentResultIndex)).getOutput(this.resultCollection.getRegistryManager());
            List<Text> list = new ArrayList<>(Screen.getTooltipFromItem(MinecraftClient.getInstance(), itemStack));

            list.add(MORE_RECIPES_TEXT);

            cir.setReturnValue(list);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(List.of(Text.literal("§c[Error rendering tooltip]")));
        }
    }
}


