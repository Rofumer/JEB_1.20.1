package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.DummySingleItemRecipe;
import jeb.client.FavoritesManager;
import jeb.client.JEBClient;
import jeb.client.RecipeIndex;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ToggleButtonWidget;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static jeb.client.JEBClient.*;

@Mixin(RecipeBookWidget.class)
//public abstract class RecipeBookWidgetSearchMixin<T extends AbstractRecipeScreenHandler> implements RecipeBookWidgetBridge {
public abstract class RecipeBookWidgetSearchMixin implements RecipeBookWidgetBridge {

    // Это будет вызов приватного метода
    @Shadow
    public void refresh() {}

    @Override
    public void jeb$refresh() {
        this.refresh();
    }

    @Shadow
    private ClientRecipeBook recipeBook;

    @Shadow
    private RecipeGroupButtonWidget currentTab;

    @Shadow
    protected MinecraftClient client;

    @Final
    @Shadow
    private RecipeBookResults recipesArea;

    @Shadow
    private TextFieldWidget searchField;

    @Shadow
    @Final
    private List<RecipeGroupButtonWidget> tabButtons;

    @Shadow
    protected ToggleButtonWidget toggleCraftableButton;

    @Unique
    private ToggleButtonWidget jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Shadow
    protected AbstractRecipeScreenHandler<?> craftingScreenHandler;

    /*
    @Unique
    private static final ButtonTextures TEXTURES_ALT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final ButtonTextures TEXTURES_DEFAULT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled_highlighted")
    );*/



    @Inject(method = "reset", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.toggleCraftableButton.getX();
        int y = this.toggleCraftableButton.getY()+120;

        jeb$customToggleButton = new ToggleButtonWidget(x, y, 24, 24, false);
        if(JEBClient.customToggleEnabled){
            jeb$customToggleButton.setTooltip(Tooltip.of(Text.of("Show 3x3")));
            jeb$customToggleButton.setToggled(false);
            jeb$customToggleButton.setTextureUV(
                    152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                    new Identifier("minecraft", "textures/gui/recipe_book.png")  // текстура
            );
        }
        else
        {
            jeb$customToggleButton.setTooltip(Tooltip.of(Text.of("Show 2x2")));
            jeb$customToggleButton.setToggled(true);
            jeb$customToggleButton.setTextureUV(
                    152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                    new Identifier("minecraft", "textures/gui/recipe_book.png")  // текстура
            );
        }
        jeb$customToggleButton.setMessage(Text.of("!"));
        jeb$customToggleButton.visible = true;

    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ToggleButtonWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    ordinal = 0, // если их несколько, выбирай нужный
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(context, mouseX, mouseY, delta);
        }
    }


    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    private void jeb$clickCustomToggle(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(mouseX, mouseY, button)) {
            jeb$customToggleState = !jeb$customToggleState;
            jeb$customToggleButton.setToggled(jeb$customToggleState);
            JEBClient.customToggleEnabled = !JEBClient.customToggleEnabled;

            JEBClient.saveConfig();
            // Меняем текстуру в зависимости от состояния
            //jeb$customToggleButton.setTextures(JEBClient.customToggleEnabled ? TEXTURES_ALT : TEXTURES_DEFAULT);
            if(JEBClient.customToggleEnabled){
                jeb$customToggleButton.setTextureUV(
                        152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                        new Identifier("minecraft", "textures/gui/recipe_book.png")  // текстура
                );
                jeb$customToggleButton.setToggled(false);
            }
            else
            {
                jeb$customToggleButton.setTextureUV(
                        152, 78, 26, 26,           // pressedUOffset (сдвиг по X при неактивном состоянии), hoverVOffset (сдвиг по Y при наведении)
                        new Identifier("minecraft", "textures/gui/recipe_book.png")  // текстура
                );
                jeb$customToggleButton.setToggled(true);
            }

            jeb$customToggleButton.setTooltip(JEBClient.customToggleEnabled ? Tooltip.of(Text.of("Show 3x3")):Tooltip.of(Text.of("Show 2x2")));

            //System.out.println("Кастомная кнопка: " + (jeb$customToggleState ? "включена" : "выключена"));

            // Рефреш через reflection
            /*try {
                Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            ((RecipeBookWidgetBridge) this).jeb$refresh();

            cir.setReturnValue(true);
        }
    }


    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;clear()V",
                    shift = At.Shift.AFTER
            )
    )
    private void injectCustomTab(CallbackInfo ci) {


        // Создаём кнопку вкладки
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        tabButton.setMessage(Text.of("Favorites"));


        this.tabButtons.add(tabButton);

    }*/

    /*@Inject(method = "<init>", at = @At("RETURN"))
    private void injectAfterConstructor(T craftingScreenHandler, List<RecipeBookWidget.Tab> tabs, CallbackInfo ci) {

        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        //RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        //tabButton.setMessage(Text.of("Favorites"));
        this.tabs.add(newTab);

    }*/

    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeGroupButtonWidget;setToggled(Z)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void jeb$replaceFavoritesAsDefaultTab(CallbackInfo ci) {
        if (this.currentTab == tabButtons.get(0) && tabButtons.size() > 1) {
            RecipeGroupButtonWidget maybeFavorites = tabButtons.get(0);
            //if ("Favorites".equals(maybeFavorites.getMessage().getString())) {
                // Сбросить подсветку со старой
                //maybeFavorites.setToggled(true);
                maybeFavorites.setToggled(true);

            //this.refreshTabButtons(bl);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);

                // Назначить новую
                this.currentTab = tabButtons.get(1);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);
            //}
        }
    }*/

    /*@Unique
    private boolean isFavoritesTabActive() {
        if (currentTab == null) return false;

        return tabButtons.stream()
                .filter(button -> button.isSelected())
                .anyMatch(button -> "Favorites".equals(button.getMessage().getString()));
    }*/

    @Unique
    private boolean isFavoritesTabActive() {
        //return currentTab != null
        //        && currentTab.getMessage() != null
        //        && "Favorites".equals(currentTab.getMessage().getString());
        return currentTab.getCategory() == RecipeBookGroup.CAMPFIRE;
    }


    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Проверка на нужную клавишу (например, клавиша G, keyCode = 71)
        //if (keyCode == GLFW.GLFW_KEY_A) {
        if (JEBClient.keyBinding.matchesKey(keyCode, scanCode)) {
            AnimatedResultButton hovered = ((RecipeBookResultsAccessor) recipesArea).getHoveredResultButton();
            if (hovered != null) {
                //System.out.println("Над кнопкой: " + hovered.getDisplayStack().getItem().toString());
                //ItemStack stack = hovered.getDisplayStack();
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.currentRecipe().getOutput(hovered.getResultCollection().getRegistryManager()));
                    // Рефреш через reflection
                    /*try {
                        Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                        method.setAccessible(true);
                        method.invoke(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.currentRecipe().getOutput(hovered.getResultCollection().getRegistryManager()));
                }
                //FavoritesManager.saveFavorite(stack);
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                // Здесь можно выполнить любое действие, например, выбрать рецепт, показать информацию и т.д.
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickRecipe(ILnet/minecraft/recipe/Recipe;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();

        RecipeManager recipeManager = client.world.getRecipeManager();


        Recipe<?> recipe = this.recipesArea.getLastClickedRecipe();

        RecipeResultCollection collection = this.recipesArea.getLastClickedResults();

        ScreenHandler screenHandler = client.player.currentScreenHandler;

        if(collection != null && recipe != null && screenHandler != null && !collection.hasCraftableRecipes()) {
            recipeManager.get(recipe.getId()).ifPresent(recipe1 -> {
                if (this.client.currentScreen instanceof RecipeBookProvider) {
                    RecipeBookWidget recipeBookWidget = ((RecipeBookProvider) this.client.currentScreen).getRecipeBookWidget();
                    recipeBookWidget.showGhostRecipe(recipe1, (List) screenHandler.slots);
                }
            });
        }



        ///if (screen instanceof RecipeBookProvider provider && entry != null) {
        ///    //System.out.println("РецептL " + entry.display().toString());
        ///    if(!results.isCraftable(recipeId) && recipeId.index()!=9999) {
        ///        provider.onCraftFailed(entry.display());
        ///    }
        ///}
    }


    /*@Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomIngredientSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        if (!string.startsWith("#")) return;

        String query = string.substring(1).toLowerCase(Locale.ROOT);
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                if (recipeDisplayMatchesIngredientQuery(entry, query)) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }*/

    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(Recipe<?> recipe, String query) {
        query = query.toLowerCase(Locale.ROOT);

        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                String itemName = stack.getItem().getName().getString().toLowerCase(Locale.ROOT);
                if (itemName.contains(query)) {
                    return true;
                }
            }
        }

        return false;
    }


    /****@Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query) {
        if (entry.display() == null || entry.display().result() == null) return false;

        SlotDisplay resultSlot = entry.display().result();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(this.client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT);

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
        TooltipType tooltipType = TooltipType.Default.BASIC;

        List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, tooltipType);
        for (Text line : tooltip) {
            String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
            if (clean.contains(query)) return true;
        }

        return false;
    }



    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        //if (string.isEmpty()) return;

        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                boolean match =
                        recipeResultMatchesQuery(entry, query) ||
                                (searchIngredients && recipeDisplayMatchesIngredientQuery(entry, query));

                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }****/

        //System.out.println("filteredList содержит " + filteredList.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor

        // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в filteredList как результат
        /*Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : filteredList) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }*/


        /// ////////
        /*for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(string.toLowerCase())) continue;

            Identifier id = Registries.ITEM.getId(item);
            System.out.println("Item: " + id);

            NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = new ArrayList<>();
            slots.add(new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath()))));

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));

            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(recipeDisplayEntry));

            filteredList.add(myCustomRecipeResultCollection);
        }*/

        /****filteredList.addAll(JEBClient.generateCustomRecipeList(string));****/

        //if (!string.isEmpty()) {
            /*for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = Registries.ITEM.getId(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getName().getString().toLowerCase(); // без Locale
                String searchLower = string.toLowerCase(); // без Locale

                // Если id или имя содержит текст поиска
                if (!idString.contains(searchLower) && !name.contains(searchLower)) continue;

                NetworkRecipeId recipeId = new NetworkRecipeId(9999);

                List<SlotDisplay> slots = List.of(
                        new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
                );

                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item));
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);

                OptionalInt group = OptionalInt.empty();
                RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
                List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                RecipeResultCollection resultCollection = new RecipeResultCollection(List.of(entry));

                filteredList.add(resultCollection);
            }*/
        //}



        //System.out.println("2: filteredList содержит " + filteredList.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + string);
        
    /****    recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }****/

    @Unique
    private boolean recipeResultMatchesQuery(Recipe<?> recipe, String query, String modName) {
        if (recipe == null || recipe.getOutput(this.client.world.getRegistryManager()) == null || recipe.getOutput(this.client.world.getRegistryManager()).isEmpty()) {
            return false;
        }

        ItemStack stack = recipe.getOutput(this.client.world.getRegistryManager());
        if (stack == null || stack.isEmpty()) return false;

        MinecraftClient client = MinecraftClient.getInstance();

        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT);

        // Проверка на имя мода
        if (modName != null && !modName.isEmpty()) {
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            if (!itemId.getNamespace().toLowerCase(Locale.ROOT).contains(modName.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Поиск по имени, ID или translationKey
        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по tooltip'у
        try {
            List<Text> tooltip = stack.getTooltip(client.player, client.options.advancedItemTooltips ? (TooltipContext)TooltipContext.Default.ADVANCED : (TooltipContext)TooltipContext.Default.BASIC);
            for (Text line : tooltip) {
                String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                if (clean.contains(query)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }


    @Shadow
    private final RecipeMatcher recipeFinder = new RecipeMatcher();

    @Shadow public abstract void reset();


    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, CallbackInfo ci) {
        String search = searchField.getText();
        boolean isIngredientSearch = search.startsWith("#");
        boolean searchByResult = search.startsWith("~");
        String query = (isIngredientSearch || searchByResult ? search.substring(1) : search).toLowerCase();

        final String finalQuery;
        final String finalModName;

        if (search.startsWith("@")) {
            int endIdx = search.indexOf(' ');
            if (endIdx != -1) {
                finalModName = search.substring(1, endIdx).trim();
                finalQuery = search.substring(endIdx + 1).toLowerCase();
            } else {
                finalModName = search.substring(1).trim();
                finalQuery = "";
            }
        } else if (isIngredientSearch) {
            finalModName = null;
            finalQuery = search.substring(1).toLowerCase();
        } else {
            finalModName = null;
            finalQuery = search.toLowerCase();
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForGroup(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = new ArrayList<>();

        if (search.startsWith("~") && !isFavoritesTabActive()) {
            List<RecipeResultCollection> ingredientsList = new ArrayList<>();

            for (RecipeResultCollection collection : recipeBook.getResultsForGroup(currentTab.getCategory())) {
                for (Recipe<?> recipe : collection.getAllRecipes()) {
                    ItemStack result = recipe.getOutput(client.world.getRegistryManager());
                    String resultName = result.getItem().toString();//.getString().toLowerCase();
                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe.getIngredients()) {
                            for (ItemStack stack : ingredient.getMatchingStacks()) {
                                if (!stack.isEmpty()) {
                                    // Создаём фиктивный RecipeCollection с одним "рецептом" — результат stack
                                    // Проверяем, есть ли коллекция рецептов, где результат — этот ингредиент
                                    boolean foundReal = false;
                                    for (RecipeResultCollection subCollection : recipeBook.getResultsForGroup(RecipeBookGroup.CRAFTING_SEARCH)) {
                                        for (Recipe<?> subRecipe : subCollection.getAllRecipes()) {
                                            ItemStack subResult = subRecipe.getOutput(client.world.getRegistryManager());
                                            if (!subResult.isEmpty() && ItemStack.areItemsEqual(subResult, stack)) {
                                                subCollection.computeCraftables(recipeFinder,
                                                        3,
                                                        3,
                                                        recipeBook);
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
                                    }

// Если не нашли настоящих рецептов — добавим фейковую коллекцию
                                    if (!foundReal) {
                                        Recipe<?> fakeRecipe = new DummySingleItemRecipe(stack);
                                        RecipeResultCollection dummycollection = new RecipeResultCollection(client.world.getRegistryManager(), List.of(fakeRecipe));
                                                dummycollection.computeCraftables(recipeFinder,
                                                        3,
                                                        3,
                                                        recipeBook);
                                        ingredientsList.add(dummycollection);
                                    }

                                    break; // только один stack из одного ingredient
                                }
                            }
                        }
                    }
                }
            }

            filteredList.addAll(ingredientsList);
            recipesArea.setResults(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }

        if (isFavoritesTabActive()) {
            Set<Identifier> favorites = FavoritesManager.loadFavoriteItemIds();
            originalList = recipeBook.getResultsForGroup(RecipeBookGroup.CRAFTING_SEARCH);

            for (RecipeResultCollection collection : originalList) {
                List<Recipe<?>> matching = collection.getAllRecipes().stream()
                        .filter(recipe -> favorites.contains(Registries.ITEM.getId(recipe.getOutput(client.world.getRegistryManager()).getItem())))
                        .toList();

                if (!matching.isEmpty()) {
                    RecipeResultCollection fakeCollection = new RecipeResultCollection(client.world.getRegistryManager(), matching);
                    fakeCollection.computeCraftables(recipeFinder,
                            3,
                            3,
                            recipeBook);
                    filteredList.add(fakeCollection);
                }
            }

            recipesArea.setResults(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }

        /*for (RecipeResultCollection collection : originalList) {
            if (!collection.hasFittingRecipes()) continue;

            boolean matches = collection.getAllRecipes().stream().anyMatch(recipe ->
                    isIngredientSearch ? recipeDisplayMatchesIngredientQuery(recipe, finalQuery)
                            : recipeResultMatchesQuery(recipe, finalQuery, finalModName)
            );

            if (matches) {
                filteredList.add(collection);
            }
        }*/

        filteredList = new ArrayList<>(RecipeIndex.fastSearch(currentTab.getCategory(),finalQuery, finalModName, isIngredientSearch));

        if(!(((RecipeBookWidgetAccessor) this).getSearchField().isActive() && ((RecipeBookWidgetAccessor) this).getSearchField().isVisible() && ((RecipeBookWidgetAccessor) this).getSearchField().isFocused())) {
            filteredList.forEach(result ->
                    result.computeCraftables(recipeFinder,
                            craftingScreenHandler.getCraftingWidth(),
                            craftingScreenHandler.getCraftingHeight(),
                            recipeBook)
            );
        }

        if (jeb$customToggleState) {
            filteredList.removeIf(result -> !result.hasFittingRecipes());
        }

        if (recipeBook.isFilteringCraftable(craftingScreenHandler)) {
            filteredList.removeIf(result -> !result.hasCraftableRecipes());
        }

        //filteredList.addAll(JEBClient.generateCustomRecipeList(search));

        if(!Objects.equals(string,search))
        {
            filtered = JEBClient.generateCustomRecipeList(search);
        }

        if(!toggleCraftableButton.isToggled()) {
            filteredList.addAll(filtered);
        }

        string=search;

        recipesArea.setResults(filteredList, resetCurrentPage);
        ci.cancel();
    }


    /*@Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, CallbackInfo ci) {
        String string = searchField.getText();
        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        String modName = null;
        if (string.startsWith("@")) {
            int endIndex = string.indexOf(" ");
            if (endIndex != -1) {
                modName = string.substring(1, endIndex).trim();
                query = string.substring(endIndex + 1).toLowerCase();
            } else {
                modName = string.substring(1).trim();
                query = "";
            }
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForGroup(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();
        // === Если на вкладке избранного (используем CAMPFIRE как временную категорию) ===
        if (isFavoritesTabActive()) {
            originalList = new ArrayList<>();

            //for (RecipeBookGroup group : RecipeBookGroup.CRAFTING) {
                //originalList.addAll(recipeBook.getResultsForGroup(group));
            originalList.addAll(recipeBook.getResultsForGroup(RecipeBookGroup.CRAFTING_SEARCH));
            //}

            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();

            List<RecipeResultCollection> matching = null;
            for (RecipeResultCollection collection : originalList) {

                matching = new ArrayList<>();
                for (Recipe<?> entry : collection.getAllRecipes()) {
                    ItemStack stack = entry.getOutput(this.client.world.getRegistryManager());
                    if (!stack.isEmpty()) {
                        Identifier itemId = Registries.ITEM.getId(stack.getItem());
                        if (favoriteItems.contains(itemId)) {
                            matching.add(new RecipeResultCollection(this.client.world.getRegistryManager(),List.of(entry)));
                        }
                    }
                }

                if(!matching.isEmpty()) {
                    filteredList.add(collection);
                }

            }

            //if (!matching.isEmpty()) {
            //    filteredList.addAll(matching);
            //}

            //if (filteringCraftable) {
            //    filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
            //}

            ///recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
            recipesArea.setResults(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }

        // === Обычный поиск ===
        for (RecipeResultCollection collection : originalList) {

            if (!collection.hasFittingRecipes()) continue;

            for (Recipe<?> entry : collection.getAllRecipes()) {

                boolean match;
                if (searchIngredients) {
                    match = recipeDisplayMatchesIngredientQuery(entry, query);
                } else {
                    match = recipeResultMatchesQuery(entry, query, modName);
                }
                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        filteredList.forEach((resultCollection) -> resultCollection.computeCraftables(this.recipeFinder, this.craftingScreenHandler.getCraftingWidth(), this.craftingScreenHandler.getCraftingHeight(), this.recipeBook));

        if(jeb$customToggleState) {
            filteredList.removeIf((resultCollection) -> !resultCollection.hasFittingRecipes());
        }

        if (this.recipeBook.isFilteringCraftable(craftingScreenHandler)) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        filteredList.addAll(JEBClient.generateCustomRecipeList(string));

        //recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ///List<RecipeResultCollection> filteredList1 = Lists.newArrayList(filteredList);
        /////recipesArea.setResults(filteredList1, resetCurrentPage);
        ///if(jeb$customToggleState) {
        ///    filteredList1.removeIf((resultCollection) -> !resultCollection.hasFittingRecipes());
       /// }

       /// if (this.recipeBook.isFilteringCraftable(craftingScreenHandler)) {
         ///   filteredList1.removeIf(rc -> !rc.hasCraftableRecipes());
      ///  }


        recipesArea.setResults(filteredList, resetCurrentPage);
        ci.cancel();
    }*/



    /*
    @Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.StackSlotDisplay(ItemStack stack)) {
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay(RegistryEntry<Item> item)) {
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay(TagKey<Item> tag)) {

            // В 1.21.5 можно безопасно использовать iterateEntries
            for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.CompositeSlotDisplay(List<SlotDisplay> contents)) {
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }*/

    /*@Inject(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", shift = At.Shift.AFTER))
    private void addNewTab(CallbackInfo ci) {
        RecipeBookWidget<?> recipeBookWidget = (RecipeBookWidget<?>) (Object) this;

        // Получаем список вкладок через @Accessor
        List<RecipeBookWidget.Tab> tabs = ((RecipeBookWidgetAccessor) recipeBookWidget).gettabs();

        // Создаем изменяемую копию списка tabs
        List<RecipeBookWidget.Tab> newTabs = new ArrayList<>(tabs);

        // Создаем новую вкладку (Tab) с иконкой и категорией
        ItemStack primaryIcon = new ItemStack(Items.WRITABLE_BOOK);  // Иконка из алмаза
        RecipeBookCategory category = RecipeBookCategories.CAMPFIRE; // Категория рецептов
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(primaryIcon.getItem(), category);

        // Добавляем новую кнопку вкладки в список tabButtons
        newTabs.add(newTab);  // Добавляем новую кнопку вкладки

        try {
            java.lang.reflect.Field tabsField = RecipeBookWidget.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);  // Даем доступ к приватному полю
            tabsField.set(recipeBookWidget, newTabs);  // Устанавливаем новое значение
        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/

}
