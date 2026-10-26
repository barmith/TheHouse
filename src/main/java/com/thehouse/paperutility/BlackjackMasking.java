package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.Hand;

import java.util.*;











public final class BlackjackMasking {
    
    
    private static final Map<Integer, List<Integer>> HOST_INDEXES_BY_INVENTORY_SLOT = new HashMap<>();
    private static final Map<Integer, Integer> HOST_SLOT_LAST_COUNT = new HashMap<>();

    private static final Map<Integer, ItemStack> RENDER_ORIGINALS = new HashMap<>();
    private static final List<PendingPickup> PENDING_PICKUPS = new ArrayList<>();
    private static final Deque<ReleasedHostCard> RECENTLY_RELEASED_HOST = new ArrayDeque<>();
    private static final Map<Integer, PendingFramePlacement> PENDING_FRAME_PLACEMENTS = new HashMap<>();

    
    
    
    private static final Map<Integer, HeldMaskLease> HELD_MASK_LEASE_BY_SLOT = new HashMap<>();
    private static final int HELD_MASK_LEASE_TICKS = 20;

    






    private static final Map<BlackjackCard, Integer> LOCKED_DISPLAY_ANCHOR_SLOT =
            new EnumMap<>(BlackjackCard.class);

    











    private static final Map<BlackjackCard, Integer> VIRTUAL_DISPLAY_SLOT =
            new EnumMap<>(BlackjackCard.class);

    private static boolean renderSwapActive = false;
    private static Integer cursorHostIndex = null;
    private static ItemStack cursorOriginal = ItemStack.EMPTY;
    private static boolean cursorRenderSwapped = false;

    private BlackjackMasking() {}

    
    private static int mainInventorySize(PlayerInventory inventory) {
        return Math.min(36, inventory.size());
    }



    public static void clearTransient() {
        HOST_INDEXES_BY_INVENTORY_SLOT.clear();
        HOST_SLOT_LAST_COUNT.clear();
        PENDING_PICKUPS.clear();
        RECENTLY_RELEASED_HOST.clear();
        PENDING_FRAME_PLACEMENTS.clear();
        HELD_MASK_LEASE_BY_SLOT.clear();
        LOCKED_DISPLAY_ANCHOR_SLOT.clear();
        VIRTUAL_DISPLAY_SLOT.clear();
        cursorHostIndex = null;
        cursorOriginal = ItemStack.EMPTY;
        cursorRenderSwapped = false;
        RENDER_ORIGINALS.clear();
        renderSwapActive = false;
    }

    public static void capturePendingPickup(MinecraftClient client, int entityId, BlackjackCard actual, boolean host, int hostIndex) {
        if (client == null || client.player == null || actual == null || (host && hostIndex < 0)) {
            return;
        }
        PlayerInventory inventory = client.player.getInventory();
        List<Integer> counts = new ArrayList<>(mainInventorySize(inventory));
        List<Integer> stackCounts = new ArrayList<>(mainInventorySize(inventory));
        for (int i = 0; i < mainInventorySize(inventory); i++) {
            ItemStack stack = inventory.getStack(i);
            counts.add(cardCount(stack, actual));
            stackCounts.add(stack == null || stack.isEmpty() ? 0 : stack.getCount());
        }
        PENDING_PICKUPS.removeIf(p -> p.entityId == entityId);
        PENDING_PICKUPS.add(new PendingPickup(entityId, actual, host, hostIndex, counts, stackCounts));
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            clearTransient();
            return;
        }
        if (!BlackjackState.isEnabled()) {
            clearTransient();
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        ItemStack cursor = client.player.currentScreenHandler.getCursorStack();

        reconcilePendingPickups(client, inventory, cursor);

        Iterator<Map.Entry<Integer, List<Integer>>> ownedSlots = HOST_INDEXES_BY_INVENTORY_SLOT.entrySet().iterator();
        while (ownedSlots.hasNext()) {
            Map.Entry<Integer, List<Integer>> entry = ownedSlots.next();
            int slot = entry.getKey();
            List<Integer> hostIndexes = entry.getValue();
            if (slot < 0 || slot >= mainInventorySize(inventory) || hostIndexes.isEmpty()) {
                HOST_SLOT_LAST_COUNT.remove(slot);
                ownedSlots.remove();
                continue;
            }

            ItemStack current = inventory.getStack(slot);
            int currentCount = current == null || current.isEmpty() ? 0 : current.getCount();
            int previousCount = HOST_SLOT_LAST_COUNT.getOrDefault(slot, currentCount);

            if (currentCount < previousCount) {
                int releasedCount = Math.max(1, previousCount - currentCount);
                for (int n = 0; n < releasedCount && !hostIndexes.isEmpty(); n++) {
                    int hostIndex = hostIndexes.remove(hostIndexes.size() - 1);
                    BlackjackCard releasedActual = actualForHostIndex(hostIndex);
                    if (releasedActual != null) {
                        RECENTLY_RELEASED_HOST.addLast(new ReleasedHostCard(releasedActual, hostIndex, 0));
                    }
                }
            }

            if (currentCount <= 0 || current == null || current.isEmpty()) {
                
                
                while (!hostIndexes.isEmpty()) {
                    int hostIndex = hostIndexes.remove(hostIndexes.size() - 1);
                    BlackjackCard releasedActual = actualForHostIndex(hostIndex);
                    if (releasedActual != null) {
                        RECENTLY_RELEASED_HOST.addLast(new ReleasedHostCard(releasedActual, hostIndex, 0));
                    }
                }
                HOST_SLOT_LAST_COUNT.remove(slot);
                ownedSlots.remove();
            } else if (hostIndexes.isEmpty()) {
                HOST_SLOT_LAST_COUNT.remove(slot);
                ownedSlots.remove();
            } else {
                HOST_SLOT_LAST_COUNT.put(slot, currentCount);
            }
        }

        Iterator<ReleasedHostCard> released = RECENTLY_RELEASED_HOST.iterator();
        while (released.hasNext()) {
            ReleasedHostCard card = released.next();
            card.age++;
            if (card.age > 8) {
                released.remove();
            }
        }

        Iterator<Map.Entry<Integer, HeldMaskLease>> heldLeases = HELD_MASK_LEASE_BY_SLOT.entrySet().iterator();
        while (heldLeases.hasNext()) {
            Map.Entry<Integer, HeldMaskLease> entry = heldLeases.next();
            HeldMaskLease lease = entry.getValue();
            lease.age++;
            BlackjackCard actual = actualForHostIndex(lease.hostIndex);
            BlackjackCard display = BlackjackState.displayedCardForHostIndex(lease.hostIndex);
            if (lease.age > HELD_MASK_LEASE_TICKS || actual != lease.actual
                    || display == null || display == actual) {
                heldLeases.remove();
            }
        }

        if (cursorHostIndex != null) {
            int hostIndex = cursorHostIndex;
            List<BlackjackCard> actual = BlackjackState.hostActualCards();
            if (hostIndex < 0 || hostIndex >= actual.size()) {
                cursorHostIndex = null;
            } else {
                BlackjackCard expected = actual.get(hostIndex);
                if (BlackjackCard.fromStack(cursor) != expected) {
                    BlackjackCard releasedActual = actualForHostIndex(hostIndex);
                    if (releasedActual != null) {
                        RECENTLY_RELEASED_HOST.addLast(new ReleasedHostCard(releasedActual, hostIndex, 0));
                    }
                    cursorHostIndex = null;
                }
            }
        }
    }

    





    private static void reconcilePendingPickups(MinecraftClient client, PlayerInventory inventory, ItemStack cursor) {
        Iterator<PendingPickup> iterator = PENDING_PICKUPS.iterator();
        while (iterator.hasNext()) {
            PendingPickup pending = iterator.next();
            
            
            if (client.world.getEntityById(pending.entityId) != null) {
                continue;
            }

            boolean assigned = false;
            for (int i = 0; i < mainInventorySize(inventory); i++) {
                int before = i < pending.baselineCounts.size() ? pending.baselineCounts.get(i) : 0;
                int now = cardCount(inventory.getStack(i), pending.actual);
                if (now > before) {
                    if (pending.host) {
                        addHostOwnership(i, pending.hostIndex);
                        HOST_SLOT_LAST_COUNT.put(i, now);
                    }
                    
                    
                    assigned = true;
                    break;
                }
            }

            









            if (!assigned && pending.host) {
                BlackjackCard pendingDisplay = BlackjackState.displayedCardForHostIndex(pending.hostIndex);
                if (pendingDisplay != null) {
                    for (int i = 0; i < mainInventorySize(inventory); i++) {
                        int beforeStack = i < pending.baselineStackCounts.size()
                                ? pending.baselineStackCounts.get(i) : 0;
                        ItemStack nowStack = inventory.getStack(i);
                        int nowStackCount = nowStack == null || nowStack.isEmpty() ? 0 : nowStack.getCount();
                        if (nowStackCount <= beforeStack) {
                            continue;
                        }
                        List<Integer> existingOwners = HOST_INDEXES_BY_INVENTORY_SLOT.get(i);
                        if (existingOwners == null || existingOwners.isEmpty()) {
                            continue;
                        }
                        boolean sameDisplay = false;
                        for (int existingHostIndex : existingOwners) {
                            if (BlackjackState.displayedCardForHostIndex(existingHostIndex) == pendingDisplay) {
                                sameDisplay = true;
                                break;
                            }
                        }
                        if (sameDisplay) {
                            addHostOwnership(i, pending.hostIndex);
                            HOST_SLOT_LAST_COUNT.put(i, nowStackCount);
                            assigned = true;
                            break;
                        }
                    }
                }
            }

            if (!assigned && BlackjackCard.fromStack(cursor) == pending.actual) {
                if (pending.host) {
                    cursorHostIndex = pending.hostIndex;
                }
                assigned = true;
            }

            if (assigned) {
                iterator.remove();
            }
        }

    }

    public static void beginRender(MinecraftClient client) {
        if (renderSwapActive || !shouldMask(client) || client.player == null) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        
        
        reconcilePendingPickups(client, inventory, client.player.currentScreenHandler.getCursorStack());
        Map<Integer, ItemStack> plan = buildVisualInventoryPlan(inventory);
        RENDER_ORIGINALS.clear();
        for (Map.Entry<Integer, ItemStack> entry : plan.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= mainInventorySize(inventory)) {
                continue;
            }
            RENDER_ORIGINALS.put(slot, inventory.getStack(slot).copy());
            inventory.setStack(slot, entry.getValue().copy());
        }
        renderSwapActive = !RENDER_ORIGINALS.isEmpty();
    }

    public static void endRender(MinecraftClient client) {
        if (!renderSwapActive || client == null || client.player == null) {
            RENDER_ORIGINALS.clear();
            renderSwapActive = false;
            return;
        }
        PlayerInventory inventory = client.player.getInventory();
        for (Map.Entry<Integer, ItemStack> entry : RENDER_ORIGINALS.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < mainInventorySize(inventory)) {
                inventory.setStack(slot, entry.getValue());
            }
        }
        RENDER_ORIGINALS.clear();
        renderSwapActive = false;
    }

    public static ItemStack replacementForSlot(MinecraftClient client, Slot slot) {
        if (!shouldMask(client) || client == null || client.player == null || slot == null) {
            return null;
        }
        int inventoryIndex = slot.getIndex();
        PlayerInventory inventory = client.player.getInventory();
        if (inventoryIndex < 0 || inventoryIndex >= mainInventorySize(inventory)
                || slot.getStack() != inventory.getStack(inventoryIndex)) {
            return null;
        }
        return buildVisualInventoryPlan(inventory).get(inventoryIndex);
    }

    public static ItemStack cursorReplacement(ItemStack real) {
        if (!shouldMask(MinecraftClient.getInstance()) || cursorHostIndex == null) {
            return null;
        }
        return replacementForHostIndex(real, cursorHostIndex);
    }

    public static ItemStack heldItemReplacement(MinecraftClient client, ItemStack real) {
        if (!shouldMask(client) || client == null || client.player == null || real == null || real.isEmpty()) {
            return null;
        }

        
        
        
        
        
        
        int selected = client.player.getInventory().getSelectedSlot();
        BlackjackCard actual = BlackjackCard.fromStack(real);
        if (actual != null) {
            int hostIndex = lastMaskedMatchingHostIndex(selected, actual);
            if (hostIndex >= 0) {
                ItemStack visual = replacementForHostIndex(real, hostIndex);
                if (visual != null) {
                    rememberHeldLease(selected, actual, hostIndex);
                    return visual;
                }
            }

            
            
            
            HeldMaskLease lease = HELD_MASK_LEASE_BY_SLOT.get(selected);
            if (lease != null && lease.actual == actual && lease.age <= HELD_MASK_LEASE_TICKS) {
                ItemStack visual = replacementForHostIndex(real, lease.hostIndex);
                if (visual != null) {
                    lease.age = 0;
                    return visual;
                }
            }
        }

        
        
        if (actual == null || BlackjackCard.fromStack(client.player.getInventory().getStack(selected)) != actual) {
            return null;
        }
        return buildVisualInventoryPlan(client.player.getInventory()).get(selected);
    }

    public static ItemStack tooltipReplacement(ItemStack real) {
        MinecraftClient client = MinecraftClient.getInstance();
        return heldItemReplacement(client, real);
    }

    
    public static void captureFramePlacement(PlayerEntity player, ItemFrameEntity frame, Hand hand) {
        
        
        if (!BlackjackState.isEnabled() || BlackjackState.isMaskLocked()
                || player == null || frame == null || hand == null) {
            return;
        }
        if (!frame.getHeldItemStack().isEmpty()) {
            return;
        }
        ItemStack held = player.getStackInHand(hand);
        BlackjackCard card = BlackjackCard.fromStack(held);
        if (card == null) {
            return;
        }

        int hostIndex = -1;
        if (hand == Hand.MAIN_HAND) {
            int selected = player.getInventory().getSelectedSlot();
            hostIndex = lastMatchingHostIndex(selected, card);
            if (hostIndex < 0) {
                HeldMaskLease lease = HELD_MASK_LEASE_BY_SLOT.get(selected);
                if (lease != null && lease.actual == card && lease.age <= HELD_MASK_LEASE_TICKS) {
                    hostIndex = lease.hostIndex;
                }
            }
            if (hostIndex >= 1) {
                rememberHeldLease(selected, card, hostIndex);
            }
        }
        if (hostIndex < 0 && cursorHostIndex != null) {
            List<BlackjackCard> actual = BlackjackState.hostActualCards();
            int owned = cursorHostIndex;
            if (owned >= 0 && owned < actual.size() && actual.get(owned) == card) {
                hostIndex = owned;
            }
        }

        BlackjackCard displayCard = card;
        if (hostIndex >= 1) {
            BlackjackCard assigned = BlackjackState.displayedCardForHostIndex(hostIndex);
            if (assigned != null) {
                displayCard = assigned;
            }
        }

        PendingFramePlacement existing = PENDING_FRAME_PLACEMENTS.get(frame.getId());
        if (existing == null || existing.card != card || (existing.hostIndex < 0 && hostIndex >= 0)) {
            PENDING_FRAME_PLACEMENTS.put(
                    frame.getId(),
                    new PendingFramePlacement(card, hostIndex, displayCard, 0)
            );
        }
    }

    public static FramePlacementClaim peekFramePlacement(int frameId, BlackjackCard card) {
        PendingFramePlacement pending = PENDING_FRAME_PLACEMENTS.get(frameId);
        if (pending == null || pending.card != card) {
            return null;
        }
        return new FramePlacementClaim(pending.hostIndex >= 0, pending.hostIndex, pending.displayCard);
    }

    public static FramePlacementClaim consumeFramePlacement(int frameId, BlackjackCard card) {
        PendingFramePlacement pending = PENDING_FRAME_PLACEMENTS.get(frameId);
        if (pending == null || pending.card != card) {
            return null;
        }
        PENDING_FRAME_PLACEMENTS.remove(frameId);
        return new FramePlacementClaim(pending.hostIndex >= 0, pending.hostIndex, pending.displayCard);
    }

    public static Integer claimHostIndexForWorldCard(BlackjackCard card) {
        
        
        
        if (BlackjackState.isMaskLocked() || card == null) {
            return null;
        }
        Iterator<ReleasedHostCard> released = RECENTLY_RELEASED_HOST.descendingIterator();
        while (released.hasNext()) {
            ReleasedHostCard candidate = released.next();
            if (candidate.card == card && candidate.age <= 8) {
                int index = candidate.hostIndex;
                released.remove();
                return index;
            }
        }
        return null;
    }

    




    private static Map<Integer, ItemStack> buildVisualInventoryPlan(PlayerInventory inventory) {
        Map<Integer, ItemStack> result = new HashMap<>();
        if (inventory == null || HOST_INDEXES_BY_INVENTORY_SLOT.isEmpty()) {
            return result;
        }

        int size = mainInventorySize(inventory);

        




        if (BlackjackState.isMaskLocked()) {
            learnLockedDisplayAnchors(inventory);
        }

        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            counts[i] = inventory.getStack(i).getCount();
        }

        Map<Integer, Integer> fallbackHostBySourceSlot = new HashMap<>();
        Set<Integer> touched = new HashSet<>();
        Set<Integer> fullyMaskedMergedSlots = new HashSet<>();

        






        for (Map.Entry<Integer, List<Integer>> entry : HOST_INDEXES_BY_INVENTORY_SLOT.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) continue;
            ItemStack real = inventory.getStack(slot);
            if (real == null || real.isEmpty() || entry.getValue().size() < real.getCount()) continue;

            BlackjackCard commonDisplay = null;
            boolean allSameDisplay = true;
            for (int hostIndex : entry.getValue()) {
                BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
                if (display == null) { allSameDisplay = false; break; }
                if (commonDisplay == null) commonDisplay = display;
                else if (commonDisplay != display) { allSameDisplay = false; break; }
            }
            if (allSameDisplay && commonDisplay != null) {
                ItemStack visual = BlackjackCardCatalog.create(commonDisplay, real.getCount());
                if (!visual.isEmpty()) {
                    result.put(slot, visual);
                    fullyMaskedMergedSlots.add(slot);
                }
            }
        }

        for (Map.Entry<Integer, List<Integer>> entry : HOST_INDEXES_BY_INVENTORY_SLOT.entrySet()) {
            int sourceSlot = entry.getKey();
            if (sourceSlot < 0 || sourceSlot >= size) {
                continue;
            }
            if (fullyMaskedMergedSlots.contains(sourceSlot)) {
                continue;
            }
            ItemStack sourceStack = inventory.getStack(sourceSlot);
            BlackjackCard sourceCard = BlackjackCard.fromStack(sourceStack);
            if (sourceCard == null) {
                continue;
            }

            for (int hostIndex : entry.getValue()) {
                BlackjackCard actual = actualForHostIndex(hostIndex);
                BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
                if (actual == null || display == null || actual != sourceCard || display == actual) {
                    continue;
                }

                if (counts[sourceSlot] <= 0) {
                    continue;
                }

                int destination = findTrueDisplayStack(inventory, display, sourceSlot);

                if (destination >= 0) {
                    
                    
                    counts[sourceSlot]--;
                    counts[destination]++;
                    touched.add(sourceSlot);
                    touched.add(destination);
                    continue;
                }

                











                if (counts[sourceSlot] == 1) {
                    fallbackHostBySourceSlot.put(sourceSlot, hostIndex);
                    continue;
                }

                int virtualDestination =
                        virtualDisplaySlot(inventory, display, sourceSlot, result);

                if (virtualDestination >= 0) {
                    counts[sourceSlot]--;
                    counts[virtualDestination]++;
                    touched.add(sourceSlot);
                    touched.add(virtualDestination);

                    ItemStack existing = result.get(virtualDestination);
                    if (existing == null || existing.isEmpty()) {
                        ItemStack visual = replacementForHostIndex(sourceStack, hostIndex);
                        if (visual != null) {
                            visual = visual.copy();
                            visual.setCount(counts[virtualDestination]);
                            result.put(virtualDestination, visual);
                        }
                    } else {
                        existing = existing.copy();
                        existing.setCount(counts[virtualDestination]);
                        result.put(virtualDestination, existing);
                    }
                } else {
                    
                    
                    fallbackHostBySourceSlot.put(sourceSlot, hostIndex);
                }
            }
        }

        for (int slot : touched) {
            ItemStack real = inventory.getStack(slot);
            int visualCount = Math.max(0, counts[slot]);

            if (real.isEmpty()) {
                ItemStack existing = result.get(slot);
                if (existing != null && !existing.isEmpty()) {
                    existing = existing.copy();
                    existing.setCount(visualCount);
                    result.put(slot, existing);
                }
                continue;
            }

            if (visualCount == real.getCount()) {
                continue;
            }
            if (visualCount == 0) {
                result.put(slot, ItemStack.EMPTY);
            } else {
                ItemStack visual = real.copy();
                visual.setCount(visualCount);
                result.put(slot, visual);
            }
        }

        for (Map.Entry<Integer, Integer> fallback : fallbackHostBySourceSlot.entrySet()) {
            int slot = fallback.getKey();
            
            if (touched.contains(slot)) {
                continue;
            }
            ItemStack real = inventory.getStack(slot);
            ItemStack visual = replacementForHostIndex(real, fallback.getValue());
            if (visual != null) {
                result.put(slot, visual);
            }
        }

        








        
        
        

        
        
        
        
        
        
        
        

        return result;
    }

    






    private static int virtualDisplaySlot(
            PlayerInventory inventory,
            BlackjackCard display,
            int sourceSlot,
            Map<Integer, ItemStack> result
    ) {
        Integer existing = VIRTUAL_DISPLAY_SLOT.get(display);
        if (existing != null
                && existing >= 9
                && existing < Math.min(36, mainInventorySize(inventory))
                && existing != sourceSlot
                && inventory.getStack(existing).isEmpty()) {
            return existing;
        }

        int end = Math.min(36, mainInventorySize(inventory));
        for (int slot = 9; slot < end; slot++) {
            if (slot == sourceSlot) {
                continue;
            }
            if (!inventory.getStack(slot).isEmpty()) {
                continue;
            }

            



            boolean alreadyReserved = false;
            for (Integer reserved : VIRTUAL_DISPLAY_SLOT.values()) {
                if (reserved != null && reserved == slot) {
                    alreadyReserved = true;
                    break;
                }
            }
            if (alreadyReserved || result.containsKey(slot)) {
                continue;
            }

            VIRTUAL_DISPLAY_SLOT.put(display, slot);
            if (BlackjackState.isMaskLocked()) {
                LOCKED_DISPLAY_ANCHOR_SLOT.putIfAbsent(display, slot);
            }
            return slot;
        }

        return -1;
    }

    



    private static void stabilizeLockedTrueStacks(
            PlayerInventory inventory,
            Map<Integer, ItemStack> result,
            int[] counts
    ) {
        for (Map.Entry<BlackjackCard, Integer> anchorEntry : LOCKED_DISPLAY_ANCHOR_SLOT.entrySet()) {
            BlackjackCard display = anchorEntry.getKey();
            int anchor = anchorEntry.getValue();

            if (anchor < 0 || anchor >= mainInventorySize(inventory)) {
                continue;
            }

            ItemStack anchorReal = inventory.getStack(anchor);
            if (anchorReal == null || anchorReal.isEmpty()) {
                continue;
            }

            BlackjackCard anchorActual = BlackjackCard.fromStack(anchorReal);
            if (anchorActual == display) {
                
                
                continue;
            }

            ItemStack anchorVisual = result.get(anchor);
            int combinedCount;

            if (result.containsKey(anchor) && (anchorVisual == null || anchorVisual.isEmpty())) {
                









                List<Integer> owners = HOST_INDEXES_BY_INVENTORY_SLOT.get(anchor);
                if (owners == null || owners.isEmpty()) {
                    continue;
                }
                anchorVisual = replacementForHostIndex(
                        anchorReal,
                        owners.get(owners.size() - 1)
                );
                if (anchorVisual == null || anchorVisual.isEmpty()) {
                    continue;
                }
                anchorVisual = anchorVisual.copy();
                combinedCount = 0;
            } else if (anchorVisual == null) {
                
                List<Integer> owners = HOST_INDEXES_BY_INVENTORY_SLOT.get(anchor);
                if (owners == null || owners.isEmpty()) {
                    continue;
                }
                anchorVisual = replacementForHostIndex(
                        anchorReal,
                        owners.get(owners.size() - 1)
                );
                if (anchorVisual == null || anchorVisual.isEmpty()) {
                    continue;
                }
                anchorVisual = anchorVisual.copy();
                combinedCount = anchorVisual.getCount();
            } else {
                anchorVisual = anchorVisual.copy();
                combinedCount = anchorVisual.getCount();
            }

            for (int slot = 0; slot < mainInventorySize(inventory); slot++) {
                if (slot == anchor) {
                    continue;
                }

                ItemStack real = inventory.getStack(slot);
                if (real == null || real.isEmpty()
                        || BlackjackCard.fromStack(real) != display) {
                    continue;
                }

                



                int remaining = Math.max(0, counts[slot]);
                if (remaining <= 0) {
                    continue;
                }

                combinedCount += remaining;
                result.put(slot, ItemStack.EMPTY);
                counts[slot] = 0;
            }

            anchorVisual.setCount(combinedCount);
            result.put(anchor, anchorVisual);
            counts[anchor] = combinedCount;
        }
    }

    




    public static List<VirtualReturnSource> virtualReturnSources(PlayerInventory inventory, int clickedInventorySlot) {
        if (inventory == null || clickedInventorySlot < 0 || clickedInventorySlot >= mainInventorySize(inventory)) {
            return List.of();
        }
        ItemStack clicked = inventory.getStack(clickedInventorySlot);
        BlackjackCard displayCard = BlackjackCard.fromStack(clicked);
        if (displayCard == null) {
            return List.of();
        }

        Map<Integer, VirtualReturnSource> grouped = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : HOST_INDEXES_BY_INVENTORY_SLOT.entrySet()) {
            int sourceSlot = entry.getKey();
            if (sourceSlot < 0 || sourceSlot >= mainInventorySize(inventory) || sourceSlot == clickedInventorySlot) {
                continue;
            }
            BlackjackCard actualStackCard = BlackjackCard.fromStack(inventory.getStack(sourceSlot));
            if (actualStackCard == null) {
                continue;
            }
            for (int hostIndex : entry.getValue()) {
                BlackjackCard actual = actualForHostIndex(hostIndex);
                BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
                if (actual == null || display == null || display != displayCard || actual != actualStackCard || display == actual) {
                    continue;
                }
                if (findTrueDisplayStack(inventory, display, sourceSlot) != clickedInventorySlot) {
                    continue;
                }
                VirtualReturnSource prior = grouped.get(sourceSlot);
                int count = prior == null ? 1 : prior.count() + 1;
                grouped.put(sourceSlot, new VirtualReturnSource(sourceSlot, actual, count));
            }
        }
        return List.copyOf(grouped.values());
    }

    public record VirtualReturnSource(int inventoryIndex, BlackjackCard actual, int count) {}

    private static int findTrueDisplayStack(PlayerInventory inventory, BlackjackCard display, int sourceSlot) {
        








        if (BlackjackState.isMaskLocked()) {
            Integer anchor = LOCKED_DISPLAY_ANCHOR_SLOT.get(display);
            if (anchor != null
                    && anchor >= 0
                    && anchor < mainInventorySize(inventory)
                    && anchor != sourceSlot
                    && !inventory.getStack(anchor).isEmpty()
                    && slotHasMaskedDisplayOwnership(anchor, display)) {
                return anchor;
            }
        }

        for (int i = 0; i < mainInventorySize(inventory); i++) {
            if (i == sourceSlot) {
                continue;
            }
            if (slotHasMaskedDisplayOwnership(i, display)) {
                if (BlackjackState.isMaskLocked()) {
                    LOCKED_DISPLAY_ANCHOR_SLOT.putIfAbsent(display, i);
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean slotHasMaskedDisplayOwnership(int slot, BlackjackCard display) {
        List<Integer> owners = HOST_INDEXES_BY_INVENTORY_SLOT.get(slot);
        if (owners == null || owners.isEmpty()) {
            return false;
        }
        for (int hostIndex : owners) {
            BlackjackCard actual = actualForHostIndex(hostIndex);
            BlackjackCard shown = BlackjackState.displayedCardForHostIndex(hostIndex);
            if (shown == display && actual != null && shown != actual) {
                return true;
            }
        }
        return false;
    }

    








    private static void learnLockedDisplayAnchors(PlayerInventory inventory) {
        for (int slot = 0; slot < mainInventorySize(inventory); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            BlackjackCard actual = BlackjackCard.fromStack(stack);
            if (actual == null) {
                continue;
            }

            BlackjackCard visible = actual;
            List<Integer> owners = HOST_INDEXES_BY_INVENTORY_SLOT.get(slot);

            if (owners != null && !owners.isEmpty() && owners.size() >= stack.getCount()) {
                BlackjackCard commonDisplay = null;
                boolean allSameDisplay = true;
                int considered = 0;

                for (int hostIndex : owners) {
                    BlackjackCard hostActual = actualForHostIndex(hostIndex);
                    BlackjackCard hostDisplay = BlackjackState.displayedCardForHostIndex(hostIndex);
                    if (hostActual != actual || hostDisplay == null) {
                        continue;
                    }

                    considered++;
                    if (commonDisplay == null) {
                        commonDisplay = hostDisplay;
                    } else if (commonDisplay != hostDisplay) {
                        allSameDisplay = false;
                        break;
                    }
                }

                if (allSameDisplay
                        && considered >= stack.getCount()
                        && commonDisplay != null) {
                    visible = commonDisplay;
                }
            }

            LOCKED_DISPLAY_ANCHOR_SLOT.putIfAbsent(visible, slot);
        }
    }

    private static void rememberHeldLease(int slot, BlackjackCard actual, int hostIndex) {
        if (slot < 0 || actual == null || hostIndex < 1) {
            return;
        }
        BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
        if (display == null || display == actual) {
            return;
        }
        HELD_MASK_LEASE_BY_SLOT.put(slot, new HeldMaskLease(actual, hostIndex, 0));
    }

    private static void addHostOwnership(int slot, int hostIndex) {
        List<Integer> list = HOST_INDEXES_BY_INVENTORY_SLOT.computeIfAbsent(slot, k -> new ArrayList<>());
        if (!list.contains(hostIndex)) {
            list.add(hostIndex);
        }
    }

    private static int lastMatchingHostIndex(int slot, BlackjackCard card) {
        List<Integer> list = HOST_INDEXES_BY_INVENTORY_SLOT.get(slot);
        if (list == null) {
            return -1;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            int hostIndex = list.get(i);
            BlackjackCard actual = actualForHostIndex(hostIndex);
            if (actual == card) {
                return hostIndex;
            }
        }
        return -1;
    }

    
    private static int lastMaskedMatchingHostIndex(int slot, BlackjackCard card) {
        List<Integer> list = HOST_INDEXES_BY_INVENTORY_SLOT.get(slot);
        if (list == null) {
            return -1;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            int hostIndex = list.get(i);
            BlackjackCard actual = actualForHostIndex(hostIndex);
            BlackjackCard display = BlackjackState.displayedCardForHostIndex(hostIndex);
            if (actual == card && display != null && display != actual) {
                return hostIndex;
            }
        }
        return -1;
    }

    private static BlackjackCard commonActualCard(List<Integer> hostIndexes) {
        BlackjackCard expected = null;
        for (int hostIndex : hostIndexes) {
            BlackjackCard actual = actualForHostIndex(hostIndex);
            if (actual == null) {
                return null;
            }
            if (expected == null) {
                expected = actual;
            } else if (expected != actual) {
                return null;
            }
        }
        return expected;
    }

    private static BlackjackCard actualForHostIndex(int hostIndex) {
        List<BlackjackCard> actual = BlackjackState.hostActualCards();
        return hostIndex >= 0 && hostIndex < actual.size() ? actual.get(hostIndex) : null;
    }

    private static ItemStack replacementForHostIndex(ItemStack real, int hostIndex) {
        if (real == null || real.isEmpty()) {
            return null;
        }
        BlackjackCard displayed = BlackjackState.displayedCardForHostIndex(hostIndex);
        BlackjackCard actual = BlackjackCard.fromStack(real);
        if (displayed == null || actual == null || displayed == actual) {
            return null;
        }
        ItemStack visual = BlackjackCardCatalog.create(displayed, real.getCount());
        return visual.isEmpty() ? null : visual;
    }

    private static boolean shouldMask(MinecraftClient client) {
        return client != null && BlackjackState.isEnabled();
    }

    private static int cardCount(ItemStack stack, BlackjackCard card) {
        return BlackjackCard.fromStack(stack) == card ? stack.getCount() : 0;
    }

    public record FramePlacementClaim(boolean host, int hostIndex, BlackjackCard displayCard) {}

    private static final class PendingFramePlacement {
        final BlackjackCard card;
        final int hostIndex;
        final BlackjackCard displayCard;
        int age;

        PendingFramePlacement(BlackjackCard card, int hostIndex, BlackjackCard displayCard, int age) {
            this.card = card;
            this.hostIndex = hostIndex;
            this.displayCard = displayCard;
            this.age = age;
        }
    }

    private static final class ReleasedHostCard {
        final BlackjackCard card;
        final int hostIndex;
        int age;

        ReleasedHostCard(BlackjackCard card, int hostIndex, int age) {
            this.card = card;
            this.hostIndex = hostIndex;
            this.age = age;
        }
    }

    private static final class HeldMaskLease {
        final BlackjackCard actual;
        final int hostIndex;
        int age;

        HeldMaskLease(BlackjackCard actual, int hostIndex, int age) {
            this.actual = actual;
            this.hostIndex = hostIndex;
            this.age = age;
        }
    }

    private static final class PendingPickup {
        final int entityId;
        final BlackjackCard actual;
        final boolean host;
        final int hostIndex;
        final List<Integer> baselineCounts;
        final List<Integer> baselineStackCounts;

        PendingPickup(int entityId, BlackjackCard actual, boolean host, int hostIndex,
                      List<Integer> baselineCounts, List<Integer> baselineStackCounts) {
            this.entityId = entityId;
            this.actual = actual;
            this.host = host;
            this.hostIndex = hostIndex;
            this.baselineCounts = baselineCounts;
            this.baselineStackCounts = baselineStackCounts;
        }
    }
}
