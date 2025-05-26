package jeb.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeGroupButtonWidget.class)
public abstract class RecipeGroupButtonWidgetMixin {

    @Inject(method = "hasKnownRecipes", at = @At("HEAD"), cancellable = true)
    private void forceVisibleForCampfire(ClientRecipeBook recipeBook, CallbackInfoReturnable<Boolean> cir) {
        RecipeGroupButtonWidget self = (RecipeGroupButtonWidget)(Object)this;

        if (self.getCategory() == RecipeBookGroup.CAMPFIRE) {
            self.visible = true; // доступ к полю напрямую, так как protected
            cir.setReturnValue(true); // отменить оригинальный метод и вернуть true
        }
    }

    @Inject(method = "renderIcons", at = @At("HEAD"), cancellable = true)
    private void overrideIcons(DrawContext context, ItemRenderer itemRenderer, CallbackInfo ci) {
        RecipeGroupButtonWidget self = (RecipeGroupButtonWidget)(Object)this;
        RecipeBookGroup category = self.getCategory();

        // ⚠️ Заменим только если это та самая вкладка
        if (category == RecipeBookGroup.CAMPFIRE) {
            int i = self.isToggled() ? -2 : 0;
            int x = self.getX();
            int y = self.getY();
            context.drawItemWithoutEntity(new ItemStack(Items.WRITABLE_BOOK), x + 9 + i, y + 5);
            ci.cancel();  // предотвратить выполнение оригинального метода
        }
    }
}
