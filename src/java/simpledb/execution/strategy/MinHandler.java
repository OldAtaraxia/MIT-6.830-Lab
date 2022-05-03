package simpledb.execution.strategy;

import java.util.HashMap;
import java.util.Map;

public class MinHandler implements AggregatorHandler {
    private Map<String, Integer> result = new HashMap<>(); // 应该不需要考虑什么并发安全罢...

    @Override
    public void mergeTuple(String key, String value) {
        if (!result.containsKey(key)) {
            result.put(key, Integer.MAX_VALUE);
        }
        result.put(key, Math.min(result.get(key), Integer.parseInt(value)));
    }

    @Override
    public Map<String, Integer> getResult() {
        return result;
    }
}
