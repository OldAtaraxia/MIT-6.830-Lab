package simpledb.common;

import simpledb.common.lockResorce.LockType;
import simpledb.transaction.TransactionId;

/*  需要一个LockManager, 内容为申请锁, 释放锁, 查看指定事务是否上锁
    申请的资源用定义的抽象类表示
*/
public interface LockManager {

    // 请求与释放shared lock, 粒度怎么决定呢
    public boolean sharedLock(TransactionId transactionId, LockType resource);

    public boolean releaseShared(TransactionId transactionId, LockType resource);

    // 请求与释放exclusive lock
    public boolean exclusiveLock(TransactionId transactionId, LockType resource);

    public boolean releaseExclusive(TransactionId transactionId, LockType resource);

    // 事务是否持有shared lock
    public boolean transactionHasSharedLock(TransactionId transactionId, LockType resource);

    // 事务是否持有exclusive lock
    public boolean transcationHasExclusiveLock(TransactionId transactionId, LockType resource);
}
