package jeb.client;

import net.minecraft.client.recipebook.RecipeBookGroup;

public record SearchHistoryEntry(String query, RecipeBookGroup category) {}
