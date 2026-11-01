package lab.phase1;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Collects experiment outcomes and writes them as JSON evidence (no secrets: only ephemeral addresses/hashes). */
final class Evidence {
    private static final Map<String, Object> ALL = new LinkedHashMap<>();

    static synchronized void rec(String id, String status, Map<String, Object> details) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("at", Instant.now().toString());
        m.putAll(details);
        ALL.put(id, m);
        System.out.println("[EVIDENCE] " + id + " " + status + " " + details);
    }

    static synchronized void flush(String file) {
        try {
            Path p = Path.of(System.getProperty("evidence.dir", "../../evidence"), file);
            Files.createDirectories(p.getParent());
            Files.writeString(p, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(ALL));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, Object> of(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1] instanceof byte[] ? "bytes" : kv[i + 1]);
        return m;
    }
}
