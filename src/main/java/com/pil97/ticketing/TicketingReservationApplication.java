package com.pil97.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling

@SpringBootApplication
public class TicketingReservationApplication {

  public static void main(String[] args) {
    SpringApplication.run(TicketingReservationApplication.class, args);
  }

}
