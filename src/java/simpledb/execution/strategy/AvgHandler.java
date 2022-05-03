package simpledb.execution.strategy;

import java.util.HashMap;
import java.util.Map;

public class AvgHandler implements AggregatorHandler {
    private Map<String, Integer> result = new HashMap<>();
    private Map<String, Integer> num = new HashMap<>();
    private Map<String, Integer> sum = new HashMap<>();

    @Override
    public void mergeTuple(String key, String value) {
        if (!result.containsKey(key)) {
            result.put(key, 0);
            num.put(key, 0);
            sum.put(key, 0);
        }

        int intValue = Integer.parseInt(value);
        num.put(key, num.get(key) + 1); // num++
        sum.put(key, sum.get(key) + intValue); // sum++
        result.put(key, sum.get(key) / num.get(key));
    }

    @Override
    public Map<String, Integer> getResult() {
        return result;
    }
}
