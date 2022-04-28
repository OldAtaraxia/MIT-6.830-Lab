算是重新整理成类似接口文档的形式. 不需要贴代码, 而是可以配合代码来看, 这是我想达成的目的

## Tuple and Tupledesc

- src/java/simpledb/storage/TupleDesc.java
- src/java/simpledb/storage/Tuple.java

---

* `TupleDesc.java`:  相当于对一个tuple的描述. 其中有一个TDItem内部类, 表示tuple中的一列, 其中又有`fieldName`和`fieldType`两个属性. `fieldName`是String就表示列的名字, `fieldType`是`simpledb.common.type`类型, 是一个枚举类型, 包括INT_TYPE和STRING_TYPE. 整个TupleDesc主体就是一个`List<TDItem>`

* `Tuple.java`: 其主要成员是一个`TupleDesc`; 一个`List<Field>`来存储具体的字段, 其中Field是一个接口, 实现类有IntField和StringField两个

```java
TupleDesc tableSchema; // 描述当前表的schema
List<Field> fields; // 存储字段
RecordId recordId;
```

## Catalog

* common/catalog

其内部有一个Table类, Table 表示一张表，其中有三个属性，分别是 file，name 和 pkeyField 。其中 pkeyField 表示主键，name 表示表的名字，而 file 则是一个 DbFile 对象。

关于DbFile: 一个接口, 表示database files on disk.  其实现类有heapFile和BTreeFile. 看之后的行为应该就是一个disk和memory之间的一个读写接口. 内部会存很多的Page. 之后可以看HeapFile & HeapPage

![image-20220428151740698](C:/Users/Lenovo/AppData/Roaming/Typora/typora-user-images/image-20220428151740698.png)

之后在主类中用`hashmap`来存Table, 这里因为同时用到nameToTable和idToTable所以我建了两个hashtable...

```java
private Map<String, Table> nameToTable;
private Map<Integer, Table> idToTable;
private List<Table> tables;
```

## BufferPool.getPage()

BufferPool内部需要维护numPages, 以及`pageId.hashcode()`到`Page`的映射关系. 为了并发安全要用`concurrentHashcode`

```java
private int numPages;
private ConcurrentHashMap<Integer, Page> pages;
```

`getPage`: 若对应page已经被缓存则直接返回, 否则需要调用`Catalog`的方法首先获得DbFile, 然后利用其来`getPage`, 并将其放入bufferpool的hashtable

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

## HeapPageId, RecordId and HeapPage

* HeapPageId: HeapPage的标识符, 成员有tableId和pageNo(table中的page编号)
* RecordId: 一条record/tuple的唯一标识符. 在Tuple中就有这个成员类. 内部有`PageId pid`和`int tupleno`, 分别表示tuple所在的Page和在Page内部当前tuple的编号

> 话说为什么不叫TupleId呢...

* HeapPage:
    * HeapPageId pid: 用于标识当前的Page
    *

![image-20220428224942264](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220428224942264.png)

* 一个Page分为Header和slot, Header用01来标识slot的使用情况, slot中插入tuple.

    * 每个page中tuple的个数为: `tuples per page_ = floor((page size * 8) / (tuple size * 8 + 1))`, 其中`page_size * 8`是每一页有多少二进制位, `tuple size * 8 + 1`表示一个tuple占用的二进制位(header中也要占用一个二进制位). 这一点对应`getNumTuples`方法

    * Header中bitmap的个数即`headerBytes = ceiling(tuplePerPage / 8)`. 这一点对应`getHeaderSize()`方法

