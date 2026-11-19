package com.byteup.jei.index;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A normalized recipe plus a 3x3 visual arrangement for JEI-style rendering. */
public record RecipeEntry(String key, String type, ItemStack result, List<Material> ingredients, List<Material> grid) {
    public RecipeEntry {
        result = result.clone();
        ingredients = List.copyOf(ingredients);
        List<Material> normalized = new ArrayList<>(grid == null ? List.of() : grid);
        while (normalized.size() < 9) normalized.add(Material.AIR);
        if (normalized.size() > 9) normalized = new ArrayList<>(normalized.subList(0, 9));
        grid = List.copyOf(normalized);
    }

    @Override
    public ItemStack result() {
        return result.clone();
    }
}
