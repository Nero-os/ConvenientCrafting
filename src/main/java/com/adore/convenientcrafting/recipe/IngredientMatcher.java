package com.adore.convenientcrafting.recipe;

import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Capacity-aware matching: flexible ingredients can be reassigned to reserve scarce materials. */
public final class IngredientMatcher {
    private IngredientMatcher() {}

    public static int[] match(List<Ingredient> ingredients, List<ItemStack> materials) {
        return match(ingredients, materials, (a, b) -> false);
    }

    /** Incompatible sources include a bag and the contents of that same bag. */
    public static int[] match(List<Ingredient> ingredients, List<ItemStack> materials,
                              BiPredicate<Integer, Integer> incompatible) {
        Deque<BitSet> pending = new ArrayDeque<>();
        Set<BitSet> visited = new HashSet<>();
        pending.add(new BitSet());
        // Conflict search is bounded independently of the polynomial ingredient matcher.
        for (int attempts = 0; !pending.isEmpty() && attempts < 4096; attempts++) {
            BitSet excluded = pending.removeLast();
            if (!visited.add(excluded)) continue;
            int[] assignment = assign(ingredients, materials, excluded);
            if (assignment.length != ingredients.size()) continue;
            int left = -1, right = -1;
            outer: for (int i = 0; i < assignment.length; i++) {
                for (int j = i + 1; j < assignment.length; j++) {
                    if (incompatible.test(assignment[i], assignment[j])) {
                        left = assignment[i]; right = assignment[j]; break outer;
                    }
                }
            }
            if (left < 0) return assignment;
            BitSet withoutLeft = (BitSet) excluded.clone();
            withoutLeft.set(left);
            pending.add(withoutLeft);
            BitSet withoutRight = (BitSet) excluded.clone();
            withoutRight.set(right);
            pending.add(withoutRight);
        }
        return new int[0];
    }

    private static int[] assign(List<Ingredient> ingredients, List<ItemStack> materials, BitSet excluded) {
        List<List<Integer>> owners = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) owners.add(new ArrayList<>());
        int[] assignment = new int[ingredients.size()];
        Arrays.fill(assignment, -1);
        for (int i = 0; i < ingredients.size(); i++) {
            if (!augment(i, ingredients, materials, excluded, new BitSet(), owners, assignment)) return new int[0];
        }
        return assignment;
    }

    private static boolean augment(int ingredient, List<Ingredient> ingredients, List<ItemStack> materials,
                                   BitSet excluded, BitSet seen, List<List<Integer>> owners, int[] assignment) {
        for (int m = 0; m < materials.size(); m++) {
            ItemStack stack = materials.get(m);
            if (excluded.get(m) || seen.get(m) || stack.isEmpty() || !ingredients.get(ingredient).test(stack)) continue;
            seen.set(m);
            List<Integer> assigned = owners.get(m);
            if (assigned.size() < stack.getCount()) {
                assigned.add(ingredient); assignment[ingredient] = m; return true;
            }
            for (int previous : List.copyOf(assigned)) {
                if (augment(previous, ingredients, materials, excluded, seen, owners, assignment)) {
                    assigned.remove(Integer.valueOf(previous));
                    assigned.add(ingredient); assignment[ingredient] = m; return true;
                }
            }
        }
        return false;
    }
}
