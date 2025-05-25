package jeb.mixin;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;

import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;


@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin  {
    @Final
    @Shadow
    private static final Logger LOGGER= LogUtils.getLogger();;

    @Inject(method = "toGroupedMap", at = @At("HEAD"), cancellable = true)
    private static void injectToGroupedMap(Iterable<Recipe<?>> recipes, CallbackInfoReturnable<Map<RecipeBookGroup, List<List<Recipe<?>>>>> cir) {
        Map<RecipeBookGroup, List<List<Recipe<?>>>> map = Maps.newHashMap();

        for(Recipe<?> recipe : recipes) {
            RecipeBookGroup recipeBookGroup = getGroupForRecipe(recipe);

            // Т.к. optionalInt всегда пустой, всё будет сюда
            ((List) map.computeIfAbsent(recipeBookGroup, (group) -> new ArrayList()))
                    .add(List.of(recipe));
        }

        cir.setReturnValue(map);
    }

    @Unique
    private static RecipeBookGroup getGroupForRecipe(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe craftingRecipe) {
            RecipeBookGroup var6;
            switch (craftingRecipe.getCategory()) {
                case BUILDING -> var6 = RecipeBookGroup.CRAFTING_BUILDING_BLOCKS;
                case EQUIPMENT -> var6 = RecipeBookGroup.CRAFTING_EQUIPMENT;
                case REDSTONE -> var6 = RecipeBookGroup.CRAFTING_REDSTONE;
                case MISC -> var6 = RecipeBookGroup.CRAFTING_MISC;
                default -> throw new IncompatibleClassChangeError();
            }

            return var6;
        } else {
            RecipeType<?> recipeType = recipe.getType();
            if (recipe instanceof AbstractCookingRecipe abstractCookingRecipe) {
                CookingRecipeCategory cookingRecipeCategory = abstractCookingRecipe.getCategory();
                if (recipeType == RecipeType.SMELTING) {
                    RecipeBookGroup var5;
                    switch (cookingRecipeCategory) {
                        case BLOCKS -> var5 = RecipeBookGroup.FURNACE_BLOCKS;
                        case FOOD -> var5 = RecipeBookGroup.FURNACE_FOOD;
                        case MISC -> var5 = RecipeBookGroup.FURNACE_MISC;
                        default -> throw new IncompatibleClassChangeError();
                    }

                    return var5;
                }

                if (recipeType == RecipeType.BLASTING) {
                    return cookingRecipeCategory == CookingRecipeCategory.BLOCKS ? RecipeBookGroup.BLAST_FURNACE_BLOCKS : RecipeBookGroup.BLAST_FURNACE_MISC;
                }

                if (recipeType == RecipeType.SMOKING) {
                    return RecipeBookGroup.SMOKER_FOOD;
                }

                if (recipeType == RecipeType.CAMPFIRE_COOKING) {
                    return RecipeBookGroup.CAMPFIRE;
                }
            }

            if (recipeType == RecipeType.STONECUTTING) {
                return RecipeBookGroup.STONECUTTER;
            } else if (recipeType == RecipeType.SMITHING) {
                return RecipeBookGroup.SMITHING;
            } else {
                Object var10002 = LogUtils.defer(() -> Registries.RECIPE_TYPE.getId(recipe.getType()));
                Objects.requireNonNull(recipe);
                LOGGER.warn("Unknown recipe category: {}/{}", var10002, LogUtils.defer(recipe::getId));
                return RecipeBookGroup.UNKNOWN;
            }
        }
    }

}




