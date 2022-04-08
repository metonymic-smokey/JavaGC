package at.jku.anttracks.callcontext;

import java.util.HashMap;
import java.util.Map;

public class Cache<T> {

    private final Map<T, T> cache = new HashMap<>();

    public T get(T object) {
        if (object == null) {
            return null;
        }
        T cached = cache.get(object);
        if (cached == null) {
            cached = object;
            cache.put(cached, cached);
        }
        return cached;
    }

}
