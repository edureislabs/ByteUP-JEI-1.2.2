package com.byteup.jei.ui;

/** Coordenadas (em px da tela de 660x370) - DEVEM bater com tools/gen_atlas.py. */
public final class Layout {
    private Layout() { }

    public static final int CANVAS_W = 660, CANVAS_H = 370;
    public static final int LINE = 9;                 // altura de uma linha de texto do Dialog
    public static final int LINES = 42;               // 42 * 9 = 378 >= 370
    public static final int BODY_W = 720;             // maior que o canvas: evita quebra de linha

    // abas e botao de modo
    public static final int TAB_X0 = 10, TAB_PITCH = 32, TAB_W = 30, TAB_H = 27, TAB_ICON_DX = 7, TAB_ICON_Y = 5, TAB_SEL_Y = 22;
    public static final int MODE_X = 180, MODE_W = 60, MODE_H = 27, MODE_TEXT_Y = 9;

    // painel de receitas
    public static final int BAR_X = 10, BAR_W = 340, BAR_H = 18, BTN_W = 22, NEXT_X = 328;
    public static final int BAR1_Y = 32, BAR1_TEXT_Y = 36, BAR2_Y = 57, BAR2_TEXT_Y = 63, BAR_CENTER_X = 180;
    public static final int[] CARD_Y = {82, 226};
    public static final int GRID_X = 30, GRID_DY = 28, PITCH = 28, ICON_OFF = 5;
    public static final int RES_X = 204, RES_DY = 52, RES_SLOT = 34, RES_ICON_OFF = 9;
    public static final int PLUS_X = 316, PLUS_DY = 110, PLUS = 22;

    // painel de itens
    public static final int COLS = 9, ROWS = 9;
    public static final int ITEM_X0 = 391, ITEM_Y0 = 40;
    public static final int IPREV_X = 382, INEXT_X = 628, IBAR_Y = 13, IBAR_TEXT_Y = 18, IBAR_CENTER_X = 516;
    public static final int SEARCH_X = 391, SEARCH_Y = 309, SEARCH_W = 206, SEARCH_TEXT_Y = 315, SBTN_X = 601, SBTN_W = 24, SEARCH_H = 20;
}
