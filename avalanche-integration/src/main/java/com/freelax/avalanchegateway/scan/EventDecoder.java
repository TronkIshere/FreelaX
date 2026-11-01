package com.freelax.avalanchegateway.scan;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Maps event topic0 values to names. The scanner accepts only logs whose topic0 is registered here. */
public class EventDecoder {

    private final Map<String, String> names = new TreeMap<>();

    public EventDecoder register(String topic0, String name) {
        names.put(topic0.toLowerCase(Locale.ROOT), name);
        return this;
    }

    public boolean known(String topic0) {
        return topic0 != null && names.containsKey(topic0.toLowerCase(Locale.ROOT));
    }

    public String name(String topic0) {
        return names.get(topic0.toLowerCase(Locale.ROOT));
    }

    public Set<String> topics() {
        return names.keySet();
    }
}
