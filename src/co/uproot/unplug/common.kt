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

  private val SECONDS_PER_YEAR = (60L*60L*24L*365L)
  private val SECONDS_PER_DECADE = (10L * SECONDS_PER_YEAR)

  val weight = EasyBind.combine(typing, present, lastActiveAgo, {(t, p, la) ->
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

  val avatarImage = EasyBind.map(avatarURL) {url ->
    if (url.isEmpty()) Image("/default-avatar.png") else Image(url, 32.0, 32.0, true, true, true)
  }
}

class RoomState(val id:String, val aliases: MutableList<String>)

// TODO: Avoid storing entire user state for every room. Instead have a common store and a lookup table
class AppState() {
  val currRoomId = SimpleStringProperty("")

  val currChatMessageList = SimpleObjectProperty<ObservableList<Message>>()
  val currUserList = SimpleObjectProperty<ObservableList<UserState>>()

  val roomStateList = SimpleListProperty<RoomState>(FXCollections.observableArrayList())
  val roomNameList = EasyBind.map(roomStateList, {(room: RoomState) -> room.aliases.firstOrNull() ?: room.id })

  private final val roomChatMessageStore = HashMap<String, ObservableList<Message>>()
  private final val roomUserStore = HashMap<String, ObservableList<UserState>>()

  synchronized fun processSyncResult(result: SyncResult, api:API) {
    result.rooms.stream().forEach { room ->
      val existingRoomState = roomStateList.firstOrNull { it.id == room.id }
      if (existingRoomState == null) {
        roomStateList.add(RoomState(room.id, LinkedList(room.aliases)))
      } else {
        existingRoomState.aliases.addAll(room.aliases)
      }

      getRoomChatMessages(room.id).setAll(room.chatMessages())
      val users = getRoomUsers(room.id)
      room.states.forEach {(state: State) ->
        when (state.type) {
          "m.room.member" -> {
            if (state.content.getString("membership", "") == "join") {
              val us = UserState(state.userId)
              val displayName = state.content.getStringOrElse("displayname", state.userId)
              us.displayName.setValue(displayName)
              us.avatarURL.setValue(api.getAvatarThumbnailURL(state.content.getStringOrElse("avatar_url","")))
              users.add(us)
            } else {
              users.removeFirstMatching { it.id == state.userId }
            }
          }
          "m.room.aliases" -> {
            // TODO
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
        roomUserStore.values().forEach { users ->
          val userId = p.content.getString("user_id", null)
          users.firstOrNull { it.id == userId }?.let {
            it.present.set(p.content.getString("presence", "") == "online")
            it.lastActiveAgo.set(p.content.getLong("last_active_ago", java.lang.Long.MAX_VALUE))
          }
        }
      }
    }

    roomUserStore.values().forEach { users ->
      FXCollections.sort(users, {(a,b) -> b.weight.get() - a.weight.get()})
    }
  }

  fun processEventsResult(eventResult: EventResult, api:API) {
    eventResult.messages.forEach { message ->
      when (message.type) {
        "m.typing" -> {
          val usersTyping = message.content.getArray("user_ids").map { it.asString() }
          roomUserStore.values().forEach { users ->
            users.forEach { it.typing.set(usersTyping.contains(it.id)) }
          }
        }
        "m.presence" -> {
          roomUserStore.values().forEach { users ->
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
            val users = getRoomUsers(message.roomId)
            getRoomChatMessages(message.roomId).add(message)
            if (message.content.getString("membership", "") == "join") {
              val us = UserState(message.userId)
              val displayName = message.content.get("displayname")?.let { if (it.isString()) it.asString() else null } ?: message.userId
              us.displayName.setValue(displayName)
              us.avatarURL.setValue(api.getAvatarThumbnailURL(message.content.getStringOrElse("avatar_url","")))
              users.add(us)
            } else {
              users.removeFirstMatching { it.id == message.userId }
            }
          }
        }
        else -> {
          println("Unhandled message: " + message)

        }

      }
    }

    roomUserStore.values().forEach { users ->
      FXCollections.sort(users, {(a,b) -> b.weight.get() - a.weight.get()})
    }
  }

  synchronized private fun getRoomChatMessages(roomId: String): ObservableList<Message> {
    return getOrCreate(roomChatMessageStore, roomId, { FXCollections.observableArrayList<Message>() })
  }

  synchronized private fun getRoomUsers(roomId: String): ObservableList<UserState> {
    return getOrCreate(roomUserStore, roomId, {
      FXCollections.observableArrayList<UserState>({ userState -> array(userState.present, userState.displayName, userState.avatarImage, userState.typing, userState.weight) })
    })
  }

  {
    EasyBind.subscribe(currRoomId, {(id: String?) ->
      if (id != null && !id.isEmpty()) {
        currChatMessageList.set(getRoomChatMessages(id))
        currUserList.set(getRoomUsers(id))
      } else {
        currChatMessageList.set(SimpleListProperty<Message>())
        currUserList.set(SimpleListProperty<UserState>())
      }
    })

  }

  synchronized private fun getOrCreate<T>(store: HashMap<String, ObservableList<T>>, roomId: String, creator: () -> ObservableList<T>): ObservableList<T> {
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
  for ((index,value) in this.withIndex()) {
    if (predicate(value)) {
      this.remove(index)
      break;
    }
  }

}

fun JsonObject.getStringOrElse(name:String, elseValue:String):String {
 return get(name)?.let { if (it.isString()) it.asString() else null } ?: elseValue
}
