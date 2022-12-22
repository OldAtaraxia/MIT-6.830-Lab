package simpledb.common;

import simpledb.common.lockResorce.LockType;
import simpledb.storage.PageId;
import simpledb.transaction.TransactionId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/*
* 只考虑Page粒度的锁的LockManager
* */
public class PageParticalLockManager implements LockManager{

    Map<PageId, TransactionId> exclusiveLockstatus = new ConcurrentHashMap<>();
    Map<PageId, List<TransactionId>> sharedLockstatus = new ConcurrentHashMap<>();

    PageParticalLockManager() {

    }

    @Override
    public boolean sharedLock(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());

        return false;
    }

    @Override
    public boolean releaseShared(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());
        return false;
    }

    @Override
    public boolean exclusiveLock(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());
        return false;
    }

    @Override
    public boolean releaseExclusive(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());
        return false;
    }

    @Override
    public boolean transactionHasSharedLock(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());
        return false;
    }

    @Override
    public boolean transcationHasExclusiveLock(TransactionId transactionId, LockType resource) {
        assert (resource.isPageType());
        return false;
    }
}
