package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    File file;
    TupleDesc tupleDesc;
    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.file = f;
        this.tupleDesc = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return this.file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return file.getAbsoluteFile().hashCode(); // file.AbsoluteFile() 返回文件的绝对路径
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return this.tupleDesc;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid){
        int tableid = pid.getTableId();
        int pageno = pid.getPageNumber();
        int pageSize = BufferPool.getPageSize();
        byte[] data = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            if((long) (pageno + 1) * pageSize > raf.length()) {
                throw new IllegalArgumentException("PageId " + pid + " does not exist");
            }
            data = new byte[pageSize];
            raf.seek((long) pageno * pageSize); // seek the offset
            raf.read(data, 0, pageSize);
            return new HeapPage((HeapPageId) pid, data);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert raf != null;
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        throw new IllegalArgumentException("PageId " + pid + " does not exist");
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(page.getId().getPageNumber() * Database.getBufferPool().getPageSize());
        raf.write(page.getPageData());
        raf.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) Math.ceil(this.file.length() * 1.0 / Database.getBufferPool().getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        List<Page> dirtyPages = new ArrayList<>();
        // 遍历所有的Page
        for (int i = 0; i < numPages(); i++) {
            HeapPageId heapPageId = new HeapPageId(this.getId(), i); // 构建HeapPageId
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_WRITE); // 通过BufferPool来得到Page
            // 找到有empty slot的page, 这里理论上是需要加锁的...
            if (page.getNumEmptySlots() != 0) {
                page.markDirty(true, tid);
                page.insertTuple(t);
                dirtyPages.add(page);
                // 不需要把脏页写盘, 之后在BufferPool中写盘
                break;
            }
        }
        // 如果没有空Page
        if (dirtyPages.isEmpty()) {
            // 创建一个新Page
            HeapPageId pageid = new HeapPageId(this.getId(), this.numPages());
            HeapPage page = new HeapPage(pageid, HeapPage.createEmptyPageData());

            // 重复之前的从BufferPool读取并修改
            // page = (HeapPage) Database.getBufferPool().getPage(tid, pageid, Permissions.READ_WRITE);
            page.markDirty(true, tid);
            page.insertTuple(t);
            dirtyPages.add(page);

            // 把新建立的Page写盘, 附加在之前的file后面
            writePage(page);
        }
        // System.out.println(dirtyPages.size());
        return dirtyPages;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        ArrayList<Page> dirtyPages = new ArrayList<>();
        PageId heapPageId = t.getRecordId().getPageId();
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_WRITE);

        page.markDirty(true, tid);
        page.deleteTuple(t);
        dirtyPages.add(page);
        return dirtyPages;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        return new HeapFileIterator(tid);
    }

    public class HeapFileIterator implements DbFileIterator {

        private int currentPageNo;
        private Iterator<Tuple> iterator;
        TransactionId tid;

        public HeapFileIterator(TransactionId tid) {
            this.tid = tid;
        }

        private Iterator<Tuple> tupleIteratorUtil(int pageNo) throws DbException, TransactionAbortedException {
            if(pageNo < 0 || pageNo >= HeapFile.this.numPages()) {
                throw new DbException("Page number out of bound");
            }
            HeapPageId heapPageId = new HeapPageId(HeapFile.this.getId(), pageNo); // 构建HeapPageId
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_ONLY); // 通过BufferPool来得到Page
            return page.iterator(); // 返回page.iterator
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            currentPageNo = 0;
            iterator = tupleIteratorUtil(currentPageNo);
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if(iterator != null) {
                if(iterator.hasNext()) {
                    return true;
                } else {
                    // time to update iterator for next page
                    if(currentPageNo >= HeapFile.this.numPages() - 1) {
                        return false;
                    }
                    iterator = tupleIteratorUtil(++currentPageNo);
                    return hasNext();
                }
            }
            return false;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if(hasNext()) {
                return iterator.next();
            }
            throw new NoSuchElementException();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            currentPageNo = 0;
            iterator = tupleIteratorUtil(currentPageNo);
        }

        @Override
        public void close() {
            iterator = null;
        }
    }

}

