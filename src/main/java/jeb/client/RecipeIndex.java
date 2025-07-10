package jeb.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.*;

import static jeb.client.JEBClient.nonexistingResultItems;
import static jeb.client.JEBClient.LOGGER;

public class RecipeIndex {
    // Индексы по категориям
    public final Map<RecipeBookGroup, Map<String, List<RecipeResultCollection>>> byResult = new HashMap<>();
    public final Map<RecipeBookGroup, Map<String, List<RecipeResultCollection>>> byMod = new HashMap<>();
    public final Map<RecipeBookGroup, Map<String, List<RecipeResultCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookGroup, Map<String, List<RecipeResultCollection>>> byTooltipWord = new HashMap<>();
    public final Map<RecipeBookGroup, Set<RecipeResultCollection>> allCollections = new HashMap<>();
    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();

    public static boolean jebIndexReady = false;

    public static RecipeManager recipeManager;


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void getAllRecipesUnsafe(RecipeManager manager, RecipeType type, MinecraftClient minecraft) {
        for (Object obj : manager.listAllOfType(type)) {
            Recipe<?> holder = (Recipe<?>) obj;
            System.out.println("    " + holder.getOutput(minecraft.world.getRegistryManager()));
        }
    }

    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[JEB] buildRecipeIndex started at {}", new Date(startTime));
        jebIndexReady = false;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        ClientRecipeBook book = minecraft.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.byResult.clear();
        GLOBAL_RECIPE_INDEX.byMod.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.byTooltipWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();

        int totalIndexedRecipes = 0;
        Set<Identifier> uniqueRecipes = new HashSet<>();

        for (RecipeBookGroup category : RecipeBookGroup.values()) {
            List<RecipeResultCollection> collections;

            long filterstartTime = System.currentTimeMillis();
            LOGGER.info("[JEB] buildRecipeIndex filtering started at {}", new Date(filterstartTime));

            List<RecipeResultCollection> filteredCollections = new ArrayList<>();

            for (RecipeResultCollection collection : book.getResultsForGroup(category)) {
                // Оставляем только те рецепты, которые дают валидный результат
                List<Recipe<?>> filtered = collection.getAllRecipes().stream()
                        .filter(recipe -> {
                            ItemStack result = recipe.getOutput(minecraft.world.getRegistryManager());
                            return result != null && !result.isEmpty() && result.getItem() != Items.AIR;
                        })
                        .toList();

                // Если после фильтрации в коллекции что-то осталось — добавляем новую коллекцию
                if (!filtered.isEmpty()) {
                    filteredCollections.add(new RecipeResultCollection(
                            minecraft.world.getRegistryManager(), filtered
                    ));
                }
            }

            long filterendTime = System.currentTimeMillis();
            long filterduration = filterendTime - filterstartTime;
            LOGGER.info("[JEB] buildRecipeIndex filter {} done at {} ({} ms)", category, new Date(filterendTime), filterduration);

            collections =filteredCollections;

            if (collections.isEmpty()) continue;

            Set<RecipeResultCollection> categoryCollections = new LinkedHashSet<>();
            Map<String, List<RecipeResultCollection>> resultIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> modIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> ingredientIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> tooltipIndex = new HashMap<>();

            for (RecipeResultCollection collection : collections) {
                categoryCollections.add(collection);
                for (Recipe<?> recipe : collection.getAllRecipes()) {
                    ItemStack result = recipe.getOutput(minecraft.world.getRegistryManager());
                    //if (result == null || result.isEmpty() || result.getItem() == Items.AIR) continue;
                    Identifier recipeId = recipe.getId();
                    if (uniqueRecipes.add(recipeId)) {
                        totalIndexedRecipes++;
                    }
                    // --- Индекс по результату ---
                    String resultId = Registries.ITEM.getId(result.getItem()).toString().toLowerCase(Locale.ROOT);
                    resultIndex.computeIfAbsent(resultId, k -> new ArrayList<>()).add(collection);
                    // По имени (displayName)
                    String name = result.getName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
                    ///resultIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(collection);
                    for (String source : List.of(resultId, name)) {
                        for (int i = 0; i < source.length(); i++) {
                            for (int j = i + 1; j <= source.length(); j++) {
                                String substr = source.substring(i, j);
                                if (substr.isEmpty()) continue;
                                resultIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                            }
                        }
                    }


                    // --- Индекс по модам ---
                    String mod = Registries.ITEM.getId(result.getItem()).getNamespace().toLowerCase(Locale.ROOT);
                    ///modIndex.computeIfAbsent(mod, k -> new ArrayList<>()).add(collection);

                    for (int i = 0; i < mod.length(); i++) {
                        for (int j = i + 1; j <= mod.length(); j++) {
                            String substr = mod.substring(i, j);
                            if (substr.isEmpty()) continue;
                            modIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                        }
                    }


                    // --- Индекс по ингредиентам ---
                    for (var ingredient : recipe.getIngredients()) {
                        for (ItemStack stack : ingredient.getMatchingStacks()) {
                            String ingredientId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                            ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>()).add(collection);
                        }
                    }

                    // --- Индекс по тултипам ---
                    List<String> tooltipLines = new ArrayList<>();
                    try {
                        var tooltipFlag = minecraft.options.advancedItemTooltips
                                ? TooltipContext.Default.ADVANCED
                                : TooltipContext.Default.BASIC;
                        tooltipLines = result.getTooltip( minecraft.player, tooltipFlag)
                                .stream()
                                .map(c -> Formatting.strip(c.getString()).toLowerCase(Locale.ROOT).trim())
                                .toList();
                    } catch (Exception e) {}

                    for (String tooltipLine : tooltipLines) {
                        for (String word : tooltipLine.split("[\\s,;.:!\\-]+")) {
                            if (word.length() < 3) continue;
                            ///tooltipIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(collection);
                            for (int i = 0; i <= word.length() - 3; i++) {
                                for (int j = i + 3; j <= word.length(); j++) {
                                    String substr = word.substring(i, j);
                                    if (substr.isEmpty()) continue;
                                    tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                                }
                            }

                        }
                    }
                }
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.byResult.put(category, resultIndex);
            GLOBAL_RECIPE_INDEX.byMod.put(category, modIndex);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);
            GLOBAL_RECIPE_INDEX.byTooltipWord.put(category, tooltipIndex);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] buildRecipeIndex category {} done at {} ({} ms)", category, new Date(endTime), duration);

        }

        jebIndexReady = true;
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }


    public static List<RecipeResultCollection> fastSearch(
            List<RecipeBookGroup> categories,
            String query,
            String modName,
            boolean searchIngredients
    ) {
        if (!jebIndexReady) return List.of();

        query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        modName = modName == null ? "" : modName.toLowerCase(Locale.ROOT).trim();

        Set<RecipeResultCollection> result = new LinkedHashSet<>();

        for (RecipeBookGroup category : categories) {
            Map<String, List<RecipeResultCollection>> modIndex = GLOBAL_RECIPE_INDEX.byMod.getOrDefault(category, Map.of());
            Map<String, List<RecipeResultCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeResultCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<String, List<RecipeResultCollection>> resultIndex = GLOBAL_RECIPE_INDEX.byResult.getOrDefault(category, Map.of());
            Map<String, List<RecipeResultCollection>> tooltipIndex = GLOBAL_RECIPE_INDEX.byTooltipWord.getOrDefault(category, Map.of());

            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            if (!modName.isEmpty()) {
                List<RecipeResultCollection> modCollections = modIndex.getOrDefault(modName, List.of());
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                for (String word : query.split("[\\s:_\\-]+")) {
                    List<RecipeResultCollection> byWord = resultIndex.getOrDefault(word, List.of());
                    for (RecipeResultCollection rc : byWord) {
                        if (modCollections.contains(rc))
                            result.add(rc);
                    }
                }
                continue;
            }

            if (searchIngredients && !query.isEmpty()) {
                List<RecipeResultCollection> byIng = ingredientIndex.getOrDefault(query, List.of());
                result.addAll(byIng);
                continue;
            }

            if (!query.isEmpty() && !searchIngredients) {
                List<RecipeResultCollection> byResult = resultIndex.getOrDefault(query, List.of());
                result.addAll(byResult);
                List<RecipeResultCollection> byTooltip = tooltipIndex.getOrDefault(query, List.of());
                result.addAll(byTooltip);
            }
        }

        return new ArrayList<>(result);
    }


    public static List<RecipeResultCollection> fastSearch(
            Object categoryOrType, String query, String modName, boolean searchIngredients
    ) {
        List<RecipeBookGroup> categories;
        if (categoryOrType instanceof List) {
            categories = (List<RecipeBookGroup>) categoryOrType;
        } else if (categoryOrType instanceof RecipeBookGroup) {
            categories = List.of((RecipeBookGroup) categoryOrType);
        } else if (categoryOrType instanceof RecipeBookCategory) {
            // Если хочешь — сделай маппинг из RecipeBookType к RecipeBookGroup
            categories = List.of(); // (или свой маппинг)
        } else {
            categories = List.of();
        }
        return fastSearch(categories, query, modName, searchIngredients);
    }

    public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> list = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();

        filter = filter.trim();
        String _modName = null;
        String _query = "";

        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            _modName = parts[0].toLowerCase(java.util.Locale.ROOT);
            if (parts.length > 1) {
                _query = parts[1].toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            _query = filter.toLowerCase(java.util.Locale.ROOT);
        }

        final String modName = _modName;
        final String query = _query;

        ITEM_INDEX.stream()
                .filter(idx ->
                        (modName == null || idx.mod.contains(modName)) &&
                                (query.isEmpty() ||
                                        idx.name.contains(query) ||
                                        idx.id.contains(query) ||
                                        idx.key.contains(query) ||
                                        idx.tooltip.stream().anyMatch(line -> line.contains(query))
                                )
                )
                .forEach(idx -> {
                    var dummy = new DummySingleItemRecipe(idx.item.getDefaultStack());
                    RecipeResultCollection collection = new RecipeResultCollection(MinecraftClient.getInstance().world.getRegistryManager(),List.of(dummy));
                    list.add(collection);
                });

        return list;
    }


    public static class IndexedItem {
        public final Item item;
        public final String id;
        public final String name;
        public final String mod;
        public final String key;
        public final List<String> tooltip;

        public IndexedItem(Item item, String id, String name, String mod, String key, List<String> tooltip) {
            this.item = item;
            this.id = id;
            this.name = name;
            this.mod = mod;
            this.key = key;
            this.tooltip = tooltip;
        }
    }


    public static List<IndexedItem> ITEM_INDEX = new ArrayList<>();

    public static void fillItemIndex(MinecraftClient client) {
        ITEM_INDEX.clear();
        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;
            var holder = Registries.ITEM.getId(item);
            String id = holder.toString().toLowerCase(java.util.Locale.ROOT);
            String name = item.getDefaultStack().getName().getString().toLowerCase(java.util.Locale.ROOT);
            String mod = holder.getNamespace().toLowerCase(java.util.Locale.ROOT);
            String key = "";
            List<String> tooltipLines = List.of();

            var nameComponent = item.getDefaultStack().getName();
            if (nameComponent.getContent() instanceof net.minecraft.text.TranslatableTextContent t) {
                key = t.getKey().toLowerCase(java.util.Locale.ROOT);
            }

            // Можно закэшировать тултипы заранее
            if (client.world != null) {
                try {
                    var tooltipFlag = client.options.advancedItemTooltips
                            ? TooltipContext.Default.ADVANCED
                            : TooltipContext.Default.BASIC;
                    tooltipLines = item.getDefaultStack().getTooltip(
                                    client.player, tooltipFlag)
                            .stream()
                            .map(c -> Formatting.strip(c.getString()).toLowerCase(java.util.Locale.ROOT).trim())
                            .toList();
                } catch (Exception e) {}
            }

            ITEM_INDEX.add(new IndexedItem(item, id, name, mod, key, tooltipLines));
        }
    }


}
