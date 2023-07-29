package com.zyter.mediaserver.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 *
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
@SpringBootApplication
public class MediaServerApp extends SpringBootServletInitializer {
 
  public static void main(String[] args) throws Exception {
    SpringApplication.run(MediaServerApp.class, args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
	return builder.sources(MediaServerApp.class);
  }
}
