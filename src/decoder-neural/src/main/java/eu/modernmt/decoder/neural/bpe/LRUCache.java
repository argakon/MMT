package eu.modernmt.decoder.neural.bpe;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A BPE.LRUCache is a LRU cache working for BPE encoded strings.
 * <p>
 * It is typically used to prevent encoding the same string multiple times.
 * When a string is passed for encoding, the BPE object first check if it is already in cache;
 * if it is, the cached result will be returned immediately.
 */
public class LRUCache extends LinkedHashMap<String, String[]> {
    private static final int DEFAULT_SIZE = 1000;
    private final int maxSize;

    public LRUCache(int maxSize) {
        super(16, .75f, true);
        this.maxSize = maxSize;
    }

    public LRUCache() {
        this(DEFAULT_SIZE);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String[]> entry) {
        return this.size() > maxSize;
    }
}
