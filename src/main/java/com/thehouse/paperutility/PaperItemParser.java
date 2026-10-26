package com.thehouse.paperutility;

import net.minecraft.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaperItemParser {
    private static final Pattern LEFT = Pattern.compile("^([1-9])\\.1$");
    private static final Pattern RIGHT = Pattern.compile("^([1-9])$");

    private PaperItemParser() {}

    public static ParsedItem parse(ItemStack stack) {
        ParsedItem named = parseNumberedName(stack);
        if (named == null) {
            return null;
        }

        return PaperItemCatalog.matchesExpectedItem(stack, named.value()) ? named : null;
    }

    




    public static ParsedItem parseNumberedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String name = stack.getName().getString();

        Matcher leftMatcher = LEFT.matcher(name);
        if (leftMatcher.matches()) {
            int value = Integer.parseInt(leftMatcher.group(1));
            return new ParsedItem(Side.LEFT, value);
        }

        Matcher rightMatcher = RIGHT.matcher(name);
        if (rightMatcher.matches()) {
            int value = Integer.parseInt(rightMatcher.group(1));
            return new ParsedItem(Side.RIGHT, value);
        }

        return null;
    }

    public enum Side {
        LEFT,
        RIGHT
    }

    public record ParsedItem(Side side, int value) {}
}
