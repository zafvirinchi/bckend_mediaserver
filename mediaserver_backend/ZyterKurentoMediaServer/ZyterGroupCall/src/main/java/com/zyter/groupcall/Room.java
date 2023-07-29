package com.zyter.groupcall;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * @author Senthil Kumar K
 */
public class Room implements Closeable {
	private final Logger LOGGER = LoggerFactory.getLogger(Room.class);

	private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
	private final MediaPipeline pipeline;
	private final String name;
	
	private String callInitiator;

	public String getName() {
		return name;
	}

	public Room(String roomName, MediaPipeline pipeline) {
		this.name = roomName;
		this.pipeline = pipeline;
		LOGGER.info("ROOM {} has been created", roomName);
	}

	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public UserSession join(String userName, WebSocketSession session) throws IOException {
		LOGGER.info("ROOM {}: adding participant {}", this.name, userName);
		final UserSession participant = new UserSession(userName, this.name, session, this.pipeline);
		participants.put(participant.getName(), participant);
		if(callInitiator == null) {
			sendNewParticipantName(participant);
			sendParticipantNames(participant);
		}
		return participant;
	}

	public void leave(UserSession user) throws IOException {
		LOGGER.info("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
		this.removeParticipant(user.getName());
		user.close();
	}

	public Collection<String> sendNewParticipantName(UserSession newParticipant) throws IOException {
		final JsonObject newParticipantMsg = new JsonObject();
		newParticipantMsg.addProperty("id", "newParticipantArrived");
		newParticipantMsg.addProperty("name", newParticipant.getName());

		final List<String> participantsList = new ArrayList<>(participants.values().size());
		LOGGER.info("ROOM {}: notifying other participants of new participant {}", name, newParticipant.getName());

		for (final UserSession participant : participants.values()) {
			try {
				if (!participant.getName().equals(newParticipant.getName())) {
					participant.sendMessage(newParticipantMsg);
				}
			} catch (Exception e) {
				LOGGER.error("ROOM {}: participant {} could not be notified", name, participant.getName(), e);
			}
			participantsList.add(participant.getName());
		}

		return participantsList;
	}

	private void removeParticipant(String name) throws IOException {
		participants.remove(name);

		LOGGER.info("ROOM {}: notifying all users that {} is leaving the room", this.name, name);

		final List<String> unnotifiedParticipants = new ArrayList<>();
		final JsonObject participantLeftJson = new JsonObject();
		participantLeftJson.addProperty("id", "participantLeft");
		participantLeftJson.addProperty("name", name);
		for (final UserSession participant : participants.values()) {
			try {
				participant.cancelVideoFrom(name);
				participant.sendMessage(participantLeftJson);
			} catch (Exception e) {
				LOGGER.error("cancelVideoFrom {}", name);
				unnotifiedParticipants.add(participant.getName());
			}
		}

		if (!unnotifiedParticipants.isEmpty()) {
			LOGGER.error("ROOM {}: The users {} could not be notified that {} left the room", this.name, unnotifiedParticipants, name);
		}
	}

	public void sendParticipantNames(UserSession user) throws IOException {
		final JsonArray participantsArray = new JsonArray();
		for (final UserSession participant : this.getParticipants()) {
			if (!participant.getName().equals(user.getName())) {
				final JsonElement participantName = new JsonPrimitive(participant.getName());
				participantsArray.add(participantName);
			}
		}

		final JsonObject existingParticipantsMsg = new JsonObject();
		existingParticipantsMsg.addProperty("id", "existingParticipants");
		existingParticipantsMsg.add("data", participantsArray);
		LOGGER.info("PARTICIPANT {}: sending a list of {} participants", user.getName(), participantsArray.size());
		user.sendMessage(existingParticipantsMsg);
	}

	public Collection<UserSession> getParticipants() {
		return participants.values();
	}

	public UserSession getParticipant(String name) {
		return participants.get(name);
	}

	@Override
	public void close() {
		for (final UserSession user : participants.values()) {
			try {
				user.close();
			} catch (Exception e) {
				LOGGER.error("ROOM {}: Could not invoke close on participant {}", this.name, user.getName(), e);
			}
		}

		participants.clear();

		pipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				LOGGER.info("ROOM {}: Released Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				LOGGER.info("PARTICIPANT {}: Could not release Pipeline", Room.this.name);
			}
		});

		LOGGER.info("Room {} closed", this.name);
	}

	public String getCallInitiator() {
		return callInitiator;
	}

	public void setCallInitiator(String callInitiator) {
		this.callInitiator = callInitiator;
	}
}
