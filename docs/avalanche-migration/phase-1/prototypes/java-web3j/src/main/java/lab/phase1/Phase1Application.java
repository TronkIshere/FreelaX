package lab.phase1;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;

/** Phase 1 lab application. Not production code; exists to prove Spring Boot 3.5.16 + Java 21 + web3j wiring. */
@SpringBootApplication
public class Phase1Application {

    public static void main(String[] args) {
        SpringApplication.run(Phase1Application.class, args);
    }

    @Bean
    OkHttpClient labHttpClient(@Value("${phase1.http.timeout-seconds:10}") long seconds) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(seconds))
                .readTimeout(Duration.ofSeconds(seconds))
                .callTimeout(Duration.ofSeconds(seconds))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    Web3j web3j(@Value("${phase1.rpc-url:http://localhost:8545}") String url, OkHttpClient client) {
        return Web3j.build(new HttpService(url, client));
    }
}
