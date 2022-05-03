package simpledb.execution.strategy;

import simpledb.storage.Tuple;

import java.util.Map;

public interface AggregatorHandler {

    public void mergeTuple(String key, String value);

    public Map<String, Integer> getResult();

}
