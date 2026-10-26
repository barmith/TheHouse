package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

import java.util.*;


public final class BlackjackTracker {
    private static final Map<Integer, SourceState> ITEM_SOURCES = new HashMap<>();
    private static final Map<Integer, SourceState> FRAME_SOURCES = new HashMap<>();
    private static final Map<Integer, Map<EquipmentSlot, SourceState>> STAND_SOURCES = new HashMap<>();

    private static final Map<Integer, ItemStack> ITEM_ORIGINALS = new HashMap<>();
    private static final Map<Integer, ItemStack> ITEM_LAST_VISUAL = new HashMap<>();
    private static final Map<Integer, Integer> ITEM_HOST_INDEX = new HashMap<>();

    
    
    private static final Map<Integer, Integer> FRAME_HOST_INDEX = new HashMap<>();
    
    
    
    private static final Map<Integer, ItemStack> FRAME_ORIGINALS = new HashMap<>();
    private static final Map<Integer, ItemStack> FRAME_LAST_VISUAL = new HashMap<>();
    
    
    
    private static final Map<Integer, BlackjackCard> FRAME_DISPLAY_CARD = new HashMap<>();
    
    
    private static final Set<Integer> FRAME_EXPLICIT_OWNERSHIP = new HashSet<>();

    private static final Map<StandKey, ItemStack> STAND_ORIGINALS = new HashMap<>();
    private static final Map<StandKey, ItemStack> STAND_LAST_VISUAL = new HashMap<>();
    private static final Map<StandKey, Integer> STAND_HOST_INDEX = new HashMap<>();

    private static final Deque<RecentDeal> RECENT_DEALS = new ArrayDeque<>();
    
    
    private static final Deque<PendingFrameRelease> PENDING_FRAME_RELEASES = new ArrayDeque<>();
    private static long tickCounter = 0;
    private static boolean displayBaselineInitialized = false;

    private BlackjackTracker() {}

    
    public static void observeItemImmediately(MinecraftClient client, ItemEntity entity) {
        if (!usable(client) || entity == null || client.player == null) {
            return;
        }
        if (client.player.squaredDistanceTo(entity) > 32.0 * 32.0) {
            return;
        }
        observeItemSource(client, entity, true);
    }

    public static void tick(MinecraftClient client) {
        tickCounter++;
        if (!usable(client)) {
            restoreAll(client);
            clearTransient();
            return;
        }

        BlackjackCardCatalog.learnFromOpenDropper(client);
        BlackjackMasking.tick(client);

        Box box = client.player.getBoundingBox().expand(32.0);
        List<ItemEntity> items = client.world.getEntitiesByClass(ItemEntity.class, box, e -> true);
        List<ItemFrameEntity> frames = client.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);
        List<ArmorStandEntity> stands = client.world.getEntitiesByClass(ArmorStandEntity.class, box, e -> true);

        Set<Integer> aliveItems = new HashSet<>();
        for (ItemEntity item : items) {
            aliveItems.add(item.getId());
            observeItemSource(client, item, false);
            applyItemVisual(item);
        }

        if (!displayBaselineInitialized) {
            primeExistingDisplays(frames, stands);
            displayBaselineInitialized = true;
        }

        Set<Integer> aliveFrames = new HashSet<>();
        for (ItemFrameEntity frame : frames) {
            aliveFrames.add(frame.getId());
            observeFrameSource(frame);
            applyFrameVisual(frame);
        }

        Set<StandKey> aliveStandSlots = new HashSet<>();
        for (ArmorStandEntity stand : stands) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = stand.getEquippedStack(slot);
                BlackjackCard card = BlackjackCard.fromStack(stack);
                StandKey key = new StandKey(stand.getId(), slot);
                if (card != null || STAND_SOURCES.containsKey(stand.getId())) {
                    aliveStandSlots.add(key);
                    observeStandSource(stand, slot, stack);
                    applyStandVisual(stand, slot, key);
                }
            }
        }

        cleanupMissingItems(client, aliveItems);
        cleanupMissingFrames(aliveFrames);
        cleanupMissingStandSlots(aliveStandSlots);
        trimRecentDeals();
    }


    private static void primeExistingDisplays(List<ItemFrameEntity> frames, List<ArmorStandEntity> stands) {
        for (ItemFrameEntity frame : frames) {
            ItemStack stack = frame.getHeldItemStack();
            BlackjackCard card = BlackjackCard.fromStack(stack);
            if (card != null) {
                BlackjackCardCatalog.observe(stack);
                FRAME_ORIGINALS.put(frame.getId(), stack.copy());
                FRAME_SOURCES.put(frame.getId(), new SourceState(card, false, -1, tickCounter));
            }
        }
        for (ArmorStandEntity stand : stands) {
            Map<EquipmentSlot, SourceState> sourceSlots = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = stand.getEquippedStack(slot);
                BlackjackCard card = BlackjackCard.fromStack(stack);
                if (card == null) {
                    continue;
                }
                BlackjackCardCatalog.observe(stack);
                StandKey key = new StandKey(stand.getId(), slot);
                STAND_ORIGINALS.put(key, stack.copy());
                sourceSlots.put(slot, new SourceState(card, false, -1, tickCounter));
            }
            if (!sourceSlots.isEmpty()) {
                STAND_SOURCES.put(stand.getId(), sourceSlots);
            }
        }
    }

    private static void observeItemSource(MinecraftClient client, ItemEntity entity, boolean immediate) {
        int id = entity.getId();
        refreshOriginal(ITEM_ORIGINALS, ITEM_LAST_VISUAL, id, entity.getStack());
        ItemStack real = ITEM_ORIGINALS.get(id);
        BlackjackCard card = BlackjackCard.fromStack(real);
        if (card == null) {
            return;
        }
        BlackjackCardCatalog.observe(real);

        SourceState old = ITEM_SOURCES.get(id);
        if (old != null && old.card == card && (old.host || old.handIndex >= 0)) {
            return;
        }

        
        
        
        PendingFrameRelease releasedFrame = claimPendingFrameRelease(entity, card);
        if (releasedFrame != null) {
            
            
            
            SourceState inherited = new SourceState(card, true, releasedFrame.hostIndex, tickCounter);
            ITEM_SOURCES.put(id, inherited);
            ITEM_HOST_INDEX.put(id, releasedFrame.hostIndex);
            BlackjackMasking.capturePendingPickup(client, id, card, true, releasedFrame.hostIndex);
            return;
        }

        
        
        
        if (old != null && old.card == card) {
            return;
        }

        if (BlackjackState.isMaskLocked()) {
            
            
            ITEM_SOURCES.put(id, new SourceState(card, false, -1, tickCounter));
            ITEM_HOST_INDEX.remove(id);
            return;
        }

        BlackjackState.DealAssignment assignment = BlackjackState.registerDeal(card);
        ITEM_SOURCES.put(id, new SourceState(card, assignment.host(), assignment.handIndex(), tickCounter));
        RECENT_DEALS.addLast(new RecentDeal(card, assignment.host(), assignment.handIndex(), tickCounter));

        if (assignment.host()) {
            ITEM_HOST_INDEX.put(id, assignment.handIndex());
        } else {
            ITEM_HOST_INDEX.remove(id);
        }
        
        
        BlackjackMasking.capturePendingPickup(client, id, card, assignment.host(), assignment.handIndex());
    }

    private static void observeFrameSource(ItemFrameEntity frame) {
        int id = frame.getId();
        
        
        
        refreshOriginal(FRAME_ORIGINALS, FRAME_LAST_VISUAL, id, frame.getHeldItemStack());
        ItemStack real = FRAME_ORIGINALS.get(id);
        BlackjackCard card = BlackjackCard.fromStack(real);
        SourceState old = FRAME_SOURCES.get(id);
        if (card == null) {
            FRAME_SOURCES.remove(id);
            FRAME_HOST_INDEX.remove(id);
            FRAME_DISPLAY_CARD.remove(id);
            FRAME_EXPLICIT_OWNERSHIP.remove(id);
            FRAME_ORIGINALS.remove(id);
            FRAME_LAST_VISUAL.remove(id);
            return;
        }
        BlackjackCardCatalog.observe(real);

        
        
        
        BlackjackMasking.FramePlacementClaim explicit = BlackjackMasking.consumeFramePlacement(id, card);
        if (explicit != null) {
            SourceState bound = new SourceState(card, explicit.host(), explicit.hostIndex(), tickCounter);
            FRAME_SOURCES.put(id, bound);
            FRAME_EXPLICIT_OWNERSHIP.add(id);
            if (explicit.host()) {
                FRAME_HOST_INDEX.put(id, explicit.hostIndex());
                if (explicit.hostIndex() >= 1 && explicit.displayCard() != null) {
                    FRAME_DISPLAY_CARD.put(id, explicit.displayCard());
                } else {
                    FRAME_DISPLAY_CARD.remove(id);
                }
            } else {
                FRAME_HOST_INDEX.remove(id);
                FRAME_DISPLAY_CARD.remove(id);
            }
            return;
        }

        
        
        
        if (old != null && old.card == card && FRAME_EXPLICIT_OWNERSHIP.contains(id)) {
            return;
        }

        
        if (old != null && old.card == card && old.host) {
            return;
        }

        Integer claimed = BlackjackMasking.claimHostIndexForWorldCard(card);
        if (claimed != null) {
            SourceState inherited = new SourceState(card, true, claimed, tickCounter);
            FRAME_SOURCES.put(id, inherited);
            FRAME_HOST_INDEX.put(id, claimed);
            BlackjackCard assigned = BlackjackState.displayedCardForHostIndex(claimed);
            if (claimed >= 2 && assigned != null) {
                FRAME_DISPLAY_CARD.put(id, assigned);
            } else {
                FRAME_DISPLAY_CARD.remove(id);
            }
            return;
        }

        if (old != null && old.card == card) {
            return;
        }

        SourceState inherited = inheritRecent(card);
        if (inherited != null && inherited.host) {
            
            
            inherited = null;
        }
        if (inherited == null) {
            inherited = new SourceState(card, false, -1, tickCounter);
        }
        FRAME_SOURCES.put(id, inherited);
        FRAME_HOST_INDEX.remove(id);
        FRAME_DISPLAY_CARD.remove(id);
    }

    private static void observeStandSource(ArmorStandEntity stand, EquipmentSlot slot, ItemStack current) {
        int id = stand.getId();
        StandKey key = new StandKey(id, slot);
        refreshOriginal(STAND_ORIGINALS, STAND_LAST_VISUAL, key, current);
        ItemStack real = STAND_ORIGINALS.get(key);
        BlackjackCard card = BlackjackCard.fromStack(real);

        Map<EquipmentSlot, SourceState> slots = STAND_SOURCES.computeIfAbsent(id, ignored -> new EnumMap<>(EquipmentSlot.class));
        SourceState old = slots.get(slot);
        if (card == null) {
            slots.remove(slot);
            STAND_HOST_INDEX.remove(key);
            if (slots.isEmpty()) {
                STAND_SOURCES.remove(id);
            }
            return;
        }
        BlackjackCardCatalog.observe(real);
        if (old != null && old.card == card) {
            return;
        }

        SourceState inherited = inheritRecent(card);
        if (inherited == null) {
            Integer claimed = BlackjackMasking.claimHostIndexForWorldCard(card);
            if (claimed != null) {
                inherited = new SourceState(card, true, claimed, tickCounter);
            }
        }
        if (inherited == null) {
            BlackjackState.DealAssignment assignment = BlackjackState.registerDeal(card);
            inherited = new SourceState(card, assignment.host(), assignment.handIndex(), tickCounter);
            RECENT_DEALS.addLast(new RecentDeal(card, assignment.host(), assignment.handIndex(), tickCounter));
        }
        slots.put(slot, inherited);
        if (inherited.host) {
            STAND_HOST_INDEX.put(key, inherited.handIndex);
        } else {
            STAND_HOST_INDEX.remove(key);
        }
    }

    private static SourceState inheritRecent(BlackjackCard card) {
        trimRecentDeals();
        Iterator<RecentDeal> iterator = RECENT_DEALS.descendingIterator();
        while (iterator.hasNext()) {
            RecentDeal deal = iterator.next();
            if (deal.card == card && tickCounter - deal.tick <= 40) {
                return new SourceState(card, deal.host, deal.handIndex, tickCounter);
            }
        }
        return null;
    }

    




    public static void captureFrameRemoval(ItemFrameEntity frame) {
        if (frame == null || !BlackjackState.isEnabled()) {
            return;
        }
        
        
        
        if (!frame.getHeldItemStack().isEmpty()) {
            observeFrameSource(frame);
        }
        int frameId = frame.getId();
        Integer hostIndex = FRAME_HOST_INDEX.get(frameId);
        if (hostIndex == null || hostIndex < 1) {
            return;
        }
        ItemStack real = FRAME_ORIGINALS.get(frameId);
        BlackjackCard actual = BlackjackCard.fromStack(real);
        BlackjackCard display = FRAME_DISPLAY_CARD.get(frameId);
        if (display == null) {
            display = BlackjackState.displayedCardForHostIndex(hostIndex);
        }
        if (actual == null || display == null) {
            return;
        }

        
        
        PENDING_FRAME_RELEASES.removeIf(p -> p.frameId == frameId);
        PENDING_FRAME_RELEASES.addLast(new PendingFrameRelease(
                frameId, actual, hostIndex, display,
                frame.getX(), frame.getY(), frame.getZ(), 0));
    }

    private static PendingFrameRelease claimPendingFrameRelease(ItemEntity entity, BlackjackCard card) {
        if (entity == null || card == null) {
            return null;
        }

        






        PendingFrameRelease best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        SourceState previouslyObserved = ITEM_SOURCES.get(entity.getId());
        for (PendingFrameRelease pending : PENDING_FRAME_RELEASES) {
            
            
            if (previouslyObserved != null && previouslyObserved.tick < pending.createdAtTick) {
                continue;
            }
            if (pending.actual != card) {
                continue;
            }
            double dx = entity.getX() - pending.x;
            double dy = entity.getY() - pending.y;
            double dz = entity.getZ() - pending.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = pending;
            }
        }

        
        
        if (best == null || bestDistanceSq > 16.0) {
            return null;
        }
        PENDING_FRAME_RELEASES.remove(best);
        return best;
    }

    private static void applyItemVisual(ItemEntity entity) {
        Integer hostIndex = ITEM_HOST_INDEX.get(entity.getId());
        if (hostIndex == null) {
            restoreItem(entity);
            return;
        }
        ItemStack real = ITEM_ORIGINALS.get(entity.getId());
        ItemStack visual = replacement(real, hostIndex);
        if (visual == null) {
            restoreItem(entity);
            return;
        }
        entity.setStack(visual.copy());
        ITEM_LAST_VISUAL.put(entity.getId(), visual.copy());
    }

    






    private static void applyFrameVisual(ItemFrameEntity frame) {
        if (frame == null) {
            return;
        }
        int id = frame.getId();
        Integer hostIndex = FRAME_HOST_INDEX.get(id);
        if (!BlackjackState.isEnabled() || hostIndex == null || hostIndex < 1) {
            restoreFrame(frame);
            return;
        }

        ItemStack real = FRAME_ORIGINALS.get(id);
        if (real == null || real.isEmpty()) {
            return;
        }
        BlackjackCard actual = BlackjackCard.fromStack(real);
        BlackjackCard display = FRAME_DISPLAY_CARD.get(id);
        if (display == null) {
            display = BlackjackState.displayedCardForHostIndex(hostIndex);
        }
        if (actual == null || display == null || display == actual) {
            restoreFrame(frame);
            return;
        }

        ItemStack visual = BlackjackCardCatalog.create(display, real.getCount());
        if (visual.isEmpty()) {
            restoreFrame(frame);
            return;
        }
        if (!ItemStack.areEqual(frame.getHeldItemStack(), visual)) {
            frame.setHeldItemStack(visual.copy());
        }
        FRAME_LAST_VISUAL.put(id, visual.copy());
    }

    




    public static ItemStack frameRenderStack(ItemFrameEntity frame, ItemStack real) {
        if (frame == null || real == null || real.isEmpty() || !BlackjackState.isEnabled()) {
            return real;
        }

        BlackjackCard actual = BlackjackCard.fromStack(real);
        if (actual == null) {
            return real;
        }

        int frameId = frame.getId();

        
        
        
        
        
        BlackjackMasking.FramePlacementClaim placement =
                BlackjackMasking.peekFramePlacement(frameId, actual);
        if (placement != null) {
            if (!placement.host() || placement.hostIndex() < 1) {
                return real;
            }
            BlackjackCard capturedDisplay = placement.displayCard();
            if (capturedDisplay == null || capturedDisplay == actual) {
                return real;
            }
            ItemStack visual = BlackjackCardCatalog.create(capturedDisplay, real.getCount());
            return visual.isEmpty() ? real : visual;
        }

        Integer hostIndex = FRAME_HOST_INDEX.get(frameId);
        if (hostIndex == null || hostIndex < 1) {
            return real;
        }

        
        
        BlackjackCard display = FRAME_DISPLAY_CARD.get(frameId);
        if (display == null) {
            display = BlackjackState.displayedCardForHostIndex(hostIndex);
        }
        if (display == null || display == actual) {
            return real;
        }
        ItemStack visual = BlackjackCardCatalog.create(display, real.getCount());
        return visual.isEmpty() ? real : visual;
    }

    private static void applyStandVisual(ArmorStandEntity stand, EquipmentSlot slot, StandKey key) {
        Integer hostIndex = STAND_HOST_INDEX.get(key);
        if (hostIndex == null) {
            restoreStand(stand, slot, key);
            return;
        }
        ItemStack real = STAND_ORIGINALS.get(key);
        ItemStack visual = replacement(real, hostIndex);
        if (visual == null) {
            restoreStand(stand, slot, key);
            return;
        }
        stand.equipStack(slot, visual.copy());
        STAND_LAST_VISUAL.put(key, visual.copy());
    }

    private static ItemStack replacement(ItemStack real, int hostIndex) {
        if (!BlackjackState.isEnabled() || real == null || real.isEmpty()) {
            return null;
        }
        BlackjackCard actual = BlackjackCard.fromStack(real);
        BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
        if (actual == null || display == null || actual == display) {
            return null;
        }
        ItemStack visual = BlackjackCardCatalog.create(display, real.getCount());
        return visual.isEmpty() ? null : visual;
    }

    private static void cleanupMissingItems(MinecraftClient client, Set<Integer> alive) {
        Iterator<Map.Entry<Integer, SourceState>> iterator = ITEM_SOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, SourceState> entry = iterator.next();
            if (alive.contains(entry.getKey())) {
                continue;
            }
            ITEM_ORIGINALS.remove(entry.getKey());
            ITEM_LAST_VISUAL.remove(entry.getKey());
            ITEM_HOST_INDEX.remove(entry.getKey());
            iterator.remove();
        }
    }

    private static void cleanupMissingFrames(Set<Integer> alive) {
        Iterator<Integer> iterator = new ArrayList<>(FRAME_SOURCES.keySet()).iterator();
        while (iterator.hasNext()) {
            int id = iterator.next();
            if (!alive.contains(id)) {
                FRAME_SOURCES.remove(id);
                FRAME_HOST_INDEX.remove(id);
                FRAME_DISPLAY_CARD.remove(id);
                FRAME_EXPLICIT_OWNERSHIP.remove(id);
                FRAME_ORIGINALS.remove(id);
                FRAME_LAST_VISUAL.remove(id);
            }
        }
    }

    private static void cleanupMissingStandSlots(Set<StandKey> alive) {
        for (StandKey key : new ArrayList<>(STAND_ORIGINALS.keySet())) {
            if (!alive.contains(key)) {
                STAND_ORIGINALS.remove(key);
                STAND_LAST_VISUAL.remove(key);
                STAND_HOST_INDEX.remove(key);
                Map<EquipmentSlot, SourceState> slots = STAND_SOURCES.get(key.entityId);
                if (slots != null) {
                    slots.remove(key.slot);
                    if (slots.isEmpty()) {
                        STAND_SOURCES.remove(key.entityId);
                    }
                }
            }
        }
    }

    public static void restoreAll(MinecraftClient client) {
        if (client == null || client.world == null) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : ITEM_ORIGINALS.entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity instanceof ItemEntity item) {
                item.setStack(entry.getValue().copy());
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : FRAME_ORIGINALS.entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity instanceof ItemFrameEntity frame) {
                frame.setHeldItemStack(entry.getValue().copy());
            }
        }
        for (Map.Entry<StandKey, ItemStack> entry : STAND_ORIGINALS.entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey().entityId);
            if (entity instanceof ArmorStandEntity stand) {
                stand.equipStack(entry.getKey().slot, entry.getValue().copy());
            }
        }
    }

    public static void clearTransient() {
        ITEM_SOURCES.clear();
        FRAME_SOURCES.clear();
        STAND_SOURCES.clear();
        ITEM_ORIGINALS.clear();
        ITEM_LAST_VISUAL.clear();
        ITEM_HOST_INDEX.clear();
        FRAME_HOST_INDEX.clear();
        FRAME_ORIGINALS.clear();
        FRAME_LAST_VISUAL.clear();
        FRAME_DISPLAY_CARD.clear();
        FRAME_EXPLICIT_OWNERSHIP.clear();
        STAND_ORIGINALS.clear();
        STAND_LAST_VISUAL.clear();
        STAND_HOST_INDEX.clear();
        RECENT_DEALS.clear();
        PENDING_FRAME_RELEASES.clear();
        displayBaselineInitialized = false;
        BlackjackMasking.clearTransient();
    }

    private static void restoreItem(ItemEntity entity) {
        ItemStack real = ITEM_ORIGINALS.get(entity.getId());
        if (real != null && !ItemStack.areEqual(entity.getStack(), real)) {
            entity.setStack(real.copy());
        }
        ITEM_LAST_VISUAL.remove(entity.getId());
    }

    private static void restoreFrame(ItemFrameEntity frame) {
        ItemStack real = FRAME_ORIGINALS.get(frame.getId());
        if (real != null && !ItemStack.areEqual(frame.getHeldItemStack(), real)) {
            frame.setHeldItemStack(real.copy());
        }
        FRAME_LAST_VISUAL.remove(frame.getId());
    }

    private static void restoreStand(ArmorStandEntity stand, EquipmentSlot slot, StandKey key) {
        ItemStack real = STAND_ORIGINALS.get(key);
        if (real != null && !ItemStack.areEqual(stand.getEquippedStack(slot), real)) {
            stand.equipStack(slot, real.copy());
        }
        STAND_LAST_VISUAL.remove(key);
    }

    private static <K> void refreshOriginal(Map<K, ItemStack> originals, Map<K, ItemStack> lastVisual, K key, ItemStack current) {
        ItemStack visual = lastVisual.get(key);
        if (!originals.containsKey(key)) {
            originals.put(key, current.copy());
            return;
        }
        if (visual == null || !ItemStack.areEqual(current, visual)) {
            originals.put(key, current.copy());
            lastVisual.remove(key);
        }
    }

    private static void trimRecentDeals() {
        while (!RECENT_DEALS.isEmpty() && tickCounter - RECENT_DEALS.peekFirst().tick > 60) {
            RECENT_DEALS.removeFirst();
        }
    }

    private static boolean usable(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.world != null
                && BlackjackState.isEnabled();
    }

    private static final class PendingFrameRelease {
        final int frameId;
        final long createdAtTick;
        final BlackjackCard actual;
        final int hostIndex;
        final BlackjackCard display;
        final double x;
        final double y;
        final double z;
        int age;

        PendingFrameRelease(int frameId, BlackjackCard actual, int hostIndex, BlackjackCard display,
                            double x, double y, double z, int age) {
            this.frameId = frameId;
            this.createdAtTick = tickCounter;
            this.actual = actual;
            this.hostIndex = hostIndex;
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = age;
        }
    }

    private record SourceState(BlackjackCard card, boolean host, int handIndex, long tick) {}
    private record RecentDeal(BlackjackCard card, boolean host, int handIndex, long tick) {}
    private record StandKey(int entityId, EquipmentSlot slot) {}
}
