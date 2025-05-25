package jeb.mixin;

import jeb.client.JEBClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.book.RecipeBook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(RecipeResultCollection.class)
public abstract class RecipeResultCollectionMixin {

    @Shadow
    @Final
    private List<Recipe<?>> recipes;

    @Shadow
    @Final
    private Set<Recipe<?>> craftableRecipes;

    @Shadow
    @Final
    private Set<Recipe<?>> fittingRecipes;

    //@Shadow
    //private List<Recipe<?>> craftableRecipes;


    @Inject(method = "computeCraftables", at = @At("HEAD"), cancellable = true)
    private void injectMyVersion(RecipeMatcher recipeFinder, int gridWidth, int gridHeight, RecipeBook recipeBook, CallbackInfo ci) {
        for (Recipe<?> recipe : this.recipes) {
            // оригинальное условие
            boolean bl = recipe.fits(gridWidth, gridHeight) && recipeBook.contains(recipe);

            if (JEBClient.customToggleEnabled) {
                // твоя дополнительная строка
                bl = recipeBook.contains(recipe);
            }

            if (bl) {
                this.fittingRecipes.add(recipe);
            } else {
                this.fittingRecipes.remove(recipe);
            }

            if (bl && recipeFinder.match(recipe, null)) {
                this.craftableRecipes.add(recipe);
            } else {
                this.craftableRecipes.remove(recipe);
            }
        }

        // Отменяем оригинальное выполнение метода
        ci.cancel();
    }


    @Inject(method = "hasFittingRecipes", at = @At("HEAD"), cancellable = true)
    private void showAllRecipes(CallbackInfoReturnable<Boolean> cir) {
        // Принудительно возвращаем true, чтобы рецепт считался отображаемым
        cir.setReturnValue(true);
    }

    @Shadow
    public abstract List<Recipe<?>> getAllRecipes();


    @Overwrite
    public List<Recipe<?>> getRecipes(boolean craftable) {
        List<Recipe<?>> allRecipes = this.getAllRecipes(); // метод публичный

        return new ArrayList<>(allRecipes); // Просто копируем все рецепты
    }
}