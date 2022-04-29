算是重新整理成类似接口文档的形式. 不需要贴代码, 而是可以配合代码来看, 这是我想达成的目的

## Tuple and Tupledesc

- src/java/simpledb/storage/TupleDesc.java
- src/java/simpledb/storage/Tuple.java

---

* `TupleDesc.java`:  相当于对一个tuple的描述. 其中有一个TDItem内部类, 表示tuple中的一列, 其中又有`fieldName`和`fieldType`两个属性. `fieldName`是String就表示列的名字, `fieldType`是`simpledb.common.type`类型, 是一个枚举类型, 包括INT_TYPE和STRING_TYPE. 整个TupleDesc主体就是一个`List<TDItem>`
  * 其中有一个`getSize`方法, 因为你db所有的字段都是定长的所以字节把所有的fieldType的len累加起来就行了. 而且从后面实现看一个tuple的field应该是直接密铺在文件里的...
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
  * numSlots int: slot的数量, 后面需要算
  * td TupleDesc: 对当前Page所在的表的schema的描述
  * tuples Tuple[]: 当前page中的tuple们
  *

![image-20220428224942264](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220428224942264.png)

* 一个Page分为Header和slot, Header用01来标识slot的使用情况, slot中插入tuple.

  * 每个page中tuple的个数为: `tuples per page_ = floor((page size * 8) / (tuple size * 8 + 1))`, 其中`page_size * 8`是每一页有多少二进制位, `tuple size * 8 + 1`表示一个tuple占用的二进制位(header中也要占用一个二进制位). 这一点对应`getNumTuples`方法

  * Header中bitmap的个数即`headerBytes = ceiling(tuplePerPage / 8)`. 这一点对应`getHeaderSize()`方法
  * `isSlotUsed()`就是判断header中第`i`个字节是否是1, 判断的时候可以用tricky的左移技巧:

  ```java
  public boolean isSlotUsed(int i) {
      int quot = i / 8; // 在header的哪个byte上
      int remain = i % 8; // 在哪一位
      return (header[quot] & (1 << remain)) != 0; // 00000100 & 需要的比如第三位 != 0
  }
  ```

* 构造方法`HeapPage(HeapPageId id, byte[] data)` 接受的data就是一个pagefile中的内容, 通过读取data中的内容来构建header, 之后通过一个工具函数`readNextTuple`来读取`data`中的`Tuple`信息

  * `readNextTuple`方法首先根据之前提到的`isSlotUsed`方法来通过`header`判断当前slot是否有一个tuple, 若不存在则返回null, 否则就创建新的Tuple并通过`TupleDesc`来
  * 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000	`	~获得每个Field的信息, 接着通过`Type.parse`方法从数据中得到~



































* 另外`readNextTuple`中遇到空Tuple的处理方法是直接跳过定长的一个Tuple的长度

> 这里之前提到过Field和Type的区别, Type表示的是数据库内部的一个数据类型, Field是数据类型 + 当前列中的具体数据. 两个类的功能是不同的.

* `getPageData`方法是与解析相反的, 把Page上的Tuple序列化到Page文件中, 最后返回一个`Byte[]`. 首先把`Header[]`原封不动地写入就可以了, 之后遍历所有的Tuples, 如果当前位置没有Tuple就给所有的位置写入0, 否则再遍历所有的Field, 直接调用Field的`serialize`方法来序列化. 最后Page没满的话再补0

> int是直接写入的, String是首先写入长度在写入String本身, 最后定长后面没用上的地方补0

* deletTuple(Tuple t): 直接给一个tupe就很奇怪, 通过`Tuple.getRecordid.getTupleNumber()`来定位Tuple并删除
* `Iterator<Tuple> iterator()`:  要返回一个遍历所有的Tuples的iterator, 那没有办法了只能首先遍历所有Tuples并把存在的拿出来存到一个arrayList里, 之后返回它的iterator.

## HeapFile

storage/HeapFile.java

这里一个HeapFile对应的一个File就是一个数据库了... 并不会做比如切片划分啥的

其中`readPage(PageId pid)`方法需要从file中读取指定的Page, 首先计算需要的Page的偏移量, 之后读取pageSize大小的数据并传入HeapPage的构造函数

`writePage`同样是首先计算要写入的Page的偏移量, 之后调用`page.getPageData`直接写入数据就好了

`iterator`需要一个遍历file内部的iterator, 这里实现一个内部类`HeapFileIterator`, 大致做的事情就是迭代返回各个Page的Iterator. 其中rewind的意思是重置迭代器到初始的位置

## SeqScan

/execution/Squscan.java 顺序扫描的算子

算子实现了OpIterator接口, 其中定义了`open`, `hasNext`, `next`, `rewind`和`getTupleDesc`, 大概是火山模型吧(

顺序扫描基本上就是把之前实现的HeapFileIterator包装一下

唯一需要注意的是`getTupleDesc`时需要构建新的`fieldName`数组为`tableAlias.原来的fieldname`



