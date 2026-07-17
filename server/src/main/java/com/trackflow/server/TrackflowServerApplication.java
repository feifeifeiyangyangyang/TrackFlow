package com.trackflow.server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling
@SpringBootApplication
public class TrackflowServerApplication {
  public static void main(String[] args) { SpringApplication.run(TrackflowServerApplication.class, args); }
}
