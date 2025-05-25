package jeb.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeAlternativesWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
//target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;",
@Mixin(RecipeAlternativesWidget.class)
public class RecipeAlternativesWidgetMixin {
    @Redirect(
            method = "showAlternativesForResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;getRecipes(Z)Ljava/util/List;",
                    ordinal = 1
            )
    )
    private List<Recipe<?>> redirectList2Filter(RecipeResultCollection instance, boolean craftable) {
        return instance.getAllRecipes();
    }

}
