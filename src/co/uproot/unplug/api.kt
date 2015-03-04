package co.uproot.unplug

import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.MediaType
import java.io.IOException
import com.eclipsesource.json.JsonObject
import java.net.URLEncoder
import com.eclipsesource.json.JsonArray

data class AccessToken(val token:String)

data class Message(
  val id: String?,
  val ts: Long,
  val roomId: String?,
  val type: String,
  val userId: String,
  val content: JsonObject)

data class State(
  val type: String,
  val ts: Long,
  val userId: String,
  val content: JsonObject ) {
}

data class Room(val id:String, val aliases:List<String>, val messages: MutableList<Message>, val states : List<State>) {
  fun isChatMessage(message: Message):Boolean {
    return when (message.type) {
      "m.room.create" -> true
      "m.room.member" -> true
      "m.room.message" -> true
      else -> false
    }
  }

  fun chatMessages(): List<Message> {
    return messages.stream().filter({isChatMessage(it)}).toList()
  }
}

fun JsonObject.getObject(name:String):JsonObject {
  return this.get(name).asObject()
}

fun JsonObject.getArray(name:String):JsonArray {
  return this.get(name).asArray()
}

class SyncResult(val rooms: List<Room>, val presence: List<Message>)

class EventResult(val messages: List<Message>, val end: String)

// TODO: Change API to be fully type-safe, and not return JSON objects
class API(val baseURL: String) {
  val apiURL = baseURL + "_matrix/client/api/v1/"
  val mediaURL = baseURL + "_matrix/media/v1/"

  private final val client = OkHttpClient();

  {
    client.setFollowSslRedirects(false)
    client.setFollowRedirects(false)
  }

  private final val net = Net(client)

  fun login(username: String, password: String): AccessToken? {
    try {
      val postBody = """
      {"type":"m.login.password", "user":"$username", "password":"$password"}
      """

      val responseStr = net.doPost(apiURL + "login", postBody)
      val jsonObj = JsonObject.readFrom(responseStr)
      val tokenStr = jsonObj.getString("access_token", null)
      return tokenStr?.let { AccessToken(it) }
    } catch(e : IOException) {
      e.printStackTrace()
      return null;
    }
  }

  fun getRoomId(accessToken: AccessToken, roomAlias: String): String? {
    val roomAliasEscaped = URLEncoder.encode(roomAlias, "UTF-8")
    val responseStr = net.doGet(apiURL + "directory/room/$roomAliasEscaped?access_token=${accessToken.token}")
    if (responseStr == null) {
      return null
    } else {
      val jsonObj = JsonObject.readFrom(responseStr)
      return jsonObj.getString("room_id", null)
    }
  }

  fun sendMessage(accessToken: AccessToken, roomId: String, message: String): String {
    val postBody = """
      {"msgtype":"m.text", "body":"$message"}
    """

    val responseStr = net.doPost(apiURL + "rooms/$roomId/send/m.room.message?access_token=${accessToken.token}", postBody)
    val jsonObj = JsonObject.readFrom(responseStr)
    val eventId = jsonObj.getString("event_id", null)
    return eventId
  }

  fun initialSync(accessToken: AccessToken):SyncResult? {
    val responseStr = net.doGet(apiURL + "initialSync?access_token=${accessToken.token}")
    if (responseStr == null) {
      return null
    }
    val jsonObj = JsonObject.readFrom(responseStr)
    val rooms = jsonObj.getArray("rooms")
    val roomList = rooms.map {room ->
      val roomObj = room.asObject()
      val messages = roomObj.getObject("messages")
      val chunks = messages.getArray("chunk").map{it.asObject()}
      val messageList = parseChunks(chunks)
      val states = roomObj.getArray("state")
      val aliasStates = states.filter {it.asObject().getString("type", null) == "m.room.aliases" }
      val aliases = aliasStates.flatMap {
        it.asObject().getObject("content").getArray("aliases").map{it.asString()}
      }
      val stateList = states.map { state ->
        val so = state.asObject()
        State(so.getString("type", null), so.getLong("origin_server_ts", 0L), so.getString("user_id", null), so.getObject("content"))
      }
      Room(roomObj.getString("room_id", null), aliases, messageList.toLinkedList(), stateList)
    }
    val presence = parseChunks(jsonObj.getArray("presence").map{it.asObject()})
    return SyncResult(roomList, presence)
  }

  fun getEvents(accessToken: AccessToken, from: String?):EventResult? {
    val eventURL = apiURL + "events?access_token=${accessToken.token}" + (from?.let{"&from="+it}?: "")
    val responseStr = net.doGet(eventURL)
    val jsonObj = JsonObject.readFrom(responseStr)
    val chunks = jsonObj.getArray("chunk")
    return EventResult(parseChunks(chunks.map{it.asObject()}), jsonObj.getString("end", null))
  }

  private fun parseChunks(chunk: List<JsonObject>): List<Message> {
    val messageList = chunk.map { messageObj ->
      val userId = messageObj.getString("user_id", "")
      val type = messageObj.getString("type", "")
      val eventId:String? = messageObj.getString("event_id", null)
      val roomId:String? = messageObj.getString("room_id", null)
      Message(eventId, messageObj.getLong("origin_server_ts", 0L), roomId, type, userId, messageObj.getObject("content"))
    }
    return messageList
  }

  private val mxcRegex = "^mxc://(.*)/([^#]*)(#auto)?$".toRegex()

  fun getAvatarThumbnailURL(mxcURL: String):String {
    val matcher = mxcRegex.matcher(mxcURL)
    if (matcher.matches()) {
      val serverName = matcher.group(1)
      val mediaId = matcher.group(2)
      return mediaURL + "thumbnail/$serverName/$mediaId?width=24&height=24"
    } else {
      return ""
    }
  }
}

private class Net(val client: OkHttpClient) {
  private final val jsonMediaType = MediaType.parse("application/json;; charset=utf-8")

  // TODO: a non-blank UA String
  private final val uaString = ""

  fun doGet(url:String):String? {
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", uaString)
      .build()

    val response = client.newCall(request).execute()

    if (!response.isSuccessful()) {
      return null
    } else {
      return response.body().string()
    }
  }

  fun doPost(url:String, json:String):String {
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", uaString)
      .post(RequestBody.create(jsonMediaType, json))
      .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful()) throw IOException("Unexpected code " + response)

    return response.body().string()
  }
}
