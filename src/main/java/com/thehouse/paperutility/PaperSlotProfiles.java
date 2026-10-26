package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.*;


public final class PaperSlotProfiles {
    private static final LinkedHashMap<String, String[]> PROFILES = new LinkedHashMap<>();
    private static String active = "Default";
    public static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_PROFILES = 64;

    private PaperSlotProfiles() {}

    private static String[] snapshot() {
        String[] items = new String[9];
        for (int i=0;i<9;i++) items[i]=PaperItemCatalog.getItemId(i+1);
        return items;
    }

    private static boolean validItems(String[] items) {
        for (String raw:items) {
            Identifier id=raw==null?null:Identifier.tryParse(raw);
            if(id==null||!Registries.ITEM.containsId(id)||Registries.ITEM.get(id)==Items.AIR)return false;
        }
        return true;
    }

    private static String validName(String raw) {
        if(raw==null)return null;
        String name=raw.trim();
        if(name.isEmpty()||name.length()>MAX_NAME_LENGTH||name.codePoints().anyMatch(Character::isISOControl))return null;
        return name;
    }

    private static String find(String name) {
        if(name==null)return null;
        for(String existing:PROFILES.keySet())if(existing.equalsIgnoreCase(name.trim()))return existing;
        return null;
    }

    private static void ensureDefault() {
        if(PROFILES.isEmpty()){active="Default";PROFILES.put(active,snapshot());}
    }

    public static synchronized List<String> names() {ensureDefault();return List.copyOf(PROFILES.keySet());}
    public static synchronized String activeName() {ensureDefault();return active;}

    public static synchronized void load(Properties p) {
        PROFILES.clear();
        int count;
        try {count=Math.max(0,Math.min(MAX_PROFILES,Integer.parseInt(p.getProperty("paperProfiles.count","0"))));}
        catch(NumberFormatException e){count=0;}
        for(int i=0;i<count;i++) {
            String prefix="paperProfiles."+i+".";
            String name=validName(p.getProperty(prefix+"name"));
            String[] items=new String[9];
            for(int slot=0;slot<9;slot++)items[slot]=p.getProperty(prefix+"slot"+(slot+1));
            if(name!=null&&find(name)==null&&validItems(items))PROFILES.put(name,items);
        }
        
        ensureDefault();
        String selected=find(p.getProperty("paperProfiles.active"));
        active=selected==null?PROFILES.keySet().iterator().next():selected;
        apply(PROFILES.get(active));
    }

    public static synchronized void store(Properties p) {
        ensureDefault();
        PROFILES.put(active,snapshot()); 
        p.setProperty("paperProfiles.count",Integer.toString(PROFILES.size()));
        p.setProperty("paperProfiles.active",active);
        int i=0;
        for(var entry:PROFILES.entrySet()) {
            String prefix="paperProfiles."+(i++)+".";
            p.setProperty(prefix+"name",entry.getKey());
            for(int slot=0;slot<9;slot++)p.setProperty(prefix+"slot"+(slot+1),entry.getValue()[slot]);
        }
    }

    
    public static synchronized String create(String rawName) {
        ensureDefault();
        String name=validName(rawName);
        if(name==null)return "Use a profile name of 1–32 characters.";
        if(find(name)!=null)return "A profile with that name already exists.";
        if(PROFILES.size()>=MAX_PROFILES)return "You can save up to 64 profiles.";
        PROFILES.put(active,snapshot());
        PROFILES.put(name,snapshot());active=name;
        ModConfig.save();return null;
    }

    public static synchronized String switchTo(String name) {
        ensureDefault();
        String selected=find(name);
        if(selected==null)return "Profile not found.";
        if(selected.equals(active))return null;
        String[] items=PROFILES.get(selected);
        if(!validItems(items))return "This profile contains an unavailable item.";
        PROFILES.put(active,snapshot());
        
        PaperSimulationTracker.resetTransientState(MinecraftClient.getInstance());
        apply(items);active=selected;
        ModConfig.save();return null;
    }

    public static synchronized String delete(String name) {
        ensureDefault();
        String selected=find(name);
        if(selected==null)return "Profile not found.";
        if(PROFILES.size()==1)return "Keep at least one profile.";
        if(selected.equals(active)) {
            String next=PROFILES.keySet().stream().filter(n->!n.equals(selected)).findFirst().orElseThrow();
            String error=switchTo(next);if(error!=null)return error;
        }
        PROFILES.remove(selected);ModConfig.save();return null;
    }

    private static void apply(String[] items) {
        for(int i=0;i<9;i++)PaperItemCatalog.setItemById(i+1,items[i]);
        PaperItemCatalog.clearLearnedTemplates();
    }
}
