package simpledb.execution.strategy;

import java.util.HashMap;
import java.util.Map;

public class SumHandler implements AggregatorHandler {
    private Map<String, Integer> result = new HashMap<>(); // 应该不需要考虑什么并发安全罢...

    @Override
    public void mergeTuple(String key, String value) {
        if (!result.containsKey(key)) {
            result.put(key, 0);
        }
        result.put(key, result.get(key) + Integer.parseInt(value));
    }

    @Override
    public Map<String, Integer> getResult() {
        return result;
    }
}
