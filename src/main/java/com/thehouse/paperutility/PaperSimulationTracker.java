package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

import java.util.*;

public final class PaperSimulationTracker {
    
    private static final Map<Integer, ItemStack> ORIGINAL_STACKS = new HashMap<>();

    
    private static final Map<Integer, ItemStack> LAST_VISUAL_STACKS = new HashMap<>();

    private static final Map<Integer, Integer> CHOSEN_DISPLAY_VALUE = new HashMap<>();
    private static final Set<Integer> PROCESSED_LEFT = new HashSet<>();
    private static final Set<Integer> PROCESSED_RIGHT = new HashSet<>();
    
    private static final Set<Integer> PRIMED_RIGHT_BASELINES = new HashSet<>();
    
    private static final Set<Integer> MASKED_GROUND_ENTITIES = new HashSet<>();
    
    private static boolean immediateLeftReady = false;
    
    private record PaperRound(int leftEntityId, int leftValue, WinningSide winningSide) {}
    private static final Deque<PaperRound> UNPAIRED_ROUNDS = new ArrayDeque<>();
    private static final Map<Integer, PaperRound> RIGHT_ROUNDS = new HashMap<>();

    
    
    
    
    private static int quietStaleTicks = 0;

    private PaperSimulationTracker() {}


    





    public static void observeEntityImmediately(MinecraftClient client, ItemEntity entity) {
        if (client == null || client.player == null || client.world == null || entity == null) {
            return;
        }

        
        if (client.player.squaredDistanceTo(entity) > (32.0 * 32.0)) {
            return;
        }

        int entityId = entity.getId();
        refreshOriginal(entityId, entity.getStack());
        ItemStack original = ORIGINAL_STACKS.get(entityId);
        if (original == null) {
            return;
        }

        
        
        
        if (InventoryPickupMask.matchesMaskedGroundStack(original)) {
            ItemStack groundVisual = InventoryPickupMask.groundReplacement(original);
            if (groundVisual != null) {
                MASKED_GROUND_ENTITIES.add(entityId);
                InventoryPickupMask.markGroundCarrySeen();
                setVisual(entityId, entity, groundVisual);
                return;
            }
        }

        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(original);
        if (parsed == null) {
            return;
        }

        if (parsed.side() == PaperItemParser.Side.LEFT) {
            processLeft(entityId, parsed.value());
            immediateLeftReady = true;
            
            
            resolveObservedRights(client);
        } else {
            
            
            
            
            
            
            primeRightBaseline(client, entityId);
            if (immediateLeftReady) {
                handleRight(entity, entityId, original, parsed.value());
            } else if (ModState.isSimulationEnabled()) {
                setVisual(entityId, entity, ItemStack.EMPTY);
            }
        }
    }

    private static void processLeft(int entityId, int value) {
        if (PROCESSED_LEFT.add(entityId)) {
            
            
            boolean pendingPrimedRight = PRIMED_RIGHT_BASELINES.stream()
                    .anyMatch(id -> !PROCESSED_RIGHT.contains(id));
            if (!pendingPrimedRight) {
                InventoryPickupMask.clear();
            }
            
            
            PaperRound round = new PaperRound(entityId, value, ModState.getWinningSide());
            
            
            
            UNPAIRED_ROUNDS.clear();
            UNPAIRED_ROUNDS.addLast(round);
            ModState.setLastLeft(value);
            ModState.setLastActualRight(-1);
            ModState.setLastDisplayedRight(-1);
            ModState.setLastActualRightStack(ItemStack.EMPTY);
            CHOSEN_DISPLAY_VALUE.clear();
        }
    }

    private static void primeRightBaseline(MinecraftClient client, int entityId) {
        if (PRIMED_RIGHT_BASELINES.add(entityId)) {
            InventoryPickupMask.captureBaseline(client);
        }
    }

    private static void resolveObservedRights(MinecraftClient client) {
        if (client == null || client.world == null) {
            return;
        }

        for (Map.Entry<Integer, ItemStack> entry : new HashMap<>(ORIGINAL_STACKS).entrySet()) {
            int entityId = entry.getKey();
            Entity tracked = client.world.getEntityById(entityId);
            if (!(tracked instanceof ItemEntity itemEntity)) {
                continue;
            }

            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(entry.getValue());
            if (parsed == null || parsed.side() != PaperItemParser.Side.RIGHT) {
                continue;
            }

            primeRightBaseline(client, entityId);
            handleRight(itemEntity, entityId, entry.getValue(), parsed.value());
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clearState();
            return;
        }

        
        
        
        PaperItemCatalog.learnFromOpenDropper(client);

        Box scanBox = client.player.getBoundingBox().expand(32.0);
        List<ItemEntity> entities = client.world.getEntitiesByClass(
                ItemEntity.class,
                scanBox,
                entity -> true
        );

        Set<Integer> alive = new HashSet<>();

        
        
        for (ItemEntity entity : entities) {
            int entityId = entity.getId();
            alive.add(entityId);
            refreshOriginal(entityId, entity.getStack());
        }

        boolean sawMaskedGround = false;
        for (ItemEntity entity : entities) {
            int entityId = entity.getId();
            ItemStack original = ORIGINAL_STACKS.get(entityId);
            if (original == null) {
                continue;
            }
            if (MASKED_GROUND_ENTITIES.contains(entityId)
                    || InventoryPickupMask.matchesMaskedGroundStack(original)) {
                ItemStack groundVisual = InventoryPickupMask.groundReplacement(original);
                if (groundVisual != null) {
                    MASKED_GROUND_ENTITIES.add(entityId);
                    InventoryPickupMask.markGroundCarrySeen();
                    setVisual(entityId, entity, groundVisual);
                    sawMaskedGround = true;
                }
            }
        }
        if (!sawMaskedGround) {
            InventoryPickupMask.markGroundCarryNotSeen();
        }

        
        
        
        for (ItemEntity entity : entities) {
            int entityId = entity.getId();
            ItemStack original = ORIGINAL_STACKS.get(entityId);
            if (original == null) {
                continue;
            }

            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(original);
            if (parsed == null || parsed.side() != PaperItemParser.Side.LEFT) {
                continue;
            }

            processLeft(entityId, parsed.value());
        }

        
        
        for (ItemEntity entity : entities) {
            int entityId = entity.getId();
            if (MASKED_GROUND_ENTITIES.contains(entityId)) {
                continue;
            }
            ItemStack original = ORIGINAL_STACKS.get(entityId);
            if (original == null) {
                continue;
            }

            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(original);
            if (parsed == null || parsed.side() != PaperItemParser.Side.RIGHT) {
                continue;
            }

            primeRightBaseline(client, entityId);
            handleRight(entity, entityId, original, parsed.value());
        }

        cleanup(client, alive);

        
        
        
        InventoryPickupMask.reconcile(client);
        autoRecoverIfSuspicious(client);
    }

    private static void refreshOriginal(int entityId, ItemStack current) {
        ItemStack lastVisual = LAST_VISUAL_STACKS.get(entityId);

        if (!ORIGINAL_STACKS.containsKey(entityId)) {
            ORIGINAL_STACKS.put(entityId, current.copy());
            return;
        }

        
        
        if (lastVisual == null || !ItemStack.areEqual(current, lastVisual)) {
            ORIGINAL_STACKS.put(entityId, current.copy());
            LAST_VISUAL_STACKS.remove(entityId);
        }
    }

    private static void handleRight(
            ItemEntity entity,
            int entityId,
            ItemStack original,
            int actualRight
    ) {
        if (!PROCESSED_RIGHT.contains(entityId)) {
            PaperRound round = RIGHT_ROUNDS.get(entityId);
            if (round == null) {
                round = UNPAIRED_ROUNDS.pollFirst();
                if (round == null) {
                    
                    
                    if (ModState.isSimulationEnabled()) {
                        setVisual(entityId, entity, ItemStack.EMPTY);
                    } else {
                        restore(entity, original);
                    }
                    return;
                }
                RIGHT_ROUNDS.put(entityId, round);
            }

            ModState.setLastLeft(round.leftValue()); 
            ModState.setLastActualRight(actualRight);
            ModState.setLastActualRightStack(original);
            primeRightBaseline(MinecraftClient.getInstance(), entityId);

            int displayed = PaperGameResolver.chooseDisplayedRight(
                    round.leftValue(), actualRight, round.winningSide());

            CHOSEN_DISPLAY_VALUE.put(entityId, displayed);
            ModState.setLastDisplayedRight(displayed);
            PROCESSED_RIGHT.add(entityId);
            immediateLeftReady = false;
        }

        if (!ModState.isSimulationEnabled()) {
            restore(entity, original);
            return;
        }

        int displayed = CHOSEN_DISPLAY_VALUE.getOrDefault(entityId, actualRight);

        if (displayed == actualRight) {
            restore(entity, original);
            return;
        }

        ItemStack visual = PaperItemCatalog.createRightStack(displayed);
        visual.setCount(original.getCount());
        setVisual(entityId, entity, visual);
    }

    private static void setVisual(int entityId, ItemEntity entity, ItemStack visual) {
        LAST_VISUAL_STACKS.put(entityId, visual.copy());
        if (!ItemStack.areEqual(entity.getStack(), visual)) {
            entity.setStack(visual);
        }
    }

    public static void restoreAll(MinecraftClient client) {
        if (client.world == null) {
            return;
        }

        for (Map.Entry<Integer, ItemStack> entry : ORIGINAL_STACKS.entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity instanceof ItemEntity itemEntity) {
                itemEntity.setStack(entry.getValue().copy());
            }
        }

        LAST_VISUAL_STACKS.clear();
        InventoryPickupMask.clear();
    }

    private static void restore(ItemEntity entity, ItemStack original) {
        LAST_VISUAL_STACKS.remove(entity.getId());
        if (!ItemStack.areEqual(entity.getStack(), original)) {
            entity.setStack(original.copy());
        }
    }

    private static void cleanup(MinecraftClient client, Set<Integer> alive) {
        
        
        
        for (Integer id : new HashSet<>(PROCESSED_RIGHT)) {
            if (!alive.contains(id)) {
                int actual = ModState.getLastActualRight();
                int displayed = CHOSEN_DISPLAY_VALUE.getOrDefault(id, ModState.getLastDisplayedRight());
                InventoryPickupMask.detectPickup(client, actual, displayed);
            }
        }
        ORIGINAL_STACKS.keySet().removeIf(id -> !alive.contains(id));
        LAST_VISUAL_STACKS.keySet().removeIf(id -> !alive.contains(id));
        CHOSEN_DISPLAY_VALUE.keySet().removeIf(id -> !alive.contains(id));
        RIGHT_ROUNDS.keySet().removeIf(id -> !alive.contains(id));
        PROCESSED_LEFT.removeIf(id -> !alive.contains(id));
        PROCESSED_RIGHT.removeIf(id -> !alive.contains(id));
        PRIMED_RIGHT_BASELINES.removeIf(id -> !alive.contains(id));
        MASKED_GROUND_ENTITIES.removeIf(id -> !alive.contains(id));
    }

    




    private static void autoRecoverIfSuspicious(MinecraftClient client) {
        if (!ModState.isSimulationEnabled()) {
            quietStaleTicks = 0;
            return;
        }

        int actual = ModState.getLastActualRight();
        int displayed = ModState.getLastDisplayedRight();

        boolean processedRight = !PROCESSED_RIGHT.isEmpty();
        boolean chosenRight = !CHOSEN_DISPLAY_VALUE.isEmpty();
        boolean pickupMask = InventoryPickupMask.isActive();

        
        
        boolean impossibleRightState =
                (processedRight && (actual < 1 || actual > 9 || displayed < 1 || displayed > 9))
                || (processedRight && !chosenRight)
                || (!processedRight && chosenRight)
                || (pickupMask && (actual < 1 || actual > 9 || displayed < 1 || displayed > 9));

        if (impossibleRightState) {
            resetTransientState(client);
            quietStaleTicks = 0;
            return;
        }

        boolean relevantWorldEntity = false;
        for (ItemStack stack : ORIGINAL_STACKS.values()) {
            if (PaperItemParser.parse(stack) != null) {
                relevantWorldEntity = true;
                break;
            }
        }

        boolean hasRoundBookkeeping = ModState.getLastLeft() != -1
                || actual != -1
                || displayed != -1
                || !PROCESSED_LEFT.isEmpty()
                || processedRight
                || chosenRight
                || !LAST_VISUAL_STACKS.isEmpty();

        
        
        if (hasRoundBookkeeping && !relevantWorldEntity && !pickupMask) {
            quietStaleTicks++;
            if (quietStaleTicks >= 20) {
                resetTransientState(client);
                quietStaleTicks = 0;
            }
        } else {
            quietStaleTicks = 0;
        }
    }

    





    public static void resetTransientState(MinecraftClient client) {
        if (client != null && client.world != null) {
            for (Map.Entry<Integer, ItemStack> entry : ORIGINAL_STACKS.entrySet()) {
                Entity entity = client.world.getEntityById(entry.getKey());
                if (entity instanceof ItemEntity itemEntity) {
                    itemEntity.setStack(entry.getValue().copy());
                }
            }
        }

        clearState();
    }

    public static void clearState() {
        ORIGINAL_STACKS.clear();
        LAST_VISUAL_STACKS.clear();
        CHOSEN_DISPLAY_VALUE.clear();
        PROCESSED_LEFT.clear();
        PROCESSED_RIGHT.clear();
        PRIMED_RIGHT_BASELINES.clear();
        MASKED_GROUND_ENTITIES.clear();
        UNPAIRED_ROUNDS.clear();
        RIGHT_ROUNDS.clear();
        immediateLeftReady = false;
        InventoryPickupMask.clear();
        ModState.clearRound();
        quietStaleTicks = 0;
    }
}
