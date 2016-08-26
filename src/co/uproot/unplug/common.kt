package co.uproot.unplug

import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import org.fxmisc.easybind.EasyBind
import java.util.HashMap
import javafx.collections.FXCollections

import javafx.beans.property.SimpleBooleanProperty
import java.util.LinkedList
import com.eclipsesource.json.JsonObject
import javafx.scene.image.Image
import javafx.beans.property.SimpleLongProperty

data class UserState(val id: String) {
  val typing = SimpleBooleanProperty(false)
  val present = SimpleBooleanProperty(false)
  val displayName = SimpleStringProperty("");
  val avatarURL = SimpleStringProperty("");
  val lastActiveAgo = SimpleLongProperty(java.lang.Long.MAX_VALUE)

  private val SECONDS_PER_YEAR = (60L * 60L * 24L * 365L)
  private val SECONDS_PER_DECADE = (10L * SECONDS_PER_YEAR)

  val weight = EasyBind.combine(typing, present, lastActiveAgo, { t, p, la ->
    val laSec = Math.min(la.toLong() / 1000, SECONDS_PER_DECADE)
    var result = (1 + (SECONDS_PER_DECADE - laSec )).toInt()
    if (t) {
      result *= 2
    }
    if (p) {
      result *= 2
    }
    result
  })

  override fun toString() = "$id ${typing.get()} ${present.get()} ${weight.get()}"

}

data class RoomState(val id: String, val aliases: ObservableList<String>)

// TODO: Avoid storing entire user state for every room. Instead have a common store and a lookup table
class AppState() {
  val currRoomId = SimpleStringProperty("")

  val currChatMessageList = SimpleObjectProperty<ObservableList<Message>>()
  val currUserList = SimpleObjectProperty<ObservableList<UserState>>()

  val roomStateList = SimpleListProperty(FXCollections.observableArrayList<RoomState>({ roomState -> arrayOf(roomState.aliases) }))
  val roomNameList = EasyBind.map(roomStateList, { room: RoomState -> room.aliases.firstOrNull() ?: room.id })

  private final val roomChatMessageStore = HashMap<String, ObservableList<Message>>()
  private final val roomUserStore = HashMap<String, ObservableList<UserState>>()

  @Synchronized
  fun processSyncResult(result: SyncResult, api: API) {
    result.rooms.asSequence().forEach { room ->
      val existingRoomState = roomStateList.firstOrNull { it.id == room.id }
      if (existingRoomState == null) {
        val aliasList = FXCollections.observableArrayList<String>()
        aliasList.addAll(room.aliases)
        roomStateList.add(RoomState(room.id, aliasList))
      } else {
        existingRoomState.aliases.addAll(room.aliases)
      }
      getRoomChatMessages(room.id).setAll(room.chatMessages())
      val users = getRoomUsers(room.id)
      room.states.forEach { state: State ->
        when (state.type) {
          "m.room.member" -> {
            val membership = state.content.getString("membership", "")
            if (membership == "join") {
              val us = UserState(state.stateKey)
              val displayName = state.content.getStringOrElse("displayname", state.stateKey)
              us.displayName.setValue(displayName)
              us.avatarURL.setValue(api.getAvatarThumbnailURL(state.content.getStringOrElse("avatar_url", "")))
              users.add(us)
            } else if (membership == "leave") {
              users.removeFirstMatching { it.id == state.userId }
            } else {
              println("Not handled membership message: $membership")
              println("  room: ${room.getAliasOrId()}, from: ${state.userId},  key: ${state.stateKey}")
            }
          }
          "m.room.aliases" -> {
            // TODO: This doesn't need to handled here, because aliases are being already parsed by the API class
            /*
            val aliases = state.content.getArray("aliases").map{it.asString()}
            existingRoomState?.let {it.aliases.addAll(aliases)}
            */
          }
          "m.room.power_levels" -> {
            // TODO
          }
          "m.room.join_rules" -> {
            // TODO
          }
          "m.room.create" -> {
            // TODO
          }
          "m.room.topic" -> {
            // TODO
          }
          "m.room.name" -> {
            // TODO
          }
          "m.room.config" -> {
            // TODO
          }
          else -> {
            System.err.println("Unhandled state type: " + state.type)
            System.err.println(Thread.currentThread().getStackTrace().take(2).joinToString("\n"))
            System.err.println()
          }
        }
      }
    }

    result.presence.forEach { p ->
      if (p.type == "m.presence") {
        roomUserStore.values.forEach { users ->
          val userId = p.content.getString("user_id", null)
          users.firstOrNull { it.id == userId }?.let {
            it.present.set(p.content.getString("presence", "") == "online")
            it.lastActiveAgo.set(p.content.getLong("last_active_ago", java.lang.Long.MAX_VALUE))
          }
        }
      }
    }

    roomUserStore.values.forEach { users ->
      FXCollections.sort(users, { a, b -> b.weight.get() - a.weight.get() })
    }
  }

  fun processEventsResult(eventResult: EventResult, api: API, loginResult: LoginResult) {
    eventResult.messages.forEach { message ->
      when (message.type) {
        "m.typing" -> {
          val usersTyping = message.content.getArray("user_ids").map { it.asString() }
          roomUserStore.values.forEach { users ->
            users.forEach { it.typing.set(usersTyping.contains(it.id)) }
          }
        }
        "m.presence" -> {
          roomUserStore.values.forEach { users ->
            val userId = message.content.getString("user_id", null)
            users.firstOrNull { it.id == userId }?.let {
              it.present.set(message.content.getString("presence", "") == "online")
              it.lastActiveAgo.set(message.content.getLong("last_active_ago", java.lang.Long.MAX_VALUE))
            }
          }
        }
        "m.room.message" -> {
          if (message.roomId != null) {
            getRoomChatMessages(message.roomId).add(message)
          }
        }
        "m.room.member" -> {
          if (message.roomId != null) {
            val messageFromLoggedInUser = loginResult.userId == message.userId

            val users = getRoomUsers(message.roomId)

            val membership = message.content.getString("membership", "")

            if (membership == "join") {
              if (messageFromLoggedInUser) {
                val roomService = RoomSyncService(loginResult, message.roomId)
                roomService.setOnSucceeded {
                  val value = roomService.getValue()
                  if (value != null) {
                    processSyncResult(value, loginResult.api)
                  }
                }
                roomService.start()
              } else {
                val existingUser = users.firstOrNull { it.id == message.userId }
                val displayName = message.content.get("displayname")?.let { if (it.isString()) it.asString() else null } ?: message.userId
                val avatarURL = api.getAvatarThumbnailURL(message.content.getStringOrElse("avatar_url", ""))
                val user = if (existingUser == null) {
                  val us = UserState(message.userId)
                  users.add(us)
                  us
                } else {
                  existingUser
                }
                user.displayName.set(displayName)
                user.avatarURL.set(avatarURL)
              }
            } else if (membership == "leave") {
              users.removeFirstMatching { it.id == message.userId }
              if (messageFromLoggedInUser) {
                roomStateList.removeFirstMatching { it.id == message.roomId }
                roomUserStore.remove(message.roomId)
                roomChatMessageStore.remove(message.roomId)
              }
            } else if (membership == "ban") {
              val name = message.content.getString("displayname", "")
              users.removeFirstMatching { it.displayName.get().equals(name) }
            } else {
              println("Unhandled membership: $membership")
            }
          }
        }
        "m.room.aliases" -> {

          val existingRoomState = roomStateList.firstOrNull { it.id == message.roomId }
          if (existingRoomState != null) {
            val alias = message.content.getArray("aliases").map { it.asString() }
            val length = alias.toString().length
            val aliases = alias.toString().substring(1, length - 1)
            existingRoomState.aliases.add(aliases)
          }
        }
        else -> {
          println("Unhandled message: " + message)

        }

      }
    }

    roomUserStore.values.forEach { users ->
      FXCollections.sort(users, { a, b -> b.weight.get() - a.weight.get() })
    }
  }

  @Synchronized private fun getRoomChatMessages(roomId: String): ObservableList<Message> {
    return getOrCreate(roomChatMessageStore, roomId, { FXCollections.observableArrayList<Message>() })
  }

  @Synchronized public fun getRoomUsers(roomId: String): ObservableList<UserState> {
    return getOrCreate(roomUserStore, roomId, {
      FXCollections.observableArrayList<UserState>({ userState -> arrayOf(userState.present, userState.displayName, userState.avatarURL, userState.typing, userState.weight) })
    })
  }

  @Synchronized public fun getCurrRoomNameOrId(): String? {
    val currRoom = roomStateList.firstOrNull { it.id == currRoomId.get() }
    val alias = currRoom?.aliases?.firstOrNull()
    val name = alias ?: currRoom?.id
    return name
  }

  init {
    EasyBind.subscribe(currRoomId, { id: String? ->
      if (id != null && !id.isEmpty()) {
        currChatMessageList.set(getRoomChatMessages(id))
        currUserList.set(getRoomUsers(id))
      } else {
        currChatMessageList.set(SimpleListProperty<Message>())
        currUserList.set(SimpleListProperty<UserState>())
      }
    })

  }

  @Synchronized private fun <T>getOrCreate(store: HashMap<String, ObservableList<T>>, roomId: String, creator: () -> ObservableList<T>): ObservableList<T> {
    val messages = store.get(roomId)
    if (messages == null) {
      val newList = SimpleListProperty(creator())
      store.put(roomId, newList)
      return newList
    } else {
      return messages
    }

  }

}

fun <T> ObservableList<T>.removeFirstMatching(predicate: (T) -> Boolean) {
  for ((index, value) in this.withIndex()) {
    if (predicate(value)) {
      this.removeAt(index)
      break;
    }
  }

}

fun JsonObject.getStringOrElse(name: String, elseValue: String): String {
  return get(name)?.let { if (it.isString()) it.asString() else null } ?: elseValue
}
