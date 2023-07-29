package com.zyter.groupcall;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * @author Senthil Kumar K
 * name - unique user id
 * roomName - unique room id
 */
public class CallHandler extends TextWebSocketHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(CallHandler.class);

	private static final Gson gson = new GsonBuilder().create();

	@Autowired
	private RoomManager roomManager;

	@Autowired
	private UserRegistry userRegistry;

	private static final ConcurrentHashMap<String, ConcurrentHashMap<String, CopyOnWriteArrayList<UserUtil>>> users;

	static {
		users = new ConcurrentHashMap<String, ConcurrentHashMap<String, CopyOnWriteArrayList<UserUtil>>>();
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		LOGGER.info("afterConnectionEstablished......");

		HttpHeaders headers = session.getHandshakeHeaders();

		String userName = "";
		String domain = "";
		String authToken = "";
		String displayName = "";

		if (headers != null && headers.get(Constants.WEBSOCKET_USERNAME) != null) {
			LOGGER.info("Websocket headers are passed");
			
			userName = headers.get(Constants.WEBSOCKET_USERNAME).get(0);
			LOGGER.info("username : {}", userName);
			
			domain = headers.get(Constants.USERDOMAIN).get(0);
			LOGGER.info("domain : {}", domain);
			
			authToken = headers.get(Constants.AUTHTOKEN).get(0);
			LOGGER.info("authToken : {}", authToken);
			
			displayName = headers.get(Constants.DISPLAYNAME).get(0);
			LOGGER.info("displayName : {}", displayName);
			
			storeUserSessionInMemory(session, userName, domain, authToken, displayName);
		}
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		LOGGER.info("Inside handleTextMessage");
		
		if (message.getPayload().toString() == null || message.getPayload().toString().isEmpty()) {
			return;
		}

		JsonObject jsonMessage = null;
		
		try {
			jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
			
			if (jsonMessage.get("id") == null)
				return;
			
			if("ping".equals(jsonMessage.get("id").getAsString())) {
				LOGGER.info("Incoming message : {}", jsonMessage);
				
				jsonMessage.addProperty("id", "pong");
				LOGGER.info("Sending the message to participant '{}' with data '{}'", jsonMessage.get("from").getAsString(), jsonMessage.toString());
			    session.sendMessage(new TextMessage(jsonMessage.toString()));
			    return;
			}

			UserSession user = userRegistry.getBySession(session);

			if (user != null) {
				LOGGER.info("Incoming message from user '{}': {}", user.getName(), jsonMessage);
			} else {
				LOGGER.info("Incoming message from new user: {}", jsonMessage);
			}

			switch (jsonMessage.get("id").getAsString()) {
				case "initialData":
					String userName = jsonMessage.get("userid").getAsString();
					String domain = jsonMessage.get("domain").getAsString();
					String authToken = jsonMessage.get("auth_token").getAsString();
					String displayName = jsonMessage.get("display_name").getAsString();
	
					storeUserSessionInMemory(session, userName, domain, authToken, displayName);
					break;
				case "joinRoom":
					joinRoom(jsonMessage, session);
					break;
				case "call":
					String roomName = UUID.randomUUID().toString();
					LOGGER.info("roomName : {}", roomName);
					jsonMessage.addProperty("room", roomName);
					jsonMessage.addProperty("name", jsonMessage.get("from").getAsString());
					joinRoom(jsonMessage, session);
	
					UserSession caller = userRegistry.getBySession(session);
					call(caller, jsonMessage, roomName);
					break;
				case "incomingCallResponse":
					incomingCallResponse(session, jsonMessage);
					break;
				case "receiveVideoFrom":
					final String senderName = jsonMessage.get("sender").getAsString();
					final UserSession sender = userRegistry.getByName(senderName);
					final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
					user.receiveVideoFrom(sender, sdpOffer);
					break;
				case "leaveRoom":
					leaveRoom(user);
					userRegistry.removeBySession(session);
					break;
				case "onIceCandidate":
					JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
					if (user != null) {
						IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
						user.addCandidate(cand, jsonMessage.get("name").getAsString());
					}
					break;
				case "changeMediaType":
					Room room = roomManager.getRoom(user.getRoomName());
					String type = jsonMessage.get("type").getAsString();
					String name = jsonMessage.get("name").getAsString();
					user.changeMediaType(room.getParticipants(), name, type);
					break;
				default:
					break;
			}
		} catch (Exception ex) {
			if(jsonMessage != null && jsonMessage.get("id") != null) {
				handleErrorResponse(ex, session, jsonMessage.get("id").getAsString() + "Response");
			}else {
				handleErrorResponse(ex, session, "Error");
			}
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		LOGGER.info("Inside afterConnectionClosed() method");
		/*UserSession user = registry.removeBySession(session);
		if (user != null) {
			roomManager.getRoom(user.getRoomName()).leave(user);
		}*/
	}
	
	// Handler method that is called when the message sent by the client don't reach the server for any reason.
	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		LOGGER.error("Error has occured with the following session {}", session);
        try {
        	if (session.isOpen()) {
    			session.close();
    		}
        } catch (Exception e) {
            LOGGER.error("Cannot close session on handleTransportError ", e);
        }
	}

	private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		LOGGER.info("PARTICIPANT {}: trying to join room {}", name, roomName);
		//String responseMsg = "accepted";
		UserSession user = null;
		if (name.isEmpty() || roomName.isEmpty()) {
			//responseMsg = "rejected: empty user name";
		} else {
			Room room = roomManager.getRoom(roomName);
			if("call".equals(params.get("id").getAsString())) {
				room.setCallInitiator(params.get("from").getAsString());
			}
			user = room.join(name, session);
			userRegistry.register(user);
		}
		/*if(user != null) {
			JsonObject response = new JsonObject();
		    response.addProperty("id", "registerResponse");
		    response.addProperty("response", responseMsg);
		    user.sendMessage(response);	
		}*/
	}

	private void leaveRoom(UserSession user) throws IOException {
		LOGGER.info("Inside leaveRoom() method");
		final Room room = roomManager.getRoom(user.getRoomName());
		room.leave(user);
		if (room.getParticipants().isEmpty()) {
			roomManager.removeRoom(room);
		}
		
		CopyOnWriteArrayList<UserUtil> utilsList = users.get("molzyter") == null ? null : users.get("molzyter").get(user.getName());
		if (utilsList != null) {
			for (UserUtil utils : utilsList) {
				LOGGER.info("Changing roomName as null for user id {}", utils.getUserId());
				utils.setRoomName(null);
			}
		}
	}

	private void call(UserSession caller, JsonObject jsonMessage, String roomName) throws IOException {
		LOGGER.info("Inside call() method");
		
		String domain = jsonMessage.get("domain").getAsString().toLowerCase();
		String from = jsonMessage.get("from").getAsString();
		String to = jsonMessage.get("to").getAsString();

		CopyOnWriteArrayList<UserUtil> utilsList = users.get("molzyter") == null ? null : users.get("molzyter").get(caller.getName());
		if (utilsList != null) {
			for (UserUtil utils : utilsList) {
				LOGGER.info("Setting roomName for user id {}", utils.getUserId());
				utils.setRoomName(roomName);
			}
		}
		
		String[] toList = to.split(",");

		for (String userName : toList) {
			utilsList = users.get(domain) == null ? null : users.get(domain).get(userName);
			if (utilsList != null) {
				for (UserUtil utils : utilsList) {
					try {
						if (utils.getSession().isOpen()) {
							utils.setRoomName(roomName);
							LOGGER.info("User session is available for the user '{}'", userName);
							
							JsonObject response = new JsonObject();

							jsonMessage.addProperty("name", utils.getUserId());
							//joinRoom(jsonMessage, utils.getSession());

							//if (userRegistry.exists(userName)) {
								LOGGER.info("User '{}' is available in user registry", userName);

								// caller.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
								// caller.setCallingTo(to);

								response.addProperty("id", "incomingCall");
								response.addProperty("from", from);
								
								LOGGER.info("Sending the message to participant '{}' with data '{}'", userName, response.toString());

								// UserSession callee = registry.getByName(userName);
								// callee.sendMessage(response);
								utils.getSession().sendMessage(new TextMessage(response.toString()));
								// callee.setCallingFrom(from);
							/*} else {
								response.addProperty("id", "callResponse");
								response.addProperty("response", "rejected: user '" + userName + "' is not registered");

								LOGGER.info("Sending the message to caller '{}' with data '{}'", caller.getName(), response.toString());
								
								caller.sendMessage(response);
							}*/
						}
					} catch (Throwable t) {
						handleErrorResponse(t, utils.getSession(), "callResponse");
					}
				}
			}
		}
	}

	private void incomingCallResponse(WebSocketSession session, JsonObject jsonMessage) throws IOException {
		LOGGER.info("Inside incomingCallResponse() method");
		String callResponse = jsonMessage.get("callResponse").getAsString();
		String from = jsonMessage.get("from").getAsString();
		
		/*JsonObject response = new JsonObject();
		response.addProperty("id", "callResponse");
		response.addProperty("from", from);*/
		
		if ("accept".equals(callResponse)) {
			//LOGGER.info("Accepted call from '{}' to '{}'", from, caller.getName());
			LOGGER.info("Accepted call from '{}'", from);
			try {
				String roomName = null;
				CopyOnWriteArrayList<UserUtil> utilsList = users.get("molzyter") == null ? null : users.get("molzyter").get(from);
				if (utilsList != null) {
					for (UserUtil utils : utilsList) {
						roomName = utils.getRoomName();
						if(roomName != null) {
							break;
						}
					}
				}
				
				Room room = roomManager.getRoom(roomName);
				if(room.getCallInitiator() == null) {
					LOGGER.error("Call initiator is not available in room");
				}
				if(room.getParticipants().size() == 1) {
					final UserSession caller = userRegistry.getByName(room.getCallInitiator());
					room.sendParticipantNames(caller);
				}
				jsonMessage.addProperty("room", roomName);
				jsonMessage.addProperty("name", from);
				joinRoom(jsonMessage, session);
				
				UserSession callee = userRegistry.getByName(from);
				room.sendParticipantNames(callee);
				room.sendNewParticipantName(callee);
				//String callerSdpOffer = registry.getByName(from).getSdpOffer();
				//String callerSdpAnswer = caller.getEndpointForUser(callee).processOffer(callerSdpOffer);
				//response.addProperty("response", "accepted");
				//response.addProperty("sdpAnswer", callerSdpAnswer);
			} catch (Throwable t) {
				LOGGER.error(t.getMessage(), t);
				//response.addProperty("response", "rejected");
				//caller.sendMessage(response);
			}
		} else {
			//LOGGER.info("Rejected call from '{}' to '{}'", from, caller.getName());
			//response.addProperty("response", "rejected");
			//caller.sendMessage(response);
		}
		/*synchronized (caller) {
			caller.sendMessage(response);
		}*/
	}
	
	private void storeUserSessionInMemory(WebSocketSession session, String userName, String domain, String authToken, String displayName) {
		LOGGER.info("Inside storeUserSessionInMemory() method");
		try {
			ConcurrentHashMap<String, CopyOnWriteArrayList<UserUtil>> domains = users.get(domain);

			if (domains == null) {
				domains = new ConcurrentHashMap<String, CopyOnWriteArrayList<UserUtil>>();
				users.put(domain, domains);
			}

			CopyOnWriteArrayList<UserUtil> temp = domains.get(userName);
			if (temp == null) {
				temp = new CopyOnWriteArrayList<UserUtil>();
				domains.put(userName, temp);
			}

			UserUtil details = new UserUtil();
			details.setUserId(userName);
			details.setSession(session);
			details.setDomain(domain);
			details.setAuthToken(authToken);
			details.setDisplayName(displayName);
			temp.add(details);
			
			LOGGER.info("User session has been stored in the server");
		} catch (Exception ex) {
			LOGGER.error(ex.getMessage());
		}
	}
	
	private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId) throws IOException {
		LOGGER.error(throwable.getMessage(), throwable);
		JsonObject response = new JsonObject();
		response.addProperty("id", responseId);
		response.addProperty("response", "rejected");
		response.addProperty("message", throwable.getMessage());
		session.sendMessage(new TextMessage(response.toString()));
	}
}