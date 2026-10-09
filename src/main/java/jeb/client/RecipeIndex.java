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
    // Индексы по категориям.
    // Раньше byResult/byMod/byTooltipWord хранили все подстроки id/имени/мода/слов тултипа
    // (O(n²) ключей на строку) — на больших сборках это сотни МБ (issue #10).
    // Теперь на каждую коллекцию хранится одна компактная запись, а подстроки ищутся
    // через contains() при поиске — это линейный проход, единицы мс даже на тысячах коллекций.
    public final Map<RecipeBookGroup, Map<RecipeResultCollection, SearchEntry>> searchEntries = new HashMap<>();
    public final Map<RecipeBookGroup, Map<String, Set<RecipeResultCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookGroup, Set<RecipeResultCollection>> allCollections = new HashMap<>();
    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();

    public static boolean jebIndexReady = false;

    // Разделитель значений внутри строк SearchEntry. Запрос обрезан trim() и не содержит '\n',
    // поэтому contains() по склеенной строке совпадает только внутри одного значения.
    private static final String SEPARATOR = "\n";
    private static final int MIN_TOOLTIP_QUERY_LENGTH = 3;

    /**
     * Поисковые строки одной коллекции. Коллекция — ванильная группа рецептов,
     * и рецепты в ней могут давать разные результаты, поэтому id/имена/моды тоже склеиваются.
     */
    public static final class SearchEntry {
        final String ids;
        final String names;
        final String mods;
        // Уникальные слова тултипа (длиной >= 3)
        final String tooltipWords;

        SearchEntry(Builder b) {
            this.ids = String.join(SEPARATOR, b.ids);
            this.names = String.join(SEPARATOR, b.names);
            this.mods = String.join(SEPARATOR, b.mods);
            this.tooltipWords = String.join(SEPARATOR, b.tooltipWords);
        }

        boolean matchesMod(String modName) {
            return mods.contains(modName);
        }

        boolean matchesResult(String query) {
            return !query.contains(SEPARATOR) && (ids.contains(query) || names.contains(query));
        }

        boolean matchesTooltip(String query) {
            return query.length() >= MIN_TOOLTIP_QUERY_LENGTH
                    && !query.contains(SEPARATOR)
                    && tooltipWords.contains(query);
        }

        static final class Builder {
            final Set<String> ids = new LinkedHashSet<>();
            final Set<String> names = new LinkedHashSet<>();
            final Set<String> mods = new LinkedHashSet<>();
            final Set<String> tooltipWords = new LinkedHashSet<>();
        }
    }

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

        GLOBAL_RECIPE_INDEX.searchEntries.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
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
            Map<String, Set<RecipeResultCollection>> ingredientIndex = new HashMap<>();
            Map<RecipeResultCollection, SearchEntry> entries = new HashMap<>();

            for (RecipeResultCollection collection : collections) {
                categoryCollections.add(collection);
                SearchEntry.Builder builder = null;
                for (Recipe<?> recipe : collection.getAllRecipes()) {
                    ItemStack result = recipe.getOutput(minecraft.world.getRegistryManager());
                    //if (result == null || result.isEmpty() || result.getItem() == Items.AIR) continue;
                    Identifier recipeId = recipe.getId();
                    if (uniqueRecipes.add(recipeId)) {
                        totalIndexedRecipes++;
                    }
                    if (builder == null) builder = new SearchEntry.Builder();

                    // --- Поисковые строки результата: id, имя (displayName), мод ---
                    Identifier key = Registries.ITEM.getId(result.getItem());
                    builder.ids.add(key.toString().toLowerCase(Locale.ROOT));
                    builder.names.add(result.getName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", ""));
                    builder.mods.add(key.getNamespace().toLowerCase(Locale.ROOT));

                    // --- Индекс по ингредиентам (точный id) ---
                    for (var ingredient : recipe.getIngredients()) {
                        for (ItemStack stack : ingredient.getMatchingStacks()) {
                            String ingredientId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                            ingredientIndex.computeIfAbsent(ingredientId, k -> new LinkedHashSet<>()).add(collection);
                        }
                    }

                    // --- Слова тултипа ---
                    builder.tooltipWords.addAll(tooltipWords(minecraft, result));
                }
                if (builder != null) entries.put(collection, new SearchEntry(builder));
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.searchEntries.put(category, entries);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] buildRecipeIndex category {} done at {} ({} ms)", category, new Date(endTime), duration);

        }

        jebIndexReady = true;
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }

    private static Set<String> tooltipWords(MinecraftClient minecraft, ItemStack result) {
        Set<String> words = new LinkedHashSet<>();
        try {
            var tooltipFlag = minecraft.options.advancedItemTooltips
                    ? TooltipContext.Default.ADVANCED
                    : TooltipContext.Default.BASIC;
            for (var line : result.getTooltip(minecraft.player, tooltipFlag)) {
                String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                for (String word : clean.split("[\\s,;.:!\\-]+")) {
                    if (word.length() >= MIN_TOOLTIP_QUERY_LENGTH) words.add(word);
                }
            }
        } catch (Exception e) {}
        return words;
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
            Map<String, Set<RecipeResultCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeResultCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<RecipeResultCollection, SearchEntry> entries = GLOBAL_RECIPE_INDEX.searchEntries.getOrDefault(category, Map.of());

            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            // Поиск по модулю (namespace)
            if (!modName.isEmpty()) {
                List<RecipeResultCollection> modCollections = new ArrayList<>();
                List<SearchEntry> modEntries = new ArrayList<>();
                for (RecipeResultCollection rc : all) {
                    SearchEntry entry = entries.get(rc);
                    if (entry != null && entry.matchesMod(modName)) {
                        modCollections.add(rc);
                        modEntries.add(entry);
                    }
                }
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                // Ищем среди коллекций по моду по словам
                for (String word : query.split("[\\s:_\\-]+")) {
                    if (word.isEmpty()) continue;
                    for (int i = 0; i < modCollections.size(); i++) {
                        if (modEntries.get(i).matchesResult(word))
                            result.add(modCollections.get(i));
                    }
                }
                continue;
            }

            // Поиск по ингредиенту
            if (searchIngredients) {
                result.addAll(ingredientIndex.getOrDefault(query, Set.of()));
                continue;
            }

            // Поиск по результату (id или имя), затем по тултипам
            List<RecipeResultCollection> byTooltip = new ArrayList<>();
            for (RecipeResultCollection rc : all) {
                SearchEntry entry = entries.get(rc);
                if (entry == null) continue;
                if (entry.matchesResult(query)) {
                    result.add(rc);
                } else if (entry.matchesTooltip(query)) {
                    byTooltip.add(rc);
                }
            }
            result.addAll(byTooltip);
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
