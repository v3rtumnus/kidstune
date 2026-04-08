package at.kidstune;

import at.kidstune.auth.SpotifyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(SpotifyConfig.class)
public class KidstuneApplication {

    public static void main(String[] args) {
        SpringApplication.run(KidstuneApplication.class, args);
    }
}
