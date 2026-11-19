package com.byteup.jei.ui;

import com.byteup.jei.ByteUpJEI;
import com.byteup.jei.index.RecipeEntry;
import com.byteup.jei.index.RecipeIndex;
import com.byteup.jei.pack.ResourcePackBuilder;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * JEI em tela cheia dentro do Dialog nativo.
 *
 * Modelo: o corpo do Dialog e um texto de {@link Layout#LINES} linhas de 9 px. Tudo e posicionado com
 * "espacos" de largura exata (fonte byteup:space). Como o jogo so testa clique/hover na LINHA sob o mouse,
 * cada area clicavel e emitida em todas as linhas que ela cobre (espaco invisivel com o evento de clique),
 * e cada icone e desenhado na linha do seu topo com a fonte row_r (r = y % 9).
 */
public final class JeiDialogRenderer {
    public static final int PAGE_SIZE = Layout.COLS * Layout.ROWS;
    private static final Key SCREEN_TOP = Key.key("byteup", "screen_top");
    private static final Key SCREEN_BOT = Key.key("byteup", "screen_bot");
    private static final Key SPACE_FONT = Key.key("byteup", "space");
    private static final int ADV = ResourcePackBuilder.GLYPH_ADV;
    private static final int[] SPACE_VALUES = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
    private static final int[] SPACE_POS = {0xF200, 0xF201, 0xF202, 0xF203, 0xF204, 0xF205, 0xF206, 0xF207, 0xF208, 0xF209, 0xF20A};
    private static final int[] SPACE_NEG = {0xF210, 0xF211, 0xF212, 0xF213, 0xF214, 0xF215, 0xF216, 0xF217, 0xF218, 0xF219, 0xF21A};
    private static final TextColor INK = TextColor.color(0x404040);
    private static final String[] FILTERS = {"crafting", "furnace", "smoker", "brewing", "smithing"};
    private static final Material[] FILTER_ICONS = {Material.CRAFTING_TABLE, Material.FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.SMITHING_TABLE};

    private final ByteUpJEI plugin;

    public JeiDialogRenderer(ByteUpJEI plugin) { this.plugin = plugin; }

    public void open(Player player, JeiViewState state) {
        player.showDialog(buildMainDialog(player, state));
    }

    // ------------------------------------------------------------------ dados

    private List<RecipeEntry> recipesFor(JeiViewState state) {
        List<RecipeEntry> all;
        if (state.selected == null) all = plugin.index().allRecipes();
        else if (state.uses) all = plugin.index().recipesUsing(state.selected);
        else all = plugin.index().recipesFor(state.selected);
        return filter(all, state.recipeFilter);
    }

    private List<RecipeEntry> filter(List<RecipeEntry> recipes, String filter) {
        if (filter == null || filter.equals("all")) return recipes;
        String f = filter.toLowerCase(Locale.ROOT);
        return recipes.stream().filter(recipe -> {
            String type = recipe.type().toLowerCase(Locale.ROOT);
            return switch (f) {
                case "crafting" -> type.contains("shaped") || type.contains("shapeless") || type.contains("craft");
                case "furnace" -> type.contains("furnace") || type.contains("smelt") || type.contains("blasting");
                case "smoker" -> type.contains("smok");
                case "brewing" -> type.contains("brew");
                case "smithing" -> type.contains("smith");
                default -> true;
            };
        }).toList();
    }

    private static String filterLabel(String filter) {
        return switch (filter == null ? "all" : filter) {
            case "crafting" -> "Crafting";
            case "furnace" -> "Fornalha";
            case "smoker" -> "Defumador";
            case "brewing" -> "Poções";
            case "smithing" -> "Ferraria";
            default -> "Todas as receitas";
        };
    }

    // ------------------------------------------------------------------ dialog

    private Dialog buildMainDialog(Player player, JeiViewState state) {
        int itemCount = plugin.index().searchItems(state.query).size();
        int itemPages = Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
        state.page = Math.max(0, Math.min(state.page, itemPages - 1));
        List<RecipeEntry> recipes = recipesFor(state);
        int recipePages = Math.max(1, (recipes.size() + 1) / 2);
        state.recipeOffset = Math.max(0, Math.min(state.recipeOffset, Math.max(0, (recipePages - 1) * 2)));

        DialogBase base = DialogBase.builder(Component.text("ByteUP JEI"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(renderCanvas(state, recipes, itemCount, itemPages, recipePages), Layout.BODY_W)))
                .build();
        ActionButton close = ActionButton.create(Component.text("Fechar"), null, 64, null);
        return Dialog.create(builder -> builder.empty().base(base).type(DialogType.notice(close)));
    }

    // ------------------------------------------------------------------ canvas

    private Component renderCanvas(JeiViewState state, List<RecipeEntry> recipes, int itemCount, int itemPages, int recipePages) {
        Canvas cv = new Canvas();

        // fundo: 2 fileiras de 3 tiles; cada fileira volta ao x=0 (avanco do tile = 220 + 1)
        int rowAdv = (ResourcePackBuilder.TILE_W + ADV) * ResourcePackBuilder.TILE_COLS;
        Component bg = Component.empty();
        for (int row = 0; row < ResourcePackBuilder.TILE_ROWS; row++) {
            for (int col = 0; col < ResourcePackBuilder.TILE_COLS; col++) {
                int code = ResourcePackBuilder.SCREEN_GLYPH + row * ResourcePackBuilder.TILE_COLS + col;
                bg = bg.append(Component.text(String.valueOf((char) code)).font(row == 0 ? SCREEN_TOP : SCREEN_BOT));
            }
            bg = bg.append(spaces(-rowAdv));
        }
        cv.add(0, 0, 0, bg, false);

        // abas
        for (int i = 0; i < FILTERS.length; i++) {
            int x = Layout.TAB_X0 + i * Layout.TAB_PITCH;
            String cmd = "/byteupjei ui filter " + FILTERS[i];
            cv.hit(x, 0, Layout.TAB_W, Layout.TAB_H, cmd, filterLabel(FILTERS[i]));
            cv.icon(x + Layout.TAB_ICON_DX, Layout.TAB_ICON_Y, glyphOf(FILTER_ICONS[i]), cmd, filterLabel(FILTERS[i]));
            if (FILTERS[i].equals(state.recipeFilter)) {
                cv.add(Layout.TAB_SEL_Y / Layout.LINE, x + 2, 26 + ADV,
                        glyph(ResourcePackBuilder.SEL_GLYPH, Layout.TAB_SEL_Y % Layout.LINE), false);
            }
        }
        // botao de modo (receitas <-> usos)
        String modeCmd = "/byteupjei ui mode " + (state.uses ? "recipes" : "uses");
        cv.hit(Layout.MODE_X, 0, Layout.MODE_W, Layout.MODE_H, modeCmd, state.uses ? "Ver receitas" : "Ver usos");
        cv.text(Layout.MODE_X + Layout.MODE_W / 2, Layout.MODE_TEXT_Y, state.uses ? "Receitas" : "Usos", INK, true);

        // barras do painel de receitas
        String subject = state.selected == null ? filterLabel(state.recipeFilter)
                : RecipeIndex.displayName(state.selected) + (state.uses ? " (usos)" : "");
        navButtons(cv, Layout.BAR_X, Layout.NEXT_X, Layout.BAR1_Y, "/byteupjei ui recipe-prev", "/byteupjei ui recipe-next",
                state.recipeOffset > 0, state.recipeOffset + 2 < recipes.size());
        cv.text(Layout.BAR_CENTER_X, Layout.BAR1_TEXT_Y, subject, INK, true);
        navButtons(cv, Layout.BAR_X, Layout.NEXT_X, Layout.BAR2_Y, "/byteupjei ui recipe-prev", "/byteupjei ui recipe-next",
                state.recipeOffset > 0, state.recipeOffset + 2 < recipes.size());
        cv.text(Layout.BAR_CENTER_X, Layout.BAR2_TEXT_Y, recipes.isEmpty() ? "Sem receitas"
                : "Receita " + (state.recipeOffset / 2 + 1) + " / " + recipePages, INK, true);

        // dois cartoes de receita
        for (int card = 0; card < 2; card++) {
            int top = Layout.CARD_Y[card];
            int idx = state.recipeOffset + card;
            if (idx >= recipes.size()) {
                if (card == 0 && recipes.isEmpty()) cv.text(Layout.BAR_CENTER_X, top + 36, "Nenhuma receita encontrada.", INK, true);
                continue;
            }
            RecipeEntry recipe = recipes.get(idx);
            List<Material> grid = recipe.grid();
            for (int slot = 0; slot < 9 && slot < grid.size(); slot++) {
                Material ingredient = grid.get(slot);
                if (ingredient == null || ingredient.isAir()) continue;
                int x = Layout.GRID_X + (slot % 3) * Layout.PITCH + Layout.ICON_OFF;
                int y = top + Layout.GRID_DY + (slot / 3) * Layout.PITCH + Layout.ICON_OFF;
                cv.icon(x, y, glyphOf(ingredient), "/byteupjei ui ingredient " + ingredient.name(), RecipeIndex.displayName(ingredient));
            }
            Material result = recipe.result().getType();
            int rx = Layout.RES_X + Layout.RES_ICON_OFF;
            int ry = top + Layout.RES_DY + Layout.RES_ICON_OFF;
            cv.icon(rx, ry, glyphOf(result), "/byteupjei ui item " + result.name(), RecipeIndex.displayName(result));
            int amount = recipe.result().getAmount();
            if (amount > 1) {
                int slotBottom = top + Layout.RES_DY + Layout.RES_SLOT;
                int textY = ((slotBottom - 10) / Layout.LINE) * Layout.LINE;
                String s = String.valueOf(amount);
                cv.text(Layout.RES_X + Layout.RES_SLOT - 3 - textWidth(s), textY, s, TextColor.color(0xFFFFFF), false);
            }
            cv.hit(Layout.PLUS_X, top + Layout.PLUS_DY, Layout.PLUS, Layout.PLUS, "/byteupjei ui recipe-next", "Próxima receita");
        }

        // painel de itens
        navButtons(cv, Layout.IPREV_X, Layout.INEXT_X, Layout.IBAR_Y, "/byteupjei ui prev", "/byteupjei ui next",
                state.page > 0, state.page < itemPages - 1);
        cv.text(Layout.IBAR_CENTER_X, Layout.IBAR_TEXT_Y, "Itens " + (state.page + 1) + " / " + itemPages, INK, true);

        List<Material> catalog = plugin.index().searchItems(state.query);
        int start = state.page * PAGE_SIZE;
        for (int i = start; i < Math.min(catalog.size(), start + PAGE_SIZE); i++) {
            int local = i - start;
            int x = Layout.ITEM_X0 + (local % Layout.COLS) * Layout.PITCH + Layout.ICON_OFF;
            int y = Layout.ITEM_Y0 + (local / Layout.COLS) * Layout.PITCH + Layout.ICON_OFF;
            Material m = catalog.get(i);
            cv.icon(x, y, glyphOf(m), "/byteupjei ui item " + m.name(), RecipeIndex.displayName(m));
        }

        // campo de pesquisa: clique abre a caixa de texto nativa
        ClickEvent searchClick = ClickEvent.showDialog(searchDialog(state));
        cv.hitEvent(Layout.SEARCH_X, Layout.SEARCH_Y, Layout.SBTN_X + Layout.SBTN_W - Layout.SEARCH_X, Layout.SEARCH_H,
                searchClick, "Pesquisar itens");
        boolean empty = state.query == null || state.query.isBlank();
        cv.text(Layout.SEARCH_X + 4, Layout.SEARCH_TEXT_Y, empty ? "Pesquisar..." : clip(state.query, 30),
                empty ? TextColor.color(0x777777) : TextColor.color(0xFFFFFF), false);

        return cv.build();
    }

    private void navButtons(Canvas cv, int prevX, int nextX, int y, String prevCmd, String nextCmd, boolean prevOn, boolean nextOn) {
        if (prevOn) cv.hit(prevX, y, Layout.BTN_W, Layout.BAR_H, prevCmd, "Anterior");
        if (nextOn) cv.hit(nextX, y, Layout.BTN_W, Layout.BAR_H, nextCmd, "Próximo");
    }

    private Integer glyphOf(Material material) {
        Integer code = plugin.pack() == null ? null : plugin.pack().glyphs().get(material);
        return code;
    }

    private static String clip(String s, int max) { return s.length() <= max ? s : s.substring(s.length() - max); }

    // ------------------------------------------------------------------ pesquisa

    private Dialog searchDialog(JeiViewState state) {
        var input = DialogInput.text("query", Component.text("Nome do item"))
                .initial(state.query).maxLength(64).width(420).build();
        var submitAction = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player target)) return;
            String query = response.getText("query");
            state.query = query == null ? "" : query.trim();
            state.page = 0;
            state.selected = null;
            state.recipeOffset = 0;
            target.showDialog(buildMainDialog(target, state));
        }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        ActionButton apply = ActionButton.create(Component.text("Pesquisar"), null, 110, submitAction);
        ActionButton cancel = ActionButton.create(Component.text("Cancelar"), null, 90, null);
        DialogBase searchBase = DialogBase.builder(Component.text("ByteUP JEI • Pesquisa"))
                .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.NONE)
                .inputs(List.of(input)).build();
        return Dialog.create(builder -> builder.empty().base(searchBase).type(DialogType.confirmation(apply, cancel)));
    }

    // ------------------------------------------------------------------ texto e espacos

    /** Largura aproximada (px) da fonte padrao, so para centralizar/alinhar. */
    static int textWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += switch (c) {
                case 'i', '!', '.', ',', ':', ';', '|' -> 2;
                case 'l', '\'' -> 3;
                case 'I', 't', ' ', '[', ']' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '"', '*' -> 5;
                default -> 6;
            };
        }
        return Math.max(0, w - 1);
    }

    private static Component glyph(int code, int row) {
        return Component.text(new String(Character.toChars(code))).font(Key.key("byteup", "row_" + row));
    }

    private static Component spaces(int delta) {
        Component out = Component.empty();
        int remaining = Math.abs(delta);
        int[] codes = delta >= 0 ? SPACE_POS : SPACE_NEG;
        for (int i = SPACE_VALUES.length - 1; i >= 0; i--) {
            while (remaining >= SPACE_VALUES[i]) {
                out = out.append(Component.text(String.valueOf((char) codes[i])).font(SPACE_FONT));
                remaining -= SPACE_VALUES[i];
            }
        }
        return out;
    }

    /** Espaco de largura exata w que carrega clique e dica (area clicavel invisivel). */
    private static Component hitSpace(int w, ClickEvent click, String tip) {
        Component out = Component.empty();
        int remaining = w;
        for (int i = SPACE_VALUES.length - 1; i >= 0; i--) {
            while (remaining >= SPACE_VALUES[i]) {
                Component c = Component.text(String.valueOf((char) SPACE_POS[i])).font(SPACE_FONT).clickEvent(click);
                if (tip != null) c = c.hoverEvent(HoverEvent.showText(Component.text(tip)));
                out = out.append(c);
                remaining -= SPACE_VALUES[i];
            }
        }
        return out;
    }

    private record Cell(int x, int adv, Component comp, boolean late) { }

    /** Linhas de 9 px; cada celula tem x absoluto e avanco; o texto vai por ultimo na linha. */
    private static final class Canvas {
        private final List<List<Cell>> lines = new ArrayList<>();

        Canvas() { for (int i = 0; i < Layout.LINES; i++) lines.add(new ArrayList<>()); }

        void add(int line, int x, int adv, Component comp, boolean late) {
            if (line < 0 || line >= lines.size()) return;
            lines.get(line).add(new Cell(x, adv, comp, late));
        }

        /** Area clicavel: um espaco invisivel em cada linha coberta. */
        void hit(int x, int y, int w, int h, String command, String tip) {
            hitEvent(x, y, w, h, ClickEvent.runCommand(command), tip);
        }

        void hitEvent(int x, int y, int w, int h, ClickEvent click, String tip) {
            for (int line = y / Layout.LINE; line <= (y + h - 1) / Layout.LINE; line++) {
                add(line, x, w, hitSpace(w, click, tip), false);
            }
        }

        /** Icone 16x16 com topo em y: glifo na linha do topo + hits nas linhas seguintes. */
        void icon(int x, int y, Integer code, String command, String tip) {
            if (code == null) return;
            ClickEvent click = ClickEvent.runCommand(command);
            int line = y / Layout.LINE;
            Component g = glyph(code, y % Layout.LINE).clickEvent(click).hoverEvent(HoverEvent.showText(Component.text(tip)));
            add(line, x, 16 + ADV, g, false);
            for (int l = line + 1; l <= (y + 15) / Layout.LINE; l++) add(l, x, 16, hitSpace(16, click, tip), false);
        }

        /** Texto com a fonte padrao; y precisa ser multiplo de 9 (topo da linha). */
        void text(int x, int y, String s, TextColor color, boolean centered) {
            int w = textWidth(s);
            int left = centered ? x - w / 2 : x;
            add(y / Layout.LINE, left, w, Component.text(s, color), true);
        }

        Component build() {
            Component out = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                List<Cell> cells = new ArrayList<>(lines.get(i));
                cells.sort(Comparator.comparing(Cell::late).thenComparingInt(Cell::x));
                int pen = 0;
                for (Cell c : cells) {
                    int delta = c.x() - pen;
                    if (delta != 0) out = out.append(spaces(delta));
                    out = out.append(c.comp());
                    pen = c.x() + c.adv();
                }
                if (i < lines.size() - 1) out = out.append(Component.newline());
            }
            return out;
        }
    }
}
