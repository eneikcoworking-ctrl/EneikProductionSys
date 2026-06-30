package com.eneik.production;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;

@SpringBootApplication
public class ProductionApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductionApplication.class, args);
	}

}

@RestController
@CrossOrigin(origins = "http://localhost:3000")
class GreetingController {

	@GetMapping("/api/v1/greetings/latest")
	public Map<String, Object> getLatestGreeting() {
		return Map.of(
			"id", UUID.randomUUID().toString(),
			"message", "Hello from Spring Boot!",
			"currentStatus", "RECEIVED",
			"createdAt", OffsetDateTime.now().toString(),
			"leadTimeSeconds", 42
		);
	}
}
