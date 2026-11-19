package com.byteup.jei.index;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Keyed;
import org.bukkit.inventory.*;

import java.util.*;

public final class RecipeIndex {
    private final List<Material> items;
    private final List<RecipeEntry> recipes;

    private RecipeIndex(List<Material> items, List<RecipeEntry> recipes) {
        this.items = List.copyOf(items);
        this.recipes = List.copyOf(recipes);
    }

    public static RecipeIndex build() {
        List<Material> items = Arrays.stream(Material.values())
                .filter(Material::isItem).filter(m -> !m.isAir())
                .sorted(Comparator.comparing(Material::name)).toList();
        List<RecipeEntry> recipes = new ArrayList<>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe == null) continue;
            ItemStack result;
            try { result = recipe.getResult(); }
            catch (RuntimeException ex) { continue; }
            if (result == null || result.getType().isAir()) continue;
            RecipeVisual visual = visual(recipe);
            String key = recipe instanceof Keyed keyed ? keyed.getKey().toString()
                    : recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            recipes.add(new RecipeEntry(key, readableType(recipe), result, visual.allIngredients(), visual.grid()));
        }
        recipes.sort(Comparator.comparing((RecipeEntry e) -> e.result().getType().name()).thenComparing(RecipeEntry::key));
        return new RecipeIndex(items, recipes);
    }

    public List<Material> searchItems(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return items;
        return items.stream().filter(m -> m.name().toLowerCase(Locale.ROOT).contains(q)
                || displayName(m).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    public List<RecipeEntry> allRecipes() { return recipes; }
    public List<RecipeEntry> recipesFor(Material result) { return recipes.stream().filter(e -> e.result().getType() == result).toList(); }
    public List<RecipeEntry> recipesUsing(Material ingredient) { return recipes.stream().filter(e -> e.ingredients().contains(ingredient)).toList(); }
    public int itemCount() { return items.size(); }
    public int recipeCount() { return recipes.size(); }

    public static String displayName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static RecipeVisual visual(Recipe recipe) {
        List<Material> grid = new ArrayList<>(Collections.nCopies(9, Material.AIR));
        LinkedHashSet<Material> all = new LinkedHashSet<>();
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            int height = shape.length;
            int width = Arrays.stream(shape).mapToInt(String::length).max().orElse(0);
            int offsetX = Math.max(0, (3 - width) / 2);
            int offsetY = Math.max(0, (3 - height) / 2);
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < height && row < 3; row++) {
                String line = shape[row];
                for (int col = 0; col < line.length() && col < 3; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    List<Material> options = materials(choices.get(symbol));
                    all.addAll(options);
                    if (!options.isEmpty()) grid.set((row + offsetY) * 3 + col + offsetX, options.get(0));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            int cell = 0;
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                List<Material> options = materials(choice);
                all.addAll(options);
                if (cell < 9 && !options.isEmpty()) grid.set(cell, options.get(0));
                cell++;
            }
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            List<Material> options = materials(cooking.getInputChoice());
            all.addAll(options);
            if (!options.isEmpty()) grid.set(4, options.get(0));
        } else if (recipe instanceof StonecuttingRecipe stonecutting) {
            List<Material> options = materials(stonecutting.getInputChoice());
            all.addAll(options);
            if (!options.isEmpty()) grid.set(4, options.get(0));
        } else if (recipe instanceof BrewingRecipe brewing) {
            List<Material> input = materials(brewing.getInput());
            List<Material> ingredient = materials(brewing.getIngredient());
            all.addAll(input); all.addAll(ingredient);
            if (!input.isEmpty()) grid.set(4, input.get(0));
            if (!ingredient.isEmpty()) grid.set(1, ingredient.get(0));
        } else if (recipe instanceof TransmuteRecipe transmute) {
            List<Material> input = materials(transmute.getInput());
            List<Material> material = materials(transmute.getMaterial());
            all.addAll(input); all.addAll(material);
            if (!input.isEmpty()) grid.set(3, input.get(0));
            if (!material.isEmpty()) grid.set(5, material.get(0));
        } else if (recipe instanceof SmithingRecipe smithing) {
            List<Material> base = materials(smithing.getBase());
            List<Material> addition = materials(smithing.getAddition());
            all.addAll(base); all.addAll(addition);
            if (recipe instanceof SmithingTransformRecipe transform) {
                List<Material> template = materials(transform.getTemplate()); all.addAll(template);
                if (!template.isEmpty()) grid.set(3, template.get(0));
            } else if (recipe instanceof SmithingTrimRecipe trim) {
                List<Material> template = materials(trim.getTemplate()); all.addAll(template);
                if (!template.isEmpty()) grid.set(3, template.get(0));
            }
            if (!base.isEmpty()) grid.set(4, base.get(0));
            if (!addition.isEmpty()) grid.set(5, addition.get(0));
        }
        return new RecipeVisual(List.copyOf(all), List.copyOf(grid));
    }

    private static List<Material> materials(RecipeChoice choice) {
        if (choice == null) return List.of();
        if (choice instanceof RecipeChoice.MaterialChoice mc) return mc.getChoices().stream().filter(Objects::nonNull).toList();
        if (choice instanceof RecipeChoice.ExactChoice ec) return ec.getChoices().stream().filter(Objects::nonNull)
                .map(ItemStack::getType).filter(m -> !m.isAir()).distinct().toList();
        try {
            ItemStack example = choice.getItemStack();
            if (example != null && !example.getType().isAir()) return List.of(example.getType());
        } catch (RuntimeException ignored) { }
        return List.of();
    }

    private static String readableType(Recipe recipe) {
        String name = recipe.getClass().getSimpleName().replace("Recipe", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2");
        return name.isBlank() ? "Recipe" : name;
    }

    private record RecipeVisual(List<Material> allIngredients, List<Material> grid) { }
}
