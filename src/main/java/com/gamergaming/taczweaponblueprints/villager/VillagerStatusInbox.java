package com.gamergaming.taczweaponblueprints.villager;
import com.gamergaming.taczweaponblueprints.network.SyncVillagerTradeStatusPacket;
import java.util.Optional;
/** Small menu-identity/revision guard, independent of client rendering and gameplay authority. */
public final class VillagerStatusInbox {
    private Object menu;
    private SyncVillagerTradeStatusPacket packet;
    private long newestSession;
    public boolean accept(Object open,int containerId,SyncVillagerTradeStatusPacket next){
        if(open==null||next.containerId()!=containerId||next.session()<newestSession
                ||next.session()==newestSession&&open!=menu
                ||packet!=null&&next.session()==packet.session()&&next.revision()<=packet.revision())return false;
        menu=open;packet=next;newestSession=next.session();return true;
    }
    public Optional<SyncVillagerTradeStatusPacket> current(Object open){return open!=null&&open==menu?Optional.ofNullable(packet):Optional.empty();}
    public void close(){menu=null;packet=null;}
    public void disconnect(){close();newestSession=0;}
}
