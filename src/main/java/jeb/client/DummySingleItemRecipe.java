package jeb.client;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;

import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;


public class DummySingleItemRecipe implements Recipe<RecipeInputInventory> {
    private final ItemStack result;

    public DummySingleItemRecipe(ItemStack result) {
        this.result = result;
    }

    @Override
    public boolean matches(RecipeInputInventory inv, World world) { return false; }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        return result.copy();
    }

    @Override
    public boolean fits(int width, int height) { return false; }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return result.copy();
    }

    @Override
    public Identifier getId() {
        return new Identifier("jeb", "dummy_" + Registries.ITEM.getKey(result.getItem()).get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPELESS; // или свой, если есть
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }
}