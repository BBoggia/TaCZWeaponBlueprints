package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.HashSet;
import java.util.Set;

/** Tracks complete reload attempts; an older completion cannot release a newer attempt. */
public final class StockReloadBarrier {
    private final Set<Long> active=new HashSet<>();
    private long generation;
    private boolean pending,failed,ready;
    public synchronized long begin(){long id=++generation;active.add(id);pending=true;ready=false;return id;}
    public enum Completion { IGNORED, WAITING, COMMIT, RECOVER }
    /** Only the last successful completion may replace the committed snapshots. */
    public synchronized Completion finish(long id,boolean failure){
        if(!active.remove(id))return Completion.IGNORED;
        failed|=failure;
        if(!active.isEmpty())return Completion.WAITING;
        ready=!failed;
        return failed?Completion.RECOVER:Completion.COMMIT;
    }
    public synchronized long generation(){return generation;}
    public synchronized boolean pending(){return pending;}
    public synchronized void recovered(long id){
        if(id==generation&&active.isEmpty()){failed=false;ready=true;}
    }
    public synchronized void initialized(){if(active.isEmpty()&&!failed)ready=true;}
    public synchronized void settle(){if(ready&&active.isEmpty()){pending=false;ready=false;}}
    public synchronized void reset(boolean loading){generation++;active.clear();pending=loading;failed=false;ready=false;}
}
