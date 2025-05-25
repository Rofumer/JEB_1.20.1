package jeb.accessor;

import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.RecipeBookGroup;

import java.util.List;
import java.util.Map;

public interface ClientRecipeBookAccessor {
    Map<RecipeBookGroup, List<RecipeResultCollection>> getRecipes();
}
