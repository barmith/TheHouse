package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;


public final class ArrowTracker {
    private static final double SEARCH_RADIUS = 18.0;
    private static final double MAX_CENTER_DISTANCE_SQ = 12.0 * 12.0;
    private static final double MOVEMENT_EPSILON_SQ = 0.000025;
    private static final double ACQUIRE_MOVEMENT_SQ = 0.0001;
    private static final int STILL_TICKS_REQUIRED = 4;
    private static final int FINISHED_ARROW_HOLD_TICKS = 100;
    private static final Map<Integer, RoundState> ROUNDS = new TreeMap<>();
    private static final List<ImpactState> IMPACTS = new ArrayList<>();
    private static final Map<Long, ItemStack> FRAME_STACK_CACHE = new HashMap<>();

    private static int worldTick;
    private static double boardFrameY=Double.NaN, boardImpactY=Double.NaN;
    private static volatile String maskDiagnostic="WAITING FOR BOARD";
    private static volatile int boardFrameCount;

    private static final Map<Long, Integer> PENDING_BREAKS = new LinkedHashMap<>();
    private static final Map<Long, ItemStack> LIVE_FRAMES = new HashMap<>();
    private static final Map<Long, ItemStack> FRAME_VISUALS = new HashMap<>();

    private static final class RoundState {
        final int entityId;
        final double launchX, launchY, launchZ;
        int ticks, stillTicks, holdTicks, finishedAtTick;
        boolean haveLast, haveSegment, targetReady, finished, impactCommitted;
        
        
        boolean maskedRound;
        double endX, endZ;
        double lastX,lastY,lastZ, x0,y0,z0,x1,y1,z1;
        int targetX, targetZ;
        int predictedTrueX, predictedTrueZ;
        boolean havePredictedTrue;
        ItemStack maskedStack = ItemStack.EMPTY;
        RoundState(ArrowEntity a) {
            entityId=a.getId(); launchX=a.getX(); launchY=a.getY(); launchZ=a.getZ();
        }
    }

    




    private static final class ImpactState {
        int realX,realZ,targetX,targetZ, stagedAtTick;
        int arrowEntityId=-1;
        boolean active=true, postPlacement, sawRealEmpty, confirmed;
        ItemStack maskedStack=ItemStack.EMPTY, realVisual=ItemStack.EMPTY;
    }

    private ArrowTracker() {}

    public static void tick(MinecraftClient client) {
        if (!ArrowState.isEnabled() || client==null || client.world==null || client.player==null) { clearTransient(); return; }
        worldTick++;
        
        scanBoardGeometry(client);
        acquireAllCandidates(client);
        cacheBoardFrames(client);

        Iterator<Map.Entry<Integer,RoundState>> it=ROUNDS.entrySet().iterator();
        while(it.hasNext()) {
            RoundState r=it.next().getValue();
            if(r.finished) { if(findById(client,r.entityId)==null && --r.holdTicks<=0) it.remove(); continue; }
            ArrowEntity a=findById(client,r.entityId);
            if(a==null) { if(r.haveLast) finish(r,r.lastX,r.lastY,r.lastZ); else it.remove(); continue; }
            updateRound(r,a);
            stageVisualImpactIfImminent(r,a);
            if(r.stillTicks>=STILL_TICKS_REQUIRED) finish(r,a.getX(),a.getY(),a.getZ());
        }
        ArrowState.setTrackingCount(activeRoundCount());
    }

    private static void acquireAllCandidates(MinecraftClient client) {
        Box box;
        if(ArrowState.getBoardCenter()!=null) {
            var c=ArrowState.getBoardCenter();
            box=new Box(c.getX(),c.getY(),c.getZ(),c.getX()+1,c.getY()+1,c.getZ()+1).expand(SEARCH_RADIUS);
        } else box=client.player.getBoundingBox().expand(SEARCH_RADIUS);
        List<ArrowEntity> arrows=client.world.getEntitiesByClass(ArrowEntity.class,box,a->{
            if(ArrowState.getBoardCenter()==null) return true;
            double dx=a.getX()-(ArrowState.getBoardCenter().getX()+.5), dz=a.getZ()-(ArrowState.getBoardCenter().getZ()+.5);
            return dx*dx+dz*dz<=MAX_CENTER_DISTANCE_SQ;
        });
        arrows.sort(Comparator.comparingInt(ArrowEntity::getId));
        for(ArrowEntity a:arrows) {
            if(ROUNDS.containsKey(a.getId()) || a.getVelocity().lengthSquared()<=ACQUIRE_MOVEMENT_SQ) continue;
            RoundState r=new RoundState(a);
            ROUNDS.put(a.getId(),r);
            predictLanding(r,a);
        }
    }

    private static void updateRound(RoundState r, ArrowEntity a) {
        r.ticks++;
        if(!r.impactCommitted) predictLanding(r,a);
        double x=a.getX(),y=a.getY(),z=a.getZ();
        if(r.haveLast) {
            double dx=x-r.lastX,dy=y-r.lastY,dz=z-r.lastZ, m=dx*dx+dy*dy+dz*dz;
            if(m<=MOVEMENT_EPSILON_SQ) r.stillTicks++; else {
                r.stillTicks=0;
                if(dy<-.001) { r.x0=r.lastX;r.y0=r.lastY;r.z0=r.lastZ;r.x1=x;r.y1=y;r.z1=z;r.haveSegment=true; updateAutomaticTarget(r); }
            }
        }
        r.lastX=x;r.lastY=y;r.lastZ=z;r.haveLast=true;
    }

    private static ArrowEntity findById(MinecraftClient client,int id) {
        var e=client.world.getEntityById(id); return e instanceof ArrowEntity a?a:null;
    }

    





    private static void stageVisualImpactIfImminent(RoundState r, ArrowEntity a) {
        if(r==null||a==null||r.impactCommitted||!r.targetReady||!r.havePredictedTrue||!r.maskedRound
                ||ArrowState.getBoardCenter()==null||!ArrowState.isMaskingEnabled()) return;
        var c=ArrowState.getBoardCenter();
        double plane=boardPlaneY();
        Vec3d v=a.getVelocity();
        
        
        
        
        if(v.y>=-.001 || a.getY()-plane>Math.max(.16,Math.abs(v.y)*1.25) || a.getY()<plane-.35) return;
        long realKey=cellKey(r.predictedTrueX,r.predictedTrueZ), targetKey=cellKey(r.targetX,r.targetZ);
        ItemStack real=FRAME_VISUALS.getOrDefault(realKey,LIVE_FRAMES.getOrDefault(realKey,FRAME_STACK_CACHE.getOrDefault(realKey,ItemStack.EMPTY)));
        ItemStack target=FRAME_VISUALS.getOrDefault(targetKey,LIVE_FRAMES.getOrDefault(targetKey,FRAME_STACK_CACHE.getOrDefault(targetKey,ItemStack.EMPTY)));
        if(real.isEmpty()||target.isEmpty()) return;
        ImpactState p=new ImpactState();
        p.realX=r.predictedTrueX;p.realZ=r.predictedTrueZ;p.targetX=r.targetX;p.targetZ=r.targetZ;
        p.realVisual=real.copy();p.maskedStack=r.maskedStack.isEmpty()?target.copy():r.maskedStack.copy();
        if(!p.maskedStack.isEmpty())p.maskedStack.setCount(1);
        p.stagedAtTick=worldTick;p.arrowEntityId=r.entityId;
        
        
        FRAME_VISUALS.put(realKey,p.realVisual.copy());
        FRAME_VISUALS.put(targetKey,ItemStack.EMPTY);
        IMPACTS.add(p);
        r.impactCommitted=true;
        maskDiagnostic="IMPACT STAGED";
    }

    private static void finish(RoundState r,double ex,double ey,double ez) {
        if(r.finished) return;
        double tx=ex,tz=ez; String method="EMBED";
        if(r.haveSegment && ArrowState.getBoardCenter()!=null) {
            double py=boardPlaneY(), dy=r.y1-r.y0;
            if(Math.abs(dy)>1e-7) { double t=(py-r.y0)/dy; if(t>=-.35&&t<=1.75) { tx=r.x0+(r.x1-r.x0)*t; tz=r.z0+(r.z1-r.z0)*t; method="PLANE"; } }
        }
        int[] cell=resolvePhysicalLandingCell(tx,tz);
        
        
        if(cell!=null && Math.abs(ey-(boardPlaneY()))<=1.0) {
            r.predictedTrueX=cell[0]; r.predictedTrueZ=cell[1]; r.havePredictedTrue=true;
            if(!r.impactCommitted) { r.endX=tx;r.endZ=tz;chooseAutomaticTarget(r,cell[0],cell[1]); }
        }
        if(cell!=null) ArrowState.setDetectedLandingCell(cell[0],cell[1],ex,ey,ez,method); else ArrowState.setNoGridLanding(ex,ey,ez,method);
        r.finished=true;r.finishedAtTick=worldTick;r.holdTicks=FINISHED_ARROW_HOLD_TICKS;
    }

    
    public static Vec3d syntheticRenderPosition(ArrowEntity a, Vec3d real) {
        if(a==null||!ArrowState.isEnabled()||!ArrowState.isMaskingEnabled()||ArrowState.getBoardCenter()==null) return null;
        RoundState r=ROUNDS.get(a.getId()); if(r==null||!r.targetReady||!r.maskedRound) return null;
        var c=ArrowState.getBoardCenter();
        double tx=c.getX()+r.targetX+.5,tz=c.getZ()+r.targetZ+.5;
        double dx=r.endX-r.launchX,dz=r.endZ-r.launchZ,den=dx*dx+dz*dz;
        double progress=den<1e-7 ? (r.launchY-real.y)/Math.max(.001,r.launchY-(boardPlaneY()))
                : ((real.x-r.launchX)*dx+(real.z-r.launchZ)*dz)/den;
        progress=Math.max(0.0,Math.min(1.0,progress));
        
        double blend=progress*progress*(3.0-2.0*progress);
        if(r.impactCommitted) return null;
        return new Vec3d(real.x+(tx-r.endX)*blend,real.y,real.z+(tz-r.endZ)*blend);
    }

    







    public static boolean shouldHideTerminalMaskedArrow(ArrowEntity a) {
        if(a==null||!ArrowState.isEnabled()||!ArrowState.isMaskingEnabled()) return false;
        RoundState r=ROUNDS.get(a.getId());
        return r!=null && r.maskedRound && r.impactCommitted;
    }

    
    public static boolean shouldSuppressImpactedArrow(ArrowEntity a) {
        if(a==null)return false;
        RoundState r=ROUNDS.get(a.getId());
        if(r==null||!r.maskedRound)return false;
        for(ImpactState p:IMPACTS)
            if(p.active&&p.confirmed&&p.arrowEntityId==a.getId())return true;
        return false;
    }

    
    private static void predictLanding(RoundState r,ArrowEntity a) {
        if(ArrowState.getBoardCenter()==null)return;
        double x=a.getX(),y=a.getY(),z=a.getZ(),plane=boardPlaneY();
        Vec3d v=a.getVelocity(); double vx=v.x,vy=v.y,vz=v.z;
        for(int i=0;i<160;i++) {
            double nx=x+vx,ny=y+vy,nz=z+vz;
            if(vy<0 && y>=plane && ny<=plane) {
                double t=(plane-y)/(ny-y); int[] cell=resolvePhysicalLandingCell(x+(nx-x)*t,z+(nz-z)*t);
                if(cell!=null) {
                    r.endX=x+(nx-x)*t;r.endZ=z+(nz-z)*t;
                    r.predictedTrueX=cell[0];r.predictedTrueZ=cell[1];r.havePredictedTrue=true;
                    chooseAutomaticTarget(r,cell[0],cell[1]);
                }
                return;
            }
            x=nx;y=ny;z=nz;vx*=.99;vy=vy*.99-.05;vz*=.99;
        }
    }

    
    public static void observeWorldUpdate(MinecraftClient client) {
        if(!ArrowState.isEnabled()||client==null||client.world==null||client.player==null)return;
        
        scanBoardGeometry(client);
        acquireAllCandidates(client);
        cacheBoardFrames(client);
        for(RoundState r:ROUNDS.values()) {
            if(r.finished||r.impactCommitted)continue;
            ArrowEntity a=findById(client,r.entityId);
            if(a!=null&&!r.targetReady)predictLanding(r,a);
        }
    }

    private static boolean confirmFrameBreak(int x,int z) {
        var c=ArrowState.getBoardCenter();
        
        
        
        for(int i=IMPACTS.size()-1;i>=0;i--) {
            ImpactState staged=IMPACTS.get(i);
            if(staged.active&&!staged.confirmed&&staged.realX==x&&staged.realZ==z) {
                staged.confirmed=true;staged.sawRealEmpty=true;
                maskDiagnostic="HOST WIN MASK CONFIRMED";
                RoundState sr=ROUNDS.get(staged.arrowEntityId);
                if(sr!=null){sr.finished=true;sr.finishedAtTick=worldTick;sr.holdTicks=FINISHED_ARROW_HOLD_TICKS;}
                ArrowState.setDetectedLandingCell(x,z,c.getX()+x+.5,boardPlaneY(),c.getZ()+z+.5,"FRAME");
                return true;
            }
        }
        RoundState best=null;double bestD=Double.POSITIVE_INFINITY;
        MinecraftClient client=MinecraftClient.getInstance();
        for(RoundState r:ROUNDS.values()) {
            if((r.impactCommitted && !hasUnconfirmedImpact(r.entityId))
                    ||(r.finished&&worldTick-r.finishedAtTick>8))continue;
            ArrowEntity a=findById(client,r.entityId);
            double ax=a==null?r.lastX:a.getX(),ay=a==null?r.lastY:a.getY(),az=a==null?r.lastZ:a.getZ();
            if(a==null&&!r.haveLast)continue;
            if(Math.abs(ay-(boardPlaneY()))>2.0)continue;
            if(a!=null&&a.getVelocity().y>.01)continue;
            double dx=ax-(c.getX()+x+.5),dz=az-(c.getZ()+z+.5),d=dx*dx+dz*dz;
            boolean predicted=r.havePredictedTrue&&r.predictedTrueX==x&&r.predictedTrueZ==z;
            if(d>2.25||(!predicted&&d>0.81))continue;
            double score=d+(predicted?0:1.0);
            if(score<bestD){best=r;bestD=score;}
        }
        if(best==null)return false;
        RoundState r=best;
        
        
        rollbackUnconfirmedImpact(r);
        r.predictedTrueX=x;r.predictedTrueZ=z;r.havePredictedTrue=true;
        chooseAutomaticTarget(r,x,z);
        if(!r.targetReady)return false;

        
        if(!r.maskedRound) {
            FRAME_VISUALS.remove(cellKey(x,z));
            r.finished=true;r.finishedAtTick=worldTick;r.holdTicks=FINISHED_ARROW_HOLD_TICKS;
            ArrowState.setDetectedLandingCell(x,z,c.getX()+x+.5,boardPlaneY(),c.getZ()+z+.5,"FRAME");
            return true;
        }

        ImpactState p=findStagedImpactForArrow(r.entityId,x,z);
        if(p==null) {
            
            
            p=new ImpactState();p.realX=x;p.realZ=z;p.targetX=r.targetX;p.targetZ=r.targetZ;
            ItemStack real=FRAME_VISUALS.getOrDefault(cellKey(x,z),FRAME_STACK_CACHE.getOrDefault(cellKey(x,z),ItemStack.EMPTY));
            p.realVisual=real.copy();p.maskedStack=r.maskedStack.copy();p.stagedAtTick=worldTick;p.arrowEntityId=r.entityId;
            FRAME_VISUALS.put(cellKey(x,z),p.realVisual.copy());
            FRAME_VISUALS.put(cellKey(p.targetX,p.targetZ),ItemStack.EMPTY);
            IMPACTS.add(p);
        }
        p.confirmed=true;p.sawRealEmpty=true;
        maskDiagnostic="HOST WIN MASK CONFIRMED";
        r.impactCommitted=true;r.finished=true;r.finishedAtTick=worldTick;r.holdTicks=FINISHED_ARROW_HOLD_TICKS;
        ArrowState.setDetectedLandingCell(x,z,c.getX()+x+.5,boardPlaneY(),c.getZ()+z+.5,"FRAME");
        return true;
    }

    private static boolean hasUnconfirmedImpact(int arrowId) {
        for(ImpactState p:IMPACTS)
            if(p.active&&!p.confirmed&&p.arrowEntityId==arrowId)return true;
        return false;
    }

    private static void rollbackUnconfirmedImpact(RoundState r) {
        for(ImpactState p:IMPACTS) {
            if(!p.active||p.confirmed||p.arrowEntityId!=r.entityId)continue;
            FRAME_VISUALS.put(cellKey(p.targetX,p.targetZ),p.maskedStack.copy());
            FRAME_VISUALS.remove(cellKey(p.realX,p.realZ));
            p.active=false;
            r.impactCommitted=false;
        }
    }

    private static ImpactState findStagedImpactForArrow(int arrowId,int realX,int realZ) {
        for(int i=IMPACTS.size()-1;i>=0;i--) {
            ImpactState p=IMPACTS.get(i);
            if(p.active&&!p.confirmed&&p.arrowEntityId==arrowId&&p.realX==realX&&p.realZ==realZ)return p;
        }
        return null;
    }

    private static void cacheBoardFrames(MinecraftClient client) {
        if(client==null||client.world==null||ArrowState.getBoardCenter()==null) return;
        var c=ArrowState.getBoardCenter(); Box box=new Box(c.getX(),c.getY(),c.getZ(),c.getX()+1,c.getY()+1,c.getZ()+1).expand(10,4,10);
        Map<Long,ItemStack> live=new HashMap<>();
        for(ItemFrameEntity f:client.world.getEntitiesByClass(ItemFrameEntity.class,box,e->true)) {
            int x=(int)Math.floor(f.getX())-c.getX(),z=(int)Math.floor(f.getZ())-c.getZ();
            if(!isBoardFrame(f,x,z))continue;
            ItemStack stack=f.getHeldItemStack();
            live.put(cellKey(x,z),stack==null?ItemStack.EMPTY:stack.copy());
            if(stack!=null&&!stack.isEmpty())FRAME_STACK_CACHE.put(cellKey(x,z),stack.copy());
        }
        for(var entry:live.entrySet()) {
            ItemStack previous=LIVE_FRAMES.get(entry.getKey());
            if(previous!=null&&!previous.isEmpty()&&entry.getValue().isEmpty()) {
                PENDING_BREAKS.put(entry.getKey(),worldTick);
            }
        }
        
        Iterator<Map.Entry<Long,Integer>> pending=PENDING_BREAKS.entrySet().iterator();
        while(pending.hasNext()) {
            var entry=pending.next();long key=entry.getKey();ItemStack now=live.get(key);
            if(worldTick-entry.getValue()>8 || (now!=null&&!now.isEmpty())
                    || (now!=null&&confirmFrameBreak((int)(key>>32),(int)key))) pending.remove();
        }
        LIVE_FRAMES.clear();LIVE_FRAMES.putAll(live);
        for(ImpactState p:IMPACTS) if(p.active) {
            ItemStack stack=live.get(cellKey(p.realX,p.realZ));
            if(stack==null)continue; 
            if(stack.isEmpty())p.sawRealEmpty=true;
            else if(p.sawRealEmpty) {
                if(findNewestImpactByTarget(p.targetX,p.targetZ,true)==p)
                    FRAME_VISUALS.put(cellKey(p.targetX,p.targetZ),p.maskedStack.copy());
                p.active=false;p.postPlacement=true;p.sawRealEmpty=false;
            }
        }
        
        
        for(ImpactState p:IMPACTS) if(p.active&&!p.confirmed&&worldTick-p.stagedAtTick>6) {
            FRAME_VISUALS.put(cellKey(p.targetX,p.targetZ),p.maskedStack.copy());
            FRAME_VISUALS.remove(cellKey(p.realX,p.realZ));
            RoundState r=ROUNDS.get(p.arrowEntityId); if(r!=null)r.impactCommitted=false;
            maskDiagnostic="NO FRAME BREAK CONFIRMED";
            p.active=false;
        }
        
        IMPACTS.removeIf(p->!p.active);
    }

    private static double boardPlaneY() {
        return Double.isNaN(boardImpactY) ? ArrowState.getBoardCenter().getY()+1.0 : boardImpactY;
    }

    
    private static void scanBoardGeometry(MinecraftClient client) {
        if(ArrowState.getBoardCenter()==null)return;
        var c=ArrowState.getBoardCenter();
        Box box=new Box(c.getX()-4,c.getY()-3,c.getZ()-4,c.getX()+5,c.getY()+4,c.getZ()+5);
        List<ItemFrameEntity> frames=client.world.getEntitiesByClass(ItemFrameEntity.class,box,f->{
            int x=(int)Math.floor(f.getX())-c.getX(),z=(int)Math.floor(f.getZ())-c.getZ();
            Box bounds=f.getBoundingBox();
            return ArrowState.isSelectableCell(x,z) && Math.abs(f.getY()-c.getY())<=2.5
                    && bounds.maxY-bounds.minY<=.125
                    && bounds.maxX-bounds.minX>=.5 && bounds.maxZ-bounds.minZ>=.5;
        });
        if(frames.isEmpty()){boardFrameCount=0;return;}
        
        
        ItemFrameEntity best=null;int bestCount=0;
        double reference=Double.isNaN(boardFrameY)?c.getY()+1.0:boardFrameY;
        for(ItemFrameEntity candidate:frames) {
            Set<Long> cells=new HashSet<>();
            for(ItemFrameEntity f:frames)if(Math.abs(f.getY()-candidate.getY())<.125)
                cells.add(cellKey((int)Math.floor(f.getX())-c.getX(),(int)Math.floor(f.getZ())-c.getZ()));
            int count=cells.size();
            if(best==null||count>bestCount||(count==bestCount
                    &&Math.abs(candidate.getY()-reference)<Math.abs(best.getY()-reference))) {
                best=candidate;bestCount=count;
            }
        }
        
        if(Double.isNaN(boardFrameY)||(activeRoundCount()==0&&IMPACTS.isEmpty())) {
            boardFrameY=best.getY();boardImpactY=best.getBoundingBox().maxY;
        }
        boardFrameCount=0;
        for(ItemFrameEntity f:frames)if(Math.abs(f.getY()-boardFrameY)<.125)boardFrameCount++;
    }

    private static boolean isBoardFrame(ItemFrameEntity frame,int x,int z) {
        if(Double.isNaN(boardFrameY)||!ArrowState.isSelectableCell(x,z))return false;
        Box bounds=frame.getBoundingBox();
        return Math.abs(frame.getY()-boardFrameY)<.125
                && bounds.maxY-bounds.minY<=.125
                && bounds.maxX-bounds.minX>=.5 && bounds.maxZ-bounds.minZ>=.5;
    }

    public static String getMaskDiagnostic() {
        if(!ArrowState.isEnabled())return "OFF";
        if(ArrowState.getBoardCenter()==null)return "LEARN BOARD";
        if(boardFrameCount==0)return "NO BOARD FRAMES FOUND";
        return maskDiagnostic+" | Frames: "+boardFrameCount;
    }

    public static ItemFrameEntity redirectedPlacementFrame(ItemFrameEntity clicked) {
        if(clicked==null||!ArrowState.isEnabled()||!ArrowState.isMaskingEnabled()||ArrowState.getBoardCenter()==null) return null;
        var c=ArrowState.getBoardCenter(); int x=(int)Math.floor(clicked.getX())-c.getX(),z=(int)Math.floor(clicked.getZ())-c.getZ();
        if(!isBoardFrame(clicked,x,z))return null;
        ImpactState p=findNewestImpactByTarget(x,z,true); if(p==null||(p.targetX==p.realX&&p.targetZ==p.realZ)) return null;
        MinecraftClient client=MinecraftClient.getInstance(); if(client.world==null)return null;
        Box box=new Box(c.getX(),c.getY(),c.getZ(),c.getX()+1,c.getY()+1,c.getZ()+1).expand(10,4,10);
        for(ItemFrameEntity f:client.world.getEntitiesByClass(ItemFrameEntity.class,box,e->true)) {
            int fx=(int)Math.floor(f.getX())-c.getX(),fz=(int)Math.floor(f.getZ())-c.getZ();
            if(isBoardFrame(f,fx,fz)&&fx==p.realX&&fz==p.realZ) { ItemStack held=f.getHeldItemStack(); return held==null||held.isEmpty()?f:null; }
        }
        return null;
    }

    public static ItemStack frameRenderStack(ItemFrameEntity frame,ItemStack real) {
        if(frame==null||!ArrowState.isEnabled()||!ArrowState.isMaskingEnabled()||ArrowState.getBoardCenter()==null)return real;
        var c=ArrowState.getBoardCenter(); int x=(int)Math.floor(frame.getX())-c.getX(),z=(int)Math.floor(frame.getZ())-c.getZ();
        if(!isBoardFrame(frame,x,z))return real;
        
        
        
        for(int i=IMPACTS.size()-1;i>=0;i--) {
            ImpactState p=IMPACTS.get(i);
            if(!p.active)continue;
            if(x==p.realX&&z==p.realZ)return p.realVisual.copy();
            if(x==p.targetX&&z==p.targetZ)return ItemStack.EMPTY;
        }
        ItemStack visual=FRAME_VISUALS.get(cellKey(x,z));
        return visual==null?real:visual.copy();
    }


    public static ItemStack droppedItemRenderStack(ItemEntity item) {
        ImpactState p=impactForDrop(item); if(p==null||p.maskedStack.isEmpty())return null;
        ArrowInventoryMask.observeImpactDrop(MinecraftClient.getInstance(),item,p.maskedStack);
        return p.maskedStack.copy();
    }

    public static void applyDroppedItemRenderMask(ItemEntity item,ItemEntityRenderState state) {
        ImpactState p=impactForDrop(item); if(p==null||state==null)return;
        var c=ArrowState.getBoardCenter();
        double rx=c.getX()+p.realX+.5, rz=c.getZ()+p.realZ+.5;
        double mx=c.getX()+p.targetX+.5, mz=c.getZ()+p.targetZ+.5;

        
        
        
        
        
        
        
        
        state.x += mx-rx;
        state.z += mz-rz;
    }

    private static ImpactState impactForDrop(ItemEntity item) {
        if(item==null||!ArrowState.isEnabled()||!ArrowState.isMaskingEnabled()||ArrowState.getBoardCenter()==null)return null;
        var c=ArrowState.getBoardCenter(); ImpactState best=null; double bestD=Double.POSITIVE_INFINITY;
        for(int i=IMPACTS.size()-1;i>=0;i--) { ImpactState p=IMPACTS.get(i); if(!p.active)continue;
            double rx=c.getX()+p.realX+.5,rz=c.getZ()+p.realZ+.5,dx=item.getX()-rx,dz=item.getZ()-rz,dy=item.getY()-(boardPlaneY()),d=dx*dx+dz*dz;
            if(d<=2.25&&Math.abs(dy)<=2.5&&d<bestD){best=p;bestD=d;}
        } return best;
    }

    private static void updateAutomaticTarget(RoundState r) {
        if(r.impactCommitted||!r.haveSegment||ArrowState.getBoardCenter()==null)return; double py=boardPlaneY(),dy=r.y1-r.y0; if(Math.abs(dy)<=1e-7)return;
        double t=(py-r.y0)/dy; if(t<-.35||t>1.75)return; int[] cell=resolvePhysicalLandingCell(r.x0+(r.x1-r.x0)*t,r.z0+(r.z1-r.z0)*t);
        
        
        
        
        if(cell==null){if(!r.targetReady)r.maskedStack=ItemStack.EMPTY;return;}
        r.predictedTrueX=cell[0]; r.predictedTrueZ=cell[1]; r.havePredictedTrue=true;
        r.endX=r.x0+(r.x1-r.x0)*t;r.endZ=r.z0+(r.z1-r.z0)*t;
        chooseAutomaticTarget(r,cell[0],cell[1]);
    }

    private static int[] resolvePhysicalLandingCell(double wx,double wz) {
        if(ArrowState.getBoardCenter()==null)return null; var c=ArrowState.getBoardCenter(); double lx=wx-c.getX(),lz=wz-c.getZ(),best=Double.POSITIVE_INFINITY; int bx=0,bz=0;boolean found=false;
        for(int z=-3;z<=3;z++)for(int x=-3;x<=3;x++){if(!ArrowState.isSelectableCell(x,z))continue;double dx=lx-(x+.5),dz=lz-(z+.5);if(Math.abs(dx)>.500001||Math.abs(dz)>.500001)continue;double d=dx*dx+dz*dz;if(!found||d<best-1e-12){found=true;best=d;bx=x;bz=z;}}
        return found?new int[]{bx,bz}:null;
    }

    private static void chooseAutomaticTarget(RoundState r,int trueX,int trueZ) {
        if(r.impactCommitted)return;
        
        
        boolean wasMasked=r.maskedRound;
        r.maskedRound=!ArrowState.isHostWinCell(trueX,trueZ);
        if(!r.maskedRound) {
            maskDiagnostic="NATURAL HOST WIN";
            r.targetX=trueX;
            r.targetZ=trueZ;
            r.targetReady=true;
            r.maskedStack=ItemStack.EMPTY;
            ArrowState.setAutoMaskTarget(trueX,trueZ);
            FRAME_VISUALS.remove(cellKey(trueX,trueZ));
            return;
        }

        Set<Long> reserved=new HashSet<>();
        for(RoundState other:ROUNDS.values())if(other!=r&&!other.finished&&other.targetReady&&other.maskedRound)reserved.add(cellKey(other.targetX,other.targetZ));
        for(ImpactState p:IMPACTS)if(p.active)reserved.add(cellKey(p.targetX,p.targetZ));
        
        
        long currentKey=cellKey(r.targetX,r.targetZ);
        ItemStack current=FRAME_VISUALS.getOrDefault(currentKey,LIVE_FRAMES.getOrDefault(currentKey,ItemStack.EMPTY));
        if(wasMasked&&r.targetReady&&ArrowState.isHostWinCell(r.targetX,r.targetZ)
                &&!reserved.contains(currentKey)&&!current.isEmpty())return;
        r.targetReady=false;
        r.maskedStack=ItemStack.EMPTY;
        int bx=0,bz=0,best=Integer.MAX_VALUE;boolean found=false;
        for(int z=-3;z<=3;z++)for(int x=-3;x<=3;x++) {
            long key=cellKey(x,z);
            if(!ArrowState.isSelectableCell(x,z)||!ArrowState.isHostWinCell(x,z)||reserved.contains(key))continue;
            ItemStack visual=FRAME_VISUALS.getOrDefault(key,LIVE_FRAMES.getOrDefault(key,ItemStack.EMPTY));
            if(visual.isEmpty())continue;
            int dx=x-trueX,dz=z-trueZ,d=dx*dx+dz*dz;
            if(!found||d<best){found=true;best=d;bx=x;bz=z;}
        }
        if(!found){maskDiagnostic="NO AVAILABLE WINNING FRAME";return;}
        maskDiagnostic="HOST WIN MASK READY";
        r.targetX=bx;r.targetZ=bz;r.targetReady=true;ArrowState.setAutoMaskTarget(bx,bz);
        long key=cellKey(bx,bz);
        r.maskedStack=FRAME_VISUALS.getOrDefault(key,LIVE_FRAMES.getOrDefault(key,ItemStack.EMPTY)).copy();
        if(!r.maskedStack.isEmpty())r.maskedStack.setCount(1);
        if(!r.havePredictedTrue) {
            var c=ArrowState.getBoardCenter();r.endX=c.getX()+trueX+.5;r.endZ=c.getZ()+trueZ+.5;
        }
    }

    private static ImpactState findNewestImpactByTarget(int x,int z,boolean activeOnly){for(int i=IMPACTS.size()-1;i>=0;i--){ImpactState p=IMPACTS.get(i);if(p.targetX==x&&p.targetZ==z&&(!activeOnly||p.active)&&(p.active||p.postPlacement))return p;}return null;}
    private static long cellKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static int activeRoundCount(){int n=0;for(RoundState r:ROUNDS.values())if(!r.finished)n++;return n;}
    public static boolean isRoundTargetReady(){for(RoundState r:ROUNDS.values())if(!r.finished&&r.targetReady)return true;return false;}

    public static void clearTransient(){ROUNDS.clear();IMPACTS.clear();PENDING_BREAKS.clear();worldTick=0;boardFrameY=Double.NaN;boardImpactY=Double.NaN;boardFrameCount=0;maskDiagnostic="WAITING FOR SHOT";FRAME_STACK_CACHE.clear();LIVE_FRAMES.clear();FRAME_VISUALS.clear();ArrowInventoryMask.clear();ArrowState.setTrackingCount(0);}
}
