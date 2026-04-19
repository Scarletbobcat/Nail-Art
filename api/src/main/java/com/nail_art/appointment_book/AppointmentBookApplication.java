package com.nail_art.appointment_book;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AppointmentBookApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppointmentBookApplication.class, args);
	}

}
