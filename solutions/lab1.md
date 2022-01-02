首先是网上找到的数据库整体架构

![img](https://gitee.com/oldataraxia/pic-bad/raw/master/img/20200103180012189.png)

![image-20220102164835642](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220102164835642.png)

要实现的类:

* `Tuple`: 元组, 相当于数据库中的一条记录. 其成员有:
    * `TableDesc`: 表示当前表的元信息
    * `List<Fields>`: 表示当前记录中存储的字段
    * `recoreId`: 表示当前记录在磁盘上的位置

* `TupleDesc`: 表示一张表(当然也是其中的元组)的元信息.

    * 其中实现了一个内部类`TDItem`, 表示每一个Item的信息, 成员包括`fieldType`和`fieldName`.
    * 主类的成员就是`List<TDItem>`

* `Catalog`: a list of the tables and schemas of the tables that are currently in the database, 表示整个数据库的schema关系...之类的.

    * 内部类Table表示一张表的信息, 对应一个name和file...

      ```java
      public static class Table {
          private DbFile file;
          private String name;
          private String pkeyField;
      
          public Table(DbFile file, String name, String pkeyField) {
              this.file = file;
              this.name = name;
              this.pkeyField = pkeyField;
          }
      
          public DbFile getFile() {
              return this.file;
          }
      
          public String getName() {
              return this.name;
          }
      
          public String getPkeyField() {
              return this.pkeyField;
          }
      
          public int getFileId() {
              return this.file.getId();
          }
      }
      ```

    * 这里可以建立两个`map`来存储TableID, TableName与Table的对应关系

* `BufferPool`: 在内存中缓存最近从磁盘(这里是DbFile)读取的页面, 由固定数量的pages组成, 数量由构造函数中的参数`numPages`决定.当前只需要实现构造函数和`getPage`方法. 如果当前page数量超过最大值, 抛出一个`DBException`.
    * 实现`getPage`方法, 通过`PageID`查找当前page是否在`BufferPool`中, 如果不在就从Catalog中获得DbFIle, 之后调用ReadPage方法来得到Page信息

```java
public Page getPage(TransactionId tid, PageId pid, Permissions perm)
    throws TransactionAbortedException, DbException {
    if(this.pages.containsKey(pid.hashCode())) {
        return this.pages.get(pid.hashCode());
    } else {
        DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
        if(dbFile == null) {
            throw new DbException("Page does not exist");
        }
        this.pages.put(pid.hashCode(), dbFile.readPage(pid));
        return this.pages.get(pid.hashCode());
    }
}
```

* `HeapPage`, `HeapFile`和`recordId`. 算是比较核心的地方了...

    * `recoreId`: 对于一个tuple的物理索引. 它需要保存两个属性, 即PageId和tupleNumber. 第一个参数被描述为对应在哪个page, 第二个参数则是描述它具体在这个page的哪个地方

    * `HeapPageId`, 实现了pageId接口, 负责标识...每个HeapPageId需要一个`tableId`(用于标识属于哪个table)和`pageNo`(应该是用于标识当前页对应物理上的哪个页)

    * `HeapPage`: 实现了Page接口, 就相当于一张数据页. 是HeapFile的基本组成的单位. 每个page由固定数量的字节组成, 用于存储tuple和自己的header

        * `HeapPage`中数据的组织:  有两个成员, `byte[] header`和`Tuple[] tuples`, 而header以bitmap的形式来表示第i个slot是否有效, 最低位表示第一个.

        * 构造: 通过传入的pageid和`data[]`(磁盘上的文件格式)进行构造,

        * 判断slot是否被使用:

          ```java
          public boolean isSlotUsed(int i) {
              int quot = i / 8;
              int remain = i % 8;
              return (header[quot]&(1 << remain)) != 0;
          }
          ```



    * 计算一个Page能存多少条tuple和header占多少字节: 说明文档里有公式:

    ```
    // 每个tuple 需要tuplesize * 8 bit 存数据, 1bit在header里
    _tuples per page_ = floor((_page size_ * 8) / (_tuple size_ * 8 + 1)) 
    // header需要的字节数, 即tuplePerPage / 8 就行了
    headerBytes = ceiling(tupsPerPage/8)
    ```
    实现
    ```java
    private int getNumTuples() {        
        return (int) Math.floor((BufferPool.getPageSize() * 8.0 ) / (td.getSize() * 8 + 1));
    }
    
    private int getHeaderSize() {        
        return (int) Math.ceil(getNumTuples() / 8.0);
    }
    ```

    * 迭代器: 首先遍历得到`useableTuples`, 之后返回它的迭代器...

    ```java
    public Iterator<Tuple> iterator() {
        List<Tuple> useableTuples = new ArrayList<Tuple>();
        for(int i = 0; i < numSlots; i++) {
            if(isSlotUsed(i)) {
                useableTuples.add(tuples[i]);
            }
        }
        return useableTuples.iterator();
    }
    ```

* `HeapFile`: 实现了DbFile接口, 直接与磁盘交互, 一个DbFIle就是一张表, 里面有很多的pages. 其成员只有tupleDesc(标识表的元数据)和file对象...

    * `readPage`: 根据PageId去磁盘里读数据...根据pageId来获得pageno, pageno * pageSize得到偏移量, 然后到文件里去读就行了
    * 迭代器: 实现一个迭代器用于遍历所有的tuple, 所有的page里的tuple... 对于单独page里的tuple倒是挺好遍历的, 直接用`page.iterator()`就好了(`HeapPage`里也实现过), 当前page中读完了就去通过`BufferPool`来获取下一个Page(而不是从文件里读的方式, 不是每次访问都要去访问磁盘, 需要通过BufferPool缓存该缓存的东西)

```java
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
            HeapPageId heapPageId = new HeapPageId(HeapFile.this.getId(), pageNo);
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_ONLY);
            return page.iterator();
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
```



* Seqscan: 就是封装了一下上一题实现的Iterator, 注意运算时支持"表的别名"的, getTupleDesc时需要重构原来的每个字段的名字, 为alias.table

```java
public class SeqScan implements OpIterator {

    private static final long serialVersionUID = 1L;
    TransactionId tid;
    int tableid;
    String tableAlias;
    DbFileIterator iterator;

    /**
     * Creates a sequential scan over the specified table as a part of the
     * specified transaction.
     *
     * @param tid
     *            The transaction this scan is running as a part of.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public SeqScan(TransactionId tid, int tableid, String tableAlias) {
        this.tid = tid;
        this.tableid = tableid;
        this.tableAlias = tableAlias;
    }

    /**
     * @return
     *       return the table name of the table the operator scans. This should
     *       be the actual name of the table in the catalog of the database
     * */
    public String getTableName() {
        return Database.getCatalog().getTableName(tableid);
    }

    /**
     * @return Return the alias of the table this operator scans.
     * */
    public String getAlias() {
        return this.tableAlias;
    }

    /**
     * Reset the tableid, and tableAlias of this operator.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public void reset(int tableid, String tableAlias) {
        this.tableid = tableid;
        this.tableAlias = tableAlias;
    }

    public SeqScan(TransactionId tid, int tableId) {
        this(tid, tableId, Database.getCatalog().getTableName(tableId));
    }

    public void open() throws DbException, TransactionAbortedException {
        HeapFile heapFile = (HeapFile) Database.getCatalog().getDatabaseFile(tableid);
        this.iterator = heapFile.iterator(tid);
        iterator.open();
    }

    /**
     * Returns the TupleDesc with field names from the underlying HeapFile,
     * prefixed with the tableAlias string from the constructor. This prefix
     * becomes useful when joining tables containing a field(s) with the same
     * name.  The alias and name should be separated with a "." character
     * (e.g., "alias.fieldName").
     *
     * @return the TupleDesc with field names from the underlying HeapFile,
     *         prefixed with the tableAlias string from the constructor.
     */
    public TupleDesc getTupleDesc() {
        TupleDesc tupleDesc = Database.getCatalog().getTupleDesc(this.tableid);
        Type[] types = new Type[tupleDesc.numFields()];
        String[] fieldNames = new String[tupleDesc.numFields()];
        for(int i = 0; i < tupleDesc.numFields(); i++) {
            types[i] = tupleDesc.getFieldType(i);
            fieldNames[i] = this.tableAlias + "." + tupleDesc.getFieldName(i);
        }
        return new TupleDesc(types, fieldNames);
    }

    public boolean hasNext() throws TransactionAbortedException, DbException {
        return iterator.hasNext();
    }

    public Tuple next() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        return iterator.next();
    }

    public void close() {
        iterator.close();
    }

    public void rewind() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        iterator.rewind();
    }
}

```

















