实现表的修改(插入和删除record), 选择, 连接和聚合



## exercise 1

实现

* src/java/simpledb/execution/Predicate.java
* src/java/simpledb/execution/JoinPredicate.java
* src/java/simpledb/execution/Filter.java
* src/java/simpledb/execution/Join.java

`predicate`, "谓词", 就是用来作比较的, 这里有一个枚举类`Op`一共有EQUALS, GREATER_THAN, LESS_THAN, LESS_THAN_OR_EQ, GREATER_THAN_OR_EQ, LIKE, NOT_EQUALS等等

其构造函数中的三个参数分别为: 传入的tuple要用来比较的field的编号, operator, 要用来比较的值

这里的比较是要用`Field`接口的compare方法

`JoinPredicate`是用在连接中的, 与predicate基本一致

关于算子的实现, 所有的算子都继承自父类`Operator`, 而`operator`实现了接口`OpIterator`. `Optrerator`规定的方法有这些:

![image-20220501162913886](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220501162913886.png)

Operator中增加了两个成员变量`Tuple next`和`boolean open`. 核心方法`hasNext`与`next`的实现为if next == null -> next = fetchNext(). 而fetchNext是一个抽象方法需要子类来实现. 除此之外hasNext中若`fetchNext`返回null则`hasNext`也会返回null

Filter维护一个Predicate, 每次读取child的tuple并通过Predicate进行判断

Join这里先只实现了基本的算是simple loop join, 后面想一下怎么实现更好的其他算法

> 说起来这个`project`算子返回的tuple为什么要复用child中读到的tuple的RecordId呢

## exercise 2

实现GroupBy操作

- src/java/simpledb/execution/IntegerAggregator.java
- src/java/simpledb/execution/StringAggregator.java
- src/java/simpledb/execution/Aggregate.java

> 这里的`GroupBy`是可以不指定的, 就像SQL里可以直接用select count(*) from xxx一样...相当于所有的tuple都在同一组内

`IntegerAggregator`和`StringAggregator`实现了接口`Aggregator`, 其中定义了聚合用的operator, 包括MIN, MAX, SUM, AVG, COUNT等, 还定义了一个常量`NO_GROUPING`

这里实现的时候参考了"策略模式", 即在aggregator中由handler来处理具体逻辑, 根据不同的Opearatornew不同的handler, handler去维护自己的hashmap. 所谓的"策略模式"就是能让你定义一系列算法， 并将每种算法分别放入独立的类中， 以使算法的对象能够相互替换。

![image-20220503093223548](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220503093223548.png)

`StringAggregator`只支持count

![image-20220503100845921](https://gitee.com/oldataraxia/pic-bad/raw/master/img/image-20220503100845921.png)

`Aggregate`就是把以上两个`Aggregator`再包装一层.

## exercise 3

实现

* src/java/simpledb/storage/HeapPage.java
* src/java/simpledb/storage/HeapFile.java

中的`insertTuple`和`deleteTuple`

* `deleteTuple`: 可以直接通过Tuple中的Record信息定位到它所在的地方, 之后修改Page的Header
* `insertTuple`: 添加tuple需要:

    * 找到一个由空slot的page
    * 如果没有, 需要创建一个新Page并把它追加到磁盘上的物理文件中
    * 更新Tuple的RecordId


这里我一开始对想改概念的理解应该是有一些误区...其实BufferPool是调用入口, HeapFile只是为BufferPool提供API的, 因此

关于HeapPage的实现:

* `isSlotUsed`和`markSlotUsed`: 运用一些位运算的手段来实现对bit map header的操作
* `markDirty`和`idDirsy`: HeapFile在对Page进行插入和删除以后需要标记当前Page为Dirty
* `insertTuple`, 找到第一个空闲的slot, 设置header, 更新tuples数组, 更新原tuple的Recordid为当前PageId与slotNum
    * 需要重新设置Tuple的`recordId`为当前位置

* `deleteTuple`: 检查传入Tuple的recordId中指示的位置与当前Page中对应位置的tuple是否一致, 一致则更新header

关于HeapFile的实现

* `insertTuple`: 返回值为过程中被修改过的Page. 这里访问Page的时候需要使用BufferPool.getPage()方法. 调用Page的`insertTuple`方法后需要调用`markDirty`来标记一下.
    * 这里脏页暂时不需要写盘, 要到BufferPool中写盘
    * 如果所有的Page都满了就创建一个空Page然后用writePage写入磁盘, 之后再用bufferpool访问并修改它
    * **话说这里遍历所有Page来找到空页的行为也太低效了吧, 不知道之后能不能优化一下**

* `deleteTuple`: 直接通过Tuple的Recordid定位到对应的Page, 之后调用HeapPage的delete方法

关于BufferPool的实现:

* BufferPool对Page的存储是用pageId来标识的
* `InerrtTuple`: 通过tableId得到HeapFile, 之后调用它的insertTuple. 对于返回的dirtyPage要加入缓存中去
* `deleteTuple`: 通过tuple.recordId.pageId.tableId得到HeapFile, 之后调用其deleteTuple方法, 对于返回的dirtyPage要加入缓存中去

## exercise 4

- src/java/simpledb/execution/Insert.java
- src/java/simpledb/execution/Delete.java

`insert`从其child operator中读取数据并将其插入tableId指定的表中. 其中`fetchNext`的逻辑是一口气把child中所有的tuple都插入, 然后返回一个tuple表示插入tuple的数量. 还需要一个变量标记是否是第一次访问, 不是的话一律返回null

`delete`: 从tableId指定的表中删除从child operator中的tuple. 具体逻辑同

就是对buffer pool访问加一层封装

## exercise 5

- src/java/simpledb/storage/BufferPool.java

其中flushpage方法要求你应该将任何脏页写入磁盘并标记为不脏，同时将其留在BufferPool中. evictPage就是采取驱逐策略(比如LRU)在bufferpool满的时候把一些page驱逐出内存. ，它应该对它所驱逐的任何脏页面调用flushPage. flushAllPage只是一个用于测试的方法, 对于bufferpool中所有的page调用flushpage方法.

