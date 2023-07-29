package com.zyter.groupcall;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.client.standard.WebSocketContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * @author Senthil Kumar K
 */
@SpringBootApplication
@EnableWebSocket
public class GroupCallApp extends SpringBootServletInitializer implements WebSocketConfigurer {

	@Bean
	public UserRegistry registry() {
		return new UserRegistry();
	}

	@Bean
	public RoomManager roomManager() {
		return new RoomManager();
	}

	@Bean
	public CallHandler groupCallHandler() {
		return new CallHandler();
	}

	@Bean
	public KurentoClient kurentoClient() {
		return KurentoClient.create("ws://34.203.8.163:8888/kurento");
	}

	@Bean
	public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(32768);
		container.setMaxSessionIdleTimeout(60 * 1000L);
		container.setAsyncSendTimeout(180 * 1000L);
		return container;
	}
	
	// Web socket configuration for client side.
	@Bean
	public WebSocketContainerFactoryBean createWebSocketContainerFactoryBean() {
		WebSocketContainerFactoryBean container = new WebSocketContainerFactoryBean();
		/*container.setMaxTextMessageBufferSize(1500000);
		container.setMaxBinaryMessageBufferSize(1500000);*/
		container.setMaxSessionIdleTimeout(60 * 1000);
		container.setAsyncSendTimeout(180 * 1000);
		return container;
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(GroupCallApp.class, args);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// When client native web socket functionality is used, the below handler & URL path are used.
		registry.addHandler(groupCallHandler(), "/webSocketServer").setAllowedOrigins("*")
				.addInterceptors(new WebSocketHandshakeInterceptor());

		// When sockjs(third party library) is used, the below handler & URL path are used.
		registry.addHandler(groupCallHandler(), "/sockjs/webSocketServer").setAllowedOrigins("*")
				.addInterceptors(new WebSocketHandshakeInterceptor()).withSockJS();
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(GroupCallApp.class);
	}
}