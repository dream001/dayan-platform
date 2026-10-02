package com.dayan.platform;

import com.dayan.platform.config.ApplicationProperties;
import com.dayan.platform.config.InitialAdminProperties;
import com.dayan.platform.config.MinioProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        ApplicationProperties.class,
        InitialAdminProperties.class,
        MinioProperties.class
})
@MapperScan("com.dayan.platform.repository.mapper")
public class DayanPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DayanPlatformApplication.class, args);
    }
}
