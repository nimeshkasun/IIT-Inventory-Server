package iit.inv.sync.lock.client;

public interface DistributedTxListener {
    void onGlobalCommit();
    void onGlobalAbort();
}
