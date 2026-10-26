package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.*;






public final class ArrowInventoryMask {
    private static final class PickupMask {
        final int entityId;
        final ItemStack genuine;
        final ItemStack visual;
        final List<ItemStack> baseline = new ArrayList<>();
        int acquiredSlot = -1;
        boolean acquired;
        int missingTicks;

        PickupMask(int entityId, ItemStack genuine, ItemStack visual, PlayerInventory inv) {
            this.entityId = entityId;
            this.genuine = one(genuine);
            this.visual = one(visual);
            for (int i = 0; i < inv.size(); i++) baseline.add(inv.getStack(i).copy());
        }
    }

    private static final Map<Integer, PickupMask> MASKS = new LinkedHashMap<>();
    private static final Map<Integer, ItemStack> OVERRIDES = new HashMap<>();
    private static final Map<Integer, ItemStack> ORIGINALS = new HashMap<>();
    private static boolean renderActive;

    
    
    
    
    
    
    private static ItemStack placementGenuine = ItemStack.EMPTY;
    private static ItemStack placementVisual = ItemStack.EMPTY;
    private static long placementVisualUntilNanos;

    private ArrowInventoryMask() {}

    public static void observeImpactDrop(MinecraftClient client, ItemEntity item, ItemStack maskedStack) {
        if (client == null || client.player == null || item == null || maskedStack == null || maskedStack.isEmpty()) return;
        ItemStack real = item.getStack();
        if (real == null || real.isEmpty()) return;
        int id = item.getId();
        if (MASKS.containsKey(id)) return; 
        MASKS.put(id, new PickupMask(id, real, maskedStack, client.player.getInventory()));
        trimOldMasks();
    }

    public static void tick(MinecraftClient client) {
        OVERRIDES.clear();
        if (client == null || client.player == null || MASKS.isEmpty()) return;
        PlayerInventory inv = client.player.getInventory();
        int mainSize = Math.min(36, inv.size());
        Set<Integer> claimedSlots = new HashSet<>();
        Iterator<PickupMask> it = MASKS.values().iterator();

        while (it.hasNext()) {
            PickupMask m = it.next();

            if (!m.acquired) {
                int slot = findAcquiredSlot(inv, mainSize, m, claimedSlots);
                if (slot >= 0) {
                    m.acquired = true;
                    m.acquiredSlot = slot;
                    m.missingTicks = 0;
                } else {
                    continue;
                }
            }

            int slot = m.acquiredSlot;
            if (slot < 0 || slot >= mainSize) { it.remove(); continue; }
            ItemStack now = inv.getStack(slot);
            if (now == null || now.isEmpty()) {
                if (++m.missingTicks >= 2) it.remove();
                continue;
            }

            
            
            
            if (!sameIdentity(now, m.genuine) && !now.isOf(m.genuine.getItem())) {
                if (++m.missingTicks >= 2) it.remove();
                continue;
            }
            m.missingTicks = 0;
            claimedSlots.add(slot);

            ItemStack before = slot < m.baseline.size() ? m.baseline.get(slot) : ItemStack.EMPTY;
            int beforeCount = sameIdentity(before, m.genuine) ? before.getCount() : 0;
            int acquiredCount = Math.max(1, now.getCount() - beforeCount);

            if (beforeCount > 0) {
                ItemStack retained = before.copy();
                retained.setCount(beforeCount);
                OVERRIDES.put(slot, retained);
                int empty = findEmptyVisualSlot(inv, slot, claimedSlots);
                if (empty >= 0) {
                    ItemStack fake = m.visual.copy(); fake.setCount(acquiredCount);
                    OVERRIDES.put(empty, fake); claimedSlots.add(empty);
                } else {
                    ItemStack fake = m.visual.copy(); fake.setCount(now.getCount());
                    OVERRIDES.put(slot, fake);
                }
            } else {
                ItemStack fake = m.visual.copy(); fake.setCount(Math.max(1, now.getCount()));
                OVERRIDES.put(slot, fake);
            }
        }
    }

    private static int findAcquiredSlot(PlayerInventory inv, int mainSize, PickupMask m, Set<Integer> claimed) {
        
        for (int i = 0; i < mainSize; i++) {
            if (claimed.contains(i)) continue;
            ItemStack now = inv.getStack(i);
            if (!sameIdentity(now, m.genuine)) continue;
            ItemStack before = i < m.baseline.size() ? m.baseline.get(i) : ItemStack.EMPTY;
            int beforeCount = sameIdentity(before, m.genuine) ? before.getCount() : 0;
            if (now.getCount() > beforeCount) return i;
        }
        
        for (int i = 0; i < mainSize; i++) {
            if (claimed.contains(i)) continue;
            ItemStack now = inv.getStack(i);
            if (now == null || now.isEmpty() || !now.isOf(m.genuine.getItem())) continue;
            ItemStack before = i < m.baseline.size() ? m.baseline.get(i) : ItemStack.EMPTY;
            int beforeCount = before != null && !before.isEmpty() && before.isOf(m.genuine.getItem()) ? before.getCount() : 0;
            if (now.getCount() > beforeCount) return i;
        }
        return -1;
    }

    private static int findEmptyVisualSlot(PlayerInventory inv, int excluded, Set<Integer> claimed) {
        int mainSize = Math.min(36, inv.size());
        for (int i = 0; i < mainSize; i++) {
            if (i == excluded || claimed.contains(i) || OVERRIDES.containsKey(i)) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private static ItemStack one(ItemStack s) {
        ItemStack c = s.copy(); c.setCount(1); return c;
    }

    private static boolean sameIdentity(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.areEqual(one(a), one(b));
    }

    public static void beginRender(MinecraftClient client) {
        if (renderActive || OVERRIDES.isEmpty() || client == null || client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        ORIGINALS.clear();
        for (Map.Entry<Integer, ItemStack> e : OVERRIDES.entrySet()) {
            int slot=e.getKey(); if(slot<0||slot>=inv.size()) continue;
            ORIGINALS.put(slot,inv.getStack(slot).copy()); inv.setStack(slot,e.getValue().copy());
        }
        renderActive=!ORIGINALS.isEmpty();
    }

    public static void endRender(MinecraftClient client) {
        if(!renderActive)return;
        if(client!=null&&client.player!=null){PlayerInventory inv=client.player.getInventory();for(var e:ORIGINALS.entrySet())if(e.getKey()>=0&&e.getKey()<inv.size())inv.setStack(e.getKey(),e.getValue().copy());}
        ORIGINALS.clear();renderActive=false;
    }

    
    public static void latchPlacementVisual(MinecraftClient client) {
        placementGenuine = ItemStack.EMPTY;
        placementVisual = ItemStack.EMPTY;
        placementVisualUntilNanos = 0L;
        if (client == null || client.player == null) return;
        int selected = client.player.getInventory().getSelectedSlot();
        ItemStack fake = OVERRIDES.get(selected);
        if (fake == null || fake.isEmpty()) return;
        ItemStack genuine = renderActive && ORIGINALS.containsKey(selected)
                ? ORIGINALS.get(selected) : client.player.getInventory().getStack(selected);
        if (genuine == null || genuine.isEmpty()) return;
        placementGenuine = one(genuine);
        placementVisual = one(fake);
        
        
        placementVisualUntilNanos = System.nanoTime() + 350_000_000L;
    }

    public static ItemStack heldReplacement(MinecraftClient client, ItemStack rendered) {
        if(client==null||client.player==null||rendered==null||rendered.isEmpty())return null;

        if (placementVisualUntilNanos != 0L) {
            if (System.nanoTime() <= placementVisualUntilNanos
                    && !placementGenuine.isEmpty() && !placementVisual.isEmpty()
                    && sameIdentity(rendered, placementGenuine)) {
                ItemStack out = placementVisual.copy();
                out.setCount(rendered.getCount());
                return out;
            }
            if (System.nanoTime() > placementVisualUntilNanos) {
                placementGenuine = ItemStack.EMPTY;
                placementVisual = ItemStack.EMPTY;
                placementVisualUntilNanos = 0L;
            }
        }

        int selected=client.player.getInventory().getSelectedSlot(); ItemStack fake=OVERRIDES.get(selected); if(fake==null)return null;
        ItemStack genuineSelected=renderActive&&ORIGINALS.containsKey(selected)?ORIGINALS.get(selected):client.player.getInventory().getStack(selected);
        if(!sameIdentity(rendered,genuineSelected))return null; return fake.copy();
    }

    public static ItemStack tooltipReplacement(ItemStack rendered) {
        if(rendered==null||rendered.isEmpty())return null;
        for(PickupMask m:MASKS.values()) if(m.acquired&&sameIdentity(rendered,m.genuine)) return m.visual.copy();
        return null;
    }

    private static void trimOldMasks(){while(MASKS.size()>32){Integer k=MASKS.keySet().iterator().next();MASKS.remove(k);}}

    public static void clear(){MASKS.clear();OVERRIDES.clear();ORIGINALS.clear();renderActive=false;placementGenuine=ItemStack.EMPTY;placementVisual=ItemStack.EMPTY;placementVisualUntilNanos=0L;}
}
