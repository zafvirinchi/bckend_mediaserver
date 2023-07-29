package com.zyter.groupcall;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.Continuation;
import org.kurento.client.ElementConnectedEvent;
import org.kurento.client.ElementDisconnectedEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceComponentStateChangeEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.MediaFlowInStateChangeEvent;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaSessionStartedEvent;
import org.kurento.client.MediaSessionTerminatedEvent;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaTranscodingStateChangeEvent;
import org.kurento.client.MediaType;
import org.kurento.client.NewCandidatePairSelectedEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * @author Senthil Kumar K
 */
public class UserSession implements Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserSession.class);

	private final String name;
	
	//private String sdpOffer;
	
	private final WebSocketSession session;

	private final MediaPipeline pipeline;

	private final String roomName;
	
	private final WebRtcEndpoint outgoingMedia;
	
	private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

	public UserSession(final String name, String roomName, final WebSocketSession session, MediaPipeline pipeline) {

		this.pipeline = pipeline;
		this.name = name;
		this.session = session;
		this.roomName = roomName;
		this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();
		
		addEventListeners(this.outgoingMedia, name);
	}

	public WebRtcEndpoint getOutgoingWebRtcPeer() {
		return outgoingMedia;
	}

	public String getName() {
		return name;
	}

	public WebSocketSession getSession() {
		return session;
	}

	/**
	 * The room to which the user is currently attending.
	 *
	 * @return The room
	 */
	public String getRoomName() {
		return this.roomName;
	}

	public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
		if (sender != null) {
			LOGGER.info("USER {}: connecting with {} in room {}", this.name, sender.getName(), this.roomName);

			LOGGER.info("USER {}: SdpOffer for {} is {}", this.name, sender.getName(), sdpOffer);

			final String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
			final JsonObject scParams = new JsonObject();
			scParams.addProperty("id", "receiveVideoAnswer");
			scParams.addProperty("name", sender.getName());
			scParams.addProperty("sdpAnswer", ipSdpAnswer);

			LOGGER.info("USER {}: SdpAnswer for {} is {}", this.name, sender.getName(), ipSdpAnswer);
			this.sendMessage(scParams);
			LOGGER.info("gather candidates");
			this.getEndpointForUser(sender).gatherCandidates();
		}
	}

	public WebRtcEndpoint getEndpointForUser(final UserSession sender) {
		if (sender.getName().equals(name)) {
			LOGGER.info("PARTICIPANT {}: configuring loopback", this.name);
			return outgoingMedia;
		}

		LOGGER.info("PARTICIPANT {}: receiving video from {}", this.name, sender.getName());

		WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
		if (incoming == null) {
			LOGGER.info("PARTICIPANT {}: creating new endpoint for {}", this.name, sender.getName());
			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			addEventListeners(incoming, sender.getName());
			
			incomingMedia.put(sender.getName(), incoming);
		}

		LOGGER.info("PARTICIPANT {}: obtained endpoint for {}", this.name, sender.getName());
		sender.getOutgoingWebRtcPeer().connect(incoming);

		return incoming;
	}
	
	public void changeMediaType(Collection<UserSession> users, String name, String type) {
		WebRtcEndpoint incoming;
		for (UserSession participant : users) {
			if(!this.name.equals(participant.getName())) {
				incoming = incomingMedia.get(participant.getName());
				if ("VIDEO".equals(type)) {
					this.getOutgoingWebRtcPeer().connect(incoming, MediaType.VIDEO);
				} else if ("AUDIO".equals(type)) {
					this.getOutgoingWebRtcPeer().connect(incoming, MediaType.AUDIO);
				} else {
					this.getOutgoingWebRtcPeer().connect(incoming);
				}
				if(!this.name.equals(name) && name.equals(participant.getName())) {
					break;
				}
			}
		}
	}
	
	private void addEventListeners(WebRtcEndpoint webRtcEndpoint, String name) {
		webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
			@Override
			public void onEvent(IceCandidateFoundEvent event) {
				LOGGER.info("IceCandidateFoundEvent : {}", event.getType());
				sendIceCandidateMessage(name, JsonUtils.toJsonObject(event.getCandidate()));
			}
		});

		webRtcEndpoint.addIceComponentStateChangeListener(new EventListener<IceComponentStateChangeEvent>() {
			@Override
			public void onEvent(IceComponentStateChangeEvent event) {
				LOGGER.info("IceComponentStateChangeEvent : {}", event.getState().name());
			}
		});

		webRtcEndpoint.addIceGatheringDoneListener(new EventListener<IceGatheringDoneEvent>() {
			@Override
			public void onEvent(IceGatheringDoneEvent event) {
				LOGGER.info("IceGatheringDoneEvent : {}", event.getType());
			}
		});

		webRtcEndpoint.addNewCandidatePairSelectedListener(new EventListener<NewCandidatePairSelectedEvent>() {
			@Override
			public void onEvent(NewCandidatePairSelectedEvent event) {
				LOGGER.info("NewCandidatePairSelectedEvent : {}", event.getCandidatePair());
			}
		});
		
		webRtcEndpoint.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {
			@Override
			public void onEvent(MediaTranscodingStateChangeEvent event) {
				LOGGER.info("MediaTranscodingStateChangeEvent : {}", event.getState().name());
			}
		});
		
		webRtcEndpoint.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {
			@Override
			public void onEvent(MediaFlowInStateChangeEvent event) {
				LOGGER.info("MediaFlowInStateChangeEvent : {}", event.getState().name());
			}
		});
		
		webRtcEndpoint.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {
			@Override
			public void onEvent(MediaFlowOutStateChangeEvent event) {
				LOGGER.info("MediaFlowOutStateChangeEvent {}", event.getState().name());
			}
		});
		
		webRtcEndpoint.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {
			@Override
			public void onEvent(ElementConnectedEvent event) {
				LOGGER.info("ElementConnectedEvent {}", event.getMediaType().name());
			}
		});
		
		webRtcEndpoint.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {
			@Override
			public void onEvent(ElementDisconnectedEvent event) {
				LOGGER.info("ElementDisconnectedEvent : {}", event.getType());
			}
		});
		
		webRtcEndpoint.addConnectionStateChangedListener(new EventListener<ConnectionStateChangedEvent>() {
			@Override
			public void onEvent(ConnectionStateChangedEvent event) {
				LOGGER.info("ConnectionStateChangedEvent : {}", event.getNewState().name());
			}
		});
		
		webRtcEndpoint.addMediaStateChangedListener(new EventListener<MediaStateChangedEvent>() {
			@Override
			public void onEvent(MediaStateChangedEvent event) {
				LOGGER.info("MediaStateChangedEvent : {}", event.getNewState().name());
			}
		});
		
		webRtcEndpoint.addMediaSessionStartedListener(new EventListener<MediaSessionStartedEvent>() {
			@Override
			public void onEvent(MediaSessionStartedEvent event) {
				LOGGER.info("MediaSessionStartedEvent : {}", event.getType());
			}
		});
		
		webRtcEndpoint.addMediaSessionTerminatedListener(new EventListener<MediaSessionTerminatedEvent>() {
			@Override
			public void onEvent(MediaSessionTerminatedEvent event) {
				LOGGER.info("MediaSessionStartedEvent : {}", event.getType());
			}
		});
		
		webRtcEndpoint.addErrorListener(new EventListener<ErrorEvent>() {
			@Override
			public void onEvent(ErrorEvent event) {
				LOGGER.error("Error code : {}, Error description : {}", event.getErrorCode(), event.getDescription());
			}
		});
	}

	private void sendIceCandidateMessage(String name, JsonObject jsonObj) {
		JsonObject response = new JsonObject();
		response.addProperty("id", "iceCandidate");
		response.addProperty("name", name);
		response.add("candidate", jsonObj);
		try {
			synchronized (session) {
				session.sendMessage(new TextMessage(response.toString()));
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}
	
	public void cancelVideoFrom(final UserSession sender) {
		this.cancelVideoFrom(sender.getName());
	}

	public void cancelVideoFrom(final String senderName) {
		LOGGER.info("PARTICIPANT {}: canceling video reception from {}", this.name, senderName);
		final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

		LOGGER.info("PARTICIPANT {}: removing endpoint for {}", this.name, senderName);

		if (incoming != null) {
			incoming.release(new Continuation<Void>() {
				@Override
				public void onSuccess(Void result) throws Exception {
					LOGGER.info("PARTICIPANT {}: Released successfully incoming EP for {}", UserSession.this.name, senderName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					LOGGER.error("PARTICIPANT {}: Could not release incoming EP for {}", UserSession.this.name, senderName);
				}
			});
		} else {
			LOGGER.info("incoming video is not available for the user");
		}
	}

	@Override
	public void close() throws IOException {
		LOGGER.info("PARTICIPANT {}: Releasing resources", this.name);
		for (final String remoteParticipantName : incomingMedia.keySet()) {

			LOGGER.info("PARTICIPANT {}: Released incoming EP for {}", this.name, remoteParticipantName);

			final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);

			ep.release(new Continuation<Void>() {
				@Override
				public void onSuccess(Void result) throws Exception {
					LOGGER.info("PARTICIPANT {}: Released successfully incoming EP for {}", UserSession.this.name, remoteParticipantName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					LOGGER.error("PARTICIPANT {}: Could not release incoming EP for {}", UserSession.this.name, remoteParticipantName);
				}
			});
		}

		outgoingMedia.release(new Continuation<Void>() {
			@Override
			public void onSuccess(Void result) throws Exception {
				LOGGER.info("PARTICIPANT {}: Released outgoing EP", UserSession.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				LOGGER.error("USER {}: Could not release outgoing EP", UserSession.this.name);
			}
		});
	}

	public void sendMessage(JsonObject message) throws IOException {
		LOGGER.info("USER {}: Sending message {}", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
		}
	}

	public void addCandidate(IceCandidate candidate, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingMedia.addIceCandidate(candidate);
		} else {
			WebRtcEndpoint webRtc = incomingMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(candidate);
			}
		}
	}
	
	/*public String getSdpOffer() {
		return sdpOffer;
	}

	public void setSdpOffer(String sdpOffer) {
		this.sdpOffer = sdpOffer;
	}*/

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof UserSession)) {
			return false;
		}
		UserSession other = (UserSession) obj;
		boolean eq = name.equals(other.name);
		eq &= roomName.equals(other.roomName);
		return eq;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		int result = 1;
		result = 31 * result + name.hashCode();
		result = 31 * result + roomName.hashCode();
		return result;
	}
}