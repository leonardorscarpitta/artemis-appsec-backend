package io.github.artemiscore.artemis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArtemisApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArtemisApplication.class, args);
	}

}
