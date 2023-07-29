package com.zyter.groupcall;

import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Senthil Kumar K
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserVO {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserVO.class);

	private String messagePayload;

	private CopyOnWriteArrayList<UserUtil> utilsList;
	
	private CloseStatus status;

	public CloseStatus getStatus() {
		return status;
	}

	public void setStatus(CloseStatus status) {
		this.status = status;
	}

	public CopyOnWriteArrayList<UserUtil> getUtilsList() {
		return utilsList;
	}

	public void setUtilsList(CopyOnWriteArrayList<UserUtil> utilsList) {
		this.utilsList = utilsList;
	}

	public String getMessagePayload() {
		return messagePayload;
	}

	public void setMessagePayload(String messagePayload) {
		this.messagePayload = messagePayload;
	}
}