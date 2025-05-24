package jeb.mixin;

import com.google.common.collect.Maps;
import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeDisplayEntry;

import net.minecraft.recipe.book.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;


@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin  {
    @Shadow
    protected static abstract RecipeBookGroup getGroupForRecipe(Recipe<?> recipe);

    @Inject(method = "toGroupedMap", at = @At("HEAD"), cancellable = true)
    private static void injectToGroupedMap(Iterable<Recipe<?>> recipes, CallbackInfoReturnable<Map<RecipeBookCategory, List<List<RecipeDisplayEntry>>>> cir) {
        Map<RecipeBookGroup, List<List<Recipe<?>>>> map = Maps.newHashMap();

        for(Recipe<?> recipe : recipes) {
            RecipeBookGroup recipeBookGroup = getGroupForRecipe(recipe);

            // Игнорируем группу, всегда делаем пустую
            OptionalInt optionalInt = OptionalInt.empty();

            // Т.к. optionalInt всегда пустой, всё будет сюда
            ((List) map.computeIfAbsent(recipeBookCategory, (group) -> new ArrayList()))
                    .add(List.of(recipeDisplayEntry));
        }

        cir.setReturnValue(map);
    }
}


