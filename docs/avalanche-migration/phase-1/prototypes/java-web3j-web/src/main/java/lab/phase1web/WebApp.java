package lab.phase1web;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Phase 1 Part B lab: Spring MVC (Jackson 2 for HTTP) + web3j (Jackson 3 for JSON-RPC) in one context. Read-only; Fuji only. */
@SpringBootApplication
public class WebApp {
    public static void main(String[] args) { SpringApplication.run(WebApp.class, args); }

    @Bean(destroyMethod = "shutdown")
    Web3j web3j(@Value("${phase1.rpc-url}") String url) {
        OkHttpClient c = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(15)).callTimeout(Duration.ofSeconds(20)).build();
        return Web3j.build(new HttpService(url, c));
    }

    @Service
    static class ChainService {
        private final Web3j web3j;
        ChainService(Web3j web3j) { this.web3j = web3j; }

        Map<String, Object> snapshot() throws Exception {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chainId", web3j.ethChainId().send().getChainId());
            var head = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
            m.put("head", head.getNumber());
            m.put("headTimestamp", head.getTimestamp());
            m.put("finalizedHead", web3j.ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED, false).send().getBlock().getNumber());
            m.put("java", System.getProperty("java.version"));
            return m;
        }
    }

    @RestController
    static class ChainController {
        private final ChainService service;
        ChainController(ChainService service) { this.service = service; }

        @GetMapping("/chain")
        Map<String, Object> chain() throws Exception { return service.snapshot(); }
    }
}
