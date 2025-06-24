package jeb.client;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.client.util.InputUtil;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.item.TooltipContext;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static net.minecraft.client.resource.language.I18n.translate;

public class JEBClient implements ClientModInitializer {

    public static boolean customToggleEnabled = true;

    private static final Path CONFIG_PATH = Paths.get(
            MinecraftClient.getInstance().runDirectory.getAbsolutePath(),
            "config", "JEB.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();



    public static KeyBinding keyBinding;

    public static Set<Item> existingResultItems = new HashSet<>();
    public static Set<Item> nonexistingResultItems = new HashSet<>();

    public static String string = "-";
    public static List<RecipeResultCollection> filtered = new ArrayList<>();
    public static List<RecipeResultCollection> emptysearch = new ArrayList<>();

    public static boolean recipesLoaded = false;

    //public static List<RecipeResultCollection> PREGENERATED_RECIPES = generateCustomRecipeList("");


    public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> list = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();

        String query = "";
        String modName = null;

        filter = filter.trim();
        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            modName = parts[0].toLowerCase(Locale.ROOT);
            if (parts.length > 1) {
                query = parts[1].toLowerCase(Locale.ROOT);
            }
        } else {
            query = filter.toLowerCase(Locale.ROOT);
        }

        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;

            Identifier id = Registries.ITEM.getId(item);
            String idStr = id.toString().toLowerCase(Locale.ROOT);
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String key = translate(item.getTranslationKey()).toLowerCase(Locale.ROOT);

            // Фильтр по мод-нейму
            if (modName != null && !id.getNamespace().toLowerCase(Locale.ROOT).contains(modName)) {
                continue;
            }

            // Базовый поиск
            boolean matchesBasic = name.contains(query) || idStr.contains(query) || key.contains(query);
            boolean matchesTooltip = false;

            // Тултипы проверяем только при необходимости
            if (!matchesBasic && query.length() >= 3 && client.world != null) {
                try {
                    TooltipContext context = client.options.advancedItemTooltips
                            ? TooltipContext.Default.ADVANCED
                            : TooltipContext.Default.BASIC;
                    List<Text> tooltip = item.getDefaultStack().getTooltip(client.player, context);

                    for (Text line : tooltip) {
                        String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            matchesTooltip = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!matchesBasic && !matchesTooltip) continue;

            // Добавляем в список
            DummySingleItemRecipe dummy = new DummySingleItemRecipe(item.getDefaultStack());
            list.add(new RecipeResultCollection(client.world.getRegistryManager(), List.of(dummy)));
        }

        return list;
    }



    /*public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> list = new ArrayList<>();

        MinecraftClient client = MinecraftClient.getInstance();

        String query;

        String modName = null;
        if (filter.startsWith("@")) {
            // Извлекаем имя мода, если оно присутствует в начале строки
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();  // Извлекаем имя мода
                query = filter.substring(endIndex + 1).toLowerCase();  // Остальная часть это обычный запрос
            } else {
                modName = filter.substring(1).trim();  // Имя мода без строки запроса
                query = "";  // Если нет строки запроса, то фильтровать только по имени мода
            }
        }
        else
        {
            query = filter.toLowerCase();
        }

        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;


            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String id_item = item.toString().toLowerCase(Locale.ROOT);
            String key = translate(item.getTranslationKey()).toLowerCase(Locale.ROOT);

            if (modName != null && !modName.isEmpty() && !Registries.ITEM.getId(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }


            boolean tooltip_bool = false;


            if (client.world != null)
            {
            // Поиск по тултипам
            RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();

                try {
                    List<Text> tooltip = item.getDefaultStack().getTooltip(client.player, client.options.advancedItemTooltips ? (TooltipContext)TooltipContext.Default.ADVANCED : (TooltipContext)TooltipContext.Default.BASIC);
                    for (Text line : tooltip) {
                        String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            tooltip_bool = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Можно также записать лог или безопасно проигнорировать ошибку
                }

            }

            if (!(name.contains(query) || id_item.contains(query) || key.contains(query) || tooltip_bool)) continue;
            ///////if (!(name.contains(query) || id_item.contains(query) || key.contains(query))) continue;


            ///////if (!translate(item.getTranslationKey()).toLowerCase().contains(filter.toLowerCase())) continue;


            Identifier id = Registries.ITEM.getId(item);


            Recipe<?> recipe = new CraftingRecipe() {
                @Override
                public CraftingRecipeCategory getCategory() {
                    return null;
                }

                @Override
                public boolean matches(RecipeInputInventory inventory, World world) {
                    return false;
                }

                @Override
                public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
                    return null;
                }

                @Override
                public boolean fits(int width, int height) {
                    return false;
                }

                @Override
                public ItemStack getOutput(DynamicRegistryManager registryManager) {
                    return new ItemStack(item);
                }

                @Override
                public Identifier getId() {
                    return null;
                }

                @Override
                public RecipeSerializer<?> getSerializer() {
                    return null;
                }
            };

            list.add(new RecipeResultCollection(client.world.getRegistryManager(),List.of(recipe)));
        }

        return list;
    }*/


    private boolean waitingForR = false; // Добавляем флаг для проверки, нужно ли устанавливать экран

    //int getCraftingStationId() {
    //    Identifier id = Registries.ITEM.getId(Items.CRAFTING_TABLE);
    //    return NetworkRecipeIdEncoder.encode(id);
    //}

    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("customToggleEnabled")) {
                        customToggleEnabled = json.get("customToggleEnabled").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("customToggleEnabled", customToggleEnabled);

            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInitializeClient() {

        loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(JEBClient::saveConfig));

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jeb.add_remove_favorite_recipes", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_A, // The keycode of the key
                "JEB (Just Enough Book)" // The translation key of the keybinding's category.
        ));


        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            recipesLoaded = false;
            //existingResultItems = new HashSet<>();
            //nonexistingResultItems = new HashSet<>();
            existingResultItems.clear();
            nonexistingResultItems.clear();
            string = "-";
            emptysearch.clear();
        });


        /*ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            //MyCache.cacheItemsOnce();


            int knownRecipeCount = 0;

            ClientRecipeBook recipeBook = null;

            int craftingStationId = 0;

            //if (client.player != null) {
                recipeBook = client.player.getRecipeBook();
                List<RecipeResultCollection> recipes = recipeBook.getOrderedResults();


                // Проходим по всем коллекциям рецептов
                for (RecipeResultCollection collection : recipes) {
                    List<RecipeDisplayEntry> entries = collection.getAllRecipes();

                    // Преобразуем в строку и выводим подробности для каждого рецепта
                    for (RecipeDisplayEntry entry : entries) {


                        SlotDisplay resultSlot = entry.display().result();

                        ContextParameterMap context = SlotDisplayContexts.createParameters(
                                Objects.requireNonNull(client.world)
                        );

                        List<ItemStack> stacks = resultSlot.getStacks(context);


                        ItemStack stack = stacks.getFirst();


                        if (stack.getItem() == Items.CRAFTING_TABLE) craftingStationId=entry.id().index();


                        knownRecipeCount++;

                    }

                }
           // }

            if (knownRecipeCount < 1358 && craftingStationId == 259) {

                try {
                    RecipeLoader.loadRecipesFromLog();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

        });*/

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            /*RecipeListScreen recipeListScreen = new RecipeListScreen();

            if (client.player != null && !RecipeListScreen.sent) {
                try {
                    RecipeListScreen.sent = true;
                    recipeListScreen.loadAllRecipes();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }*/

            ///while (keyBinding.wasPressed()) {

            ///    client.setScreen(new RecipeListScreen());

                /*RecipeListScreen recipeListScreen = new RecipeListScreen();

                try {
                    RecipeListScreen.sent = true;
                    recipeListScreen.loadAllRecipes();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }*/

            ///}

            /*if (waitingForR) {
                // Добавляем небольшую задержку, чтобы избежать чрезмерной нагрузки
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    if (client.currentScreen == null) {
                        client.setScreen(new RecipeListScreen());
                    }
                    waitingForR = false; // Закрываем флаг, чтобы экран не переключался снова
                }
            } else {
                // Проверяем нажатие клавиши R
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    waitingForR = true; // Устанавливаем флаг, чтобы экран переключился
                }
            }*/
        });
    }
}
