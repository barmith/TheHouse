package com.thehouse.paperutility;

import java.util.*;


public final class BlackjackRigging {
    private BlackjackRigging() {}

    












    public static BlackjackCard chooseNextMaskedCard(List<BlackjackCard> lockedPrefix, int viewerTotal) {
        BlackjackCard[] available = BlackjackCardCatalog.availableCards();
        if (available.length == 0) return null;

        List<BlackjackCard> prefix = lockedPrefix == null ? List.of() : lockedPrefix;
        int prefixScore = BlackjackState.score(prefix);
        List<BlackjackCard> legal = new ArrayList<>();
        for (BlackjackCard candidate : available) {
            if (scoreAfter(prefix, candidate) <= 21) legal.add(candidate);
        }
        if (legal.isEmpty()) return null;

        
        if (prefix.size() == 1) {
            BlackjackCard first = prefix.get(0);
            int firstValue = first == null ? -1 : first.rawValue();
            Map<Integer, List<BlackjackCard>> immediateWins = winningCandidatesByTotal(prefix, legal, viewerTotal, true);
            if (firstValue >= 8 && firstValue <= 10 && viewerTotal < 21
                    && !immediateWins.isEmpty()
                    && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.32d) {
                return randomByResultingTotal(immediateWins);
            }

            Map<Integer, List<BlackjackCard>> staged = new LinkedHashMap<>();
            for (BlackjackCard candidate : legal) {
                int score = scoreAfter(prefix, candidate);
                if (score < 16 && canReachExact21Next(prefix, candidate, available))
                    staged.computeIfAbsent(score, k -> new ArrayList<>()).add(candidate);
            }
            if (!staged.isEmpty()) return randomByResultingTotal(staged);

            Map<Integer, List<BlackjackCard>> flexible = new LinkedHashMap<>();
            for (BlackjackCard candidate : legal) {
                int score = scoreAfter(prefix, candidate);
                if (score < 16) flexible.computeIfAbsent(score, k -> new ArrayList<>()).add(candidate);
            }
            if (!flexible.isEmpty()) return randomByResultingTotal(flexible);

            Map<Integer, List<BlackjackCard>> nonTrap = new LinkedHashMap<>();
            for (BlackjackCard candidate : legal) {
                int score = scoreAfter(prefix, candidate);
                if (score != 16 && score != 17) nonTrap.computeIfAbsent(score, k -> new ArrayList<>()).add(candidate);
            }
            if (!nonTrap.isEmpty()) return randomByResultingTotal(nonTrap);
        }

        
        if (prefix.size() == 2) {
            if (viewerTotal == 21) {
                List<BlackjackCard> exact21 = candidatesForScore(prefix, legal, 21);
                if (!exact21.isEmpty()) return randomChoice(exact21);
            } else {
                Map<Integer, List<BlackjackCard>> winners = winningCandidatesByTotal(prefix, legal, viewerTotal, false);
                if (!winners.isEmpty()) return randomByResultingTotal(winners);
            }
            if (prefixScore < 17) {
                List<BlackjackCard> staged = new ArrayList<>();
                for (BlackjackCard candidate : legal) {
                    int score = scoreAfter(prefix, candidate);
                    if (score < 17 && canReachExact21Next(prefix, candidate, available)) staged.add(candidate);
                }
                if (!staged.isEmpty()) return randomChoice(staged);
            }
            List<BlackjackCard> exact21 = candidatesForScore(prefix, legal, 21);
            if (!exact21.isEmpty()) return randomChoice(exact21);
        } else if (prefix.size() >= 3) {
            List<BlackjackCard> exact21 = candidatesForScore(prefix, legal, 21);
            if (!exact21.isEmpty()) return randomChoice(exact21);
            if (viewerTotal < 21) {
                Map<Integer, List<BlackjackCard>> winners = winningCandidatesByTotal(prefix, legal, viewerTotal, false);
                if (!winners.isEmpty()) return randomByResultingTotal(winners);
            }
        }

        BlackjackCard best = null; int bestScore = -1;
        for (BlackjackCard candidate : legal) {
            int score = scoreAfter(prefix, candidate);
            if (best == null || score > bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }

    private static Map<Integer, List<BlackjackCard>> winningCandidatesByTotal(
            List<BlackjackCard> prefix, List<BlackjackCard> legal, int viewerTotal, boolean allow21) {
        Map<Integer, List<BlackjackCard>> winners = new LinkedHashMap<>();
        for (BlackjackCard candidate : legal) {
            int score = scoreAfter(prefix, candidate);
            if (score > viewerTotal && (allow21 ? score <= 21 : score < 21))
                winners.computeIfAbsent(score, k -> new ArrayList<>()).add(candidate);
        }
        return winners;
    }

    private static BlackjackCard randomByResultingTotal(Map<Integer, List<BlackjackCard>> byTotal) {
        List<Integer> totals = new ArrayList<>(byTotal.keySet());
        int total = totals.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(totals.size()));
        return randomChoice(byTotal.get(total));
    }

    private static List<BlackjackCard> candidatesForScore(
            List<BlackjackCard> prefix,
            List<BlackjackCard> candidates,
            int targetScore
    ) {
        List<BlackjackCard> matches = new ArrayList<>();
        for (BlackjackCard candidate : candidates) {
            if (scoreAfter(prefix, candidate) == targetScore) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static boolean canReachExact21Next(
            List<BlackjackCard> prefix,
            BlackjackCard stagedCard,
            BlackjackCard[] available
    ) {
        List<BlackjackCard> stagedPrefix = new ArrayList<>(prefix);
        stagedPrefix.add(stagedCard);

        for (BlackjackCard followUp : available) {
            if (scoreAfter(stagedPrefix, followUp) == 21) {
                return true;
            }
        }
        return false;
    }

    private static int scoreAfter(List<BlackjackCard> prefix, BlackjackCard candidate) {
        List<BlackjackCard> test = new ArrayList<>(prefix);
        test.add(candidate);
        return BlackjackState.score(test);
    }

    private static BlackjackCard randomChoice(List<BlackjackCard> candidates) {
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(candidates.size()));
    }

}
