package org.eclipse.flux.client

public val CF_CONTROLLER_URL: String = "cf_controller" // property
public val CF_USERNAME: String = "cf_username" // property
public val CF_TOKEN: String = "cf_token" //property oath2 bearer token

public val OK: String = "ok"

public val CF_SPACES_REQUEST: String = "cfSpacesRequest" // message
// type
public val CF_SPACES_RESPONSE: String = "cfSpacesResponse" // message
// type
public val CF_SPACES: String = "cfSpaces" // property type

public val CF_PUSH_REQUEST: String = "cfPushRequest" // message
// type
public val CF_PUSH_RESPONSE: String = "cfPushResponse" // message
// type

public val PROJECT_NAME: String = "project"
public val CF_ORG_SPACE: String = "orgSpace" // property org + "/"
// + space

public val USERNAME: String = "username" // property
public val ERROR: String = "error" // property

public val REQUEST_SENDER_ID: String = "requestSenderID"
public val RESPONSE_SENDER_ID: String = "responseSenderID"
public val CALLBACK_ID: String = "callback_id"

public val CF_APP_LOG: String = "cfAppLog" // message type
public val CF_APP: String = "app"
public val CF_ORG: String = "org"
public val CF_SPACE: String = "space"
public val CF_STREAM: String = "stream"
public val CF_MESSAGE: String = "msg"

public val CF_STREAM_CLIENT_ERROR: String = "STDERROR"
public val CF_STREAM_STDOUT: String = "STDOUT"
public val CF_STREAM_STDERROR: String = "STDERROR"
public val CF_STREAM_SERVICE_OUT: String = "SVCOUT"

/**
 * Name of the Flux super user.
 */
public val SUPER_USER: String = "\$super\$"