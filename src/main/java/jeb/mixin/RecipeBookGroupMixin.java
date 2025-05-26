package jeb.mixin;

import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.recipe.book.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookGroup.class)
public abstract class RecipeBookGroupMixin {

    @Inject(method = "getGroups", at = @At("RETURN"), cancellable = true)
    private static void addCustomGroup(RecipeBookCategory category, CallbackInfoReturnable<List<RecipeBookGroup>> cir) {
        if (category == RecipeBookCategory.CRAFTING) {
            List<RecipeBookGroup> original = new ArrayList<>(cir.getReturnValue());

            // Вставляем CAMPFIRE вторым (после CRAFTING_SEARCH)
            original.add(1, RecipeBookGroup.CAMPFIRE);
            //int index = original.indexOf(RecipeBookGroup.CRAFTING_SEARCH);
            //original.add(index + 1, RecipeBookGroup.CAMPFIRE);

            cir.setReturnValue(original);
        }
    }
}


