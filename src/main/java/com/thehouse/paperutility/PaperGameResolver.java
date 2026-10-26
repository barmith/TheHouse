package com.thehouse.paperutility;

import java.util.concurrent.ThreadLocalRandom;

public final class PaperGameResolver {
    private PaperGameResolver() {}

    






    public static int chooseDisplayedRight(int left, int actualRight, WinningSide side) {
        if (left < 1 || left > 9 || actualRight < 1 || actualRight > 9) {
            return actualRight;
        }

        return switch (side) {
            case RIGHT -> {
                if (actualRight >= left) {
                    yield actualRight;
                }
                yield ThreadLocalRandom.current().nextInt(left, 10);
            }

            case LEFT -> {
                if (actualRight <= left) {
                    yield actualRight;
                }
                yield ThreadLocalRandom.current().nextInt(1, left + 1);
            }
        };
    }
}
