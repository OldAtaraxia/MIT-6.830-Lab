package simpledb.execution.strategy;

import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;

// 真是疑惑, test case里面没有判断重复计数...
public class CountHandler implements AggregatorHandler {
    private Map<String, Integer> result = new HashMap<>();
    // private Map<Pair<String, String>, Integer> book = new HashMap<>(); // Pair<String. String>表示对应的key-value对是否出现过

    @Override
    public void mergeTuple(String key, String value) {
        if (!result.containsKey(key)) {
            result.put(key, 0);
        }

//        Pair<String, String> bookKey = new Pair<>(key, value);
//        if (!book.containsKey(bookKey)) {
//            // 否则就说明对应的key已经出现过了...
//            book.put(bookKey, 1);
//            result.put(key, result.get(key) + 1);
//        } else {
//            System.out.println("repetitive pair with groupkey " + key + " and value " + value);
//        }
        result.put(key, result.get(key) + 1);
    }

    @Override
    public Map<String, Integer> getResult() {
        return result;
    }
}
