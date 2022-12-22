package simpledb.common.lockResorce;

import simpledb.storage.PageId;
import simpledb.storage.Tuple;

/*
* 需要考虑不同的锁的粒度
* */
public interface LockType {

    public boolean isTupleType();

    public boolean isPageType();

    public boolean isTableType(); // 锁住整个HeapFile

    public PageId getPageId();

}
