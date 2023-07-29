package com.zyter.groupcall;

public final class Constants {

	public static final String DISPLAYNAME = "X-Display-Name";
	public static final String WEBSOCKET_USERNAME = "X-User-Id";
	public static final String WEBSOCKET_USER_KEY = "X-User-Key";
	public static final String USERDOMAIN = "schemaName";
	public static final String AUTHTOKEN = "X-Auth-Token";
	public static final String EXCHANGEID = "X-Exchange-Id";
	public static final String PRESENCE = "X-Presence-Status";
	public static final String APPTOKEN = "X-App-Token";
	public static final String USERTYPE = "X-User-Type";
	public static final String USERSEATID = "X-User-SeatId";

	public static final Integer CALLHISTORYSTATUS_INITIATE_CALL = 1;

	public static final Integer CALLHISTORYSTATUS_IN_CALL = 2;

	public static final Integer CALLHISTORYSTATUS_CALL_REJECTED = 3;

	public static final Integer CALLHISTORYSTATUS_USER_BUSY = 4;

	public static final Integer CALLHISTORYSTATUS_MISSED_CALL = 5;

	public static final Integer CALLHISTORYSTATUS_CALL_DISCONNECTED = 6;

	public static final Integer CALLTYPE_AUDIO_CALL = 1;

	public static final Integer CALLTYPE_VIDEO_CALL = 2;

	public static final Integer CALLTYPE_AUDIO_CONF_CALL = 3;

	public static final Integer CALLTYPE_VIDEO_CONF_CALL = 4;
}
