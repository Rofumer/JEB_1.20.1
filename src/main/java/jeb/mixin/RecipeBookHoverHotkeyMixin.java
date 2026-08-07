package jeb.mixin;

import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.JEBClient;
import jeb.client.RecipeSearchQueries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Хоткеи наведения (как в JEI): R — «рецепты этого предмета», U — «где используется».
 * Работают в любом экране с книгой рецептов (верстак, печи, инвентарь игрока):
 * наводим курсор на любой слот и жмём клавишу — книга откроется (если была закрыта)
 * с подставленным поиском.
 *
 * <p>Общего «экрана с книгой рецептов» нет, поэтому цель — HandledScreen,
 * а наличие книги проверяется через RecipeBookProvider.
 */
@Mixin(HandledScreen.class)
public abstract class RecipeBookHoverHotkeyMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void jeb$onHoverHotkey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        boolean viewRecipe = JEBClient.keyViewRecipe != null
                && JEBClient.keyViewRecipe.matchesKey(keyCode, scanCode);
        boolean viewUses = JEBClient.keyViewUses != null
                && JEBClient.keyViewUses.matchesKey(keyCode, scanCode);

        if (!viewRecipe && !viewUses) {
            return;
        }

        if (!((Object) this instanceof RecipeBookProvider provider)) {
            return;
        }

        Slot hovered = this.focusedSlot;
        if (hovered == null || !hovered.hasStack()) {
            return;
        }

        RecipeBookWidget widget = provider.getRecipeBookWidget();
        if (widget == null) {
            return;
        }

        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) widget;

        // Инжект стоит перед тем, как событие дойдёт до поля поиска, поэтому во время
        // ввода текста хоткей срабатывать не должен.
        TextFieldWidget searchField = accessor.getSearchField();
        if (searchField != null && searchField.isFocused()) {
            return;
        }

        ItemStack stack = hovered.getStack();
        String searchText = viewRecipe
                ? RecipeSearchQueries.forResult(stack)
                : RecipeSearchQueries.forIngredient(stack);

        if (!widget.isOpen()) {
            widget.toggleOpen();
            // Открытая книга сдвигает GUI: пересобираем экран, чтобы x и кнопка
            // книги встали на свои места (ванильная кнопка делает это же вручную).
            Screen screen = (Screen) (Object) this;
            screen.resize(MinecraftClient.getInstance(), screen.width, screen.height);
        }

        // После открытия книги виджеты пересоздаются — берём поле поиска заново.
        searchField = accessor.getSearchField();
        if (searchField == null) {
            return;
        }

        ((RecipeBookWidgetBridge) widget).jeb$pushHistory(searchField.getText(), accessor.getSelectedTab());
        searchField.setText(searchText);
        accessor.setSelectedTab((RecipeGroupButtonWidget) accessor.getTabButtons().get(0));
        accessor.invokeReset();

        cir.setReturnValue(true);
    }
}
