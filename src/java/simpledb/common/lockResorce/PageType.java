package simpledb.common.lockResorce;

import simpledb.storage.PageId;

/*
* used for lockResources
* */
public class PageType implements LockType {

    PageId pageId;

    PageType(PageId id) {
        this.pageId = id;
    }

    @Override
    public boolean isTupleType() {
        return false;
    }

    @Override
    public boolean isPageType() {
        return true;
    }

    @Override
    public boolean isTableType() {
        return false;
    }

    @Override
    public PageId getPageId() {
        return this.pageId;
    }


}
