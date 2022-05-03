package simpledb.execution;

import simpledb.common.Type;
import simpledb.execution.strategy.*;
import simpledb.storage.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    // 定义成员变量
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    // 具体存group之后的数据
    private AggregatorHandler handler;

    private String NO_GROUPING_KEY = "NO_GROUPING_KEY";

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;

        switch (what) {
            case MIN:
                this.handler = new MinHandler();
                break;
            case AVG:
                this.handler = new AvgHandler();
                break;
            case SUM:
                this.handler = new SumHandler();
                break;
            case COUNT:
                this.handler = new CountHandler();
                break;
            case MAX:
                this.handler = new MaxHandler();
                break;
            default:
                throw new IllegalArgumentException("don't support this kind of operator");
        }

    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        if (this.gbfield != NO_GROUPING && tup.getField(gbfield).getType() != this.gbfieldtype) {
            throw new IllegalArgumentException("tuple type not legal");
        }

        String key = NO_GROUPING_KEY;
        if (this.gbfield != NO_GROUPING) {
            key = tup.getField(gbfield).toString();
        }
        handler.mergeTuple(key, tup.getField(afield).toString());
    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        Map<String, Integer> result = handler.getResult();
        List<Tuple> tupleList = new ArrayList<>();
        TupleDesc tupleDesc;

        if (this.gbfield == NO_GROUPING) {
            Type[] types = new Type[]{Type.INT_TYPE};
            String[] names = new String[]{"aggregateVal"};

            tupleDesc = new TupleDesc(types, names);
            for (Integer value : result.values()) {
                Tuple tuple = new Tuple(tupleDesc);
                tuple.setField(0, new IntField(value));
                tupleList.add(tuple);
            }
        } else {
            Type[] types = new Type[]{gbfieldtype, Type.INT_TYPE};
            String[] names = new String[]{"groupVal", "aggregateVal"};

            tupleDesc = new TupleDesc(types, names);
            for (String key: result.keySet()) {
                Integer value = result.get(key);

                Tuple tuple = new Tuple(tupleDesc);
                tuple.setField(1, new IntField(value));

                // tuple.setField(0, new IntField(Integer.parseInt(key)));
                if (gbfieldtype == Type.INT_TYPE) {
                    tuple.setField(0, new IntField(Integer.parseInt(key)));
                } else {
                    tuple.setField(0, new StringField(key, key.length()));
                }
                tupleList.add(tuple);
            }
        }
        return new TupleIterator(tupleDesc, tupleList);
    }

}
