package co.uproot.unplug

import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import org.fxmisc.easybind.EasyBind
import java.util.HashMap
import javafx.collections.FXCollections
import javafx.beans.property.SimpleBooleanProperty

import javafx.beans.property.SimpleBooleanProperty

class UserState(val id: String) {
  val typing = SimpleBooleanProperty(false)
  val present = SimpleBooleanProperty(false)
  val displayName = SimpleStringProperty("");
}

// TODO: Avoid storing entire user state for every room. Instead have a common store and a lookup table
class AppState() {
  val currRoomId = SimpleStringProperty("")

  val currChatMessageList = SimpleObjectProperty<ObservableList<Message>>()
  val currUserList = SimpleObjectProperty<ObservableList<UserState>>()

  val roomList = SimpleListProperty<Room>(FXCollections.observableArrayList())
  val roomNameList = EasyBind.map(roomList, {(room: Room) -> room.aliases.firstOrNull() ?: room.id })

  private final val roomChatMessageStore = HashMap<String, ObservableList<Message>>()
  private final val roomUserStore = HashMap<String, ObservableList<UserState>>()

  fun processSyncResult(result: SyncResult) {
    roomList.setAll(result.roomList)
    result.roomList.stream().forEach { room ->
      getRoomChatMessages(room.id).setAll(room.chatMessages())
      val users = getRoomUsers(room.id)
      room.states.forEach {(state: State) ->
        if (state.type == "m.room.member" && state.content.getString("membership", "") == "join") {
          val us = UserState(state.userId)
          val displayName = state.content.get("displayname")?.let { if (it.isString()) it.asString() else null } ?: state.userId
          us.displayName.setValue(displayName)
          users.add(us)
        }
      }
    }
  }

  fun processEventsResult(eventResult: EventResult) {
    eventResult.roomMessageList.forEach { room ->
      getRoomChatMessages(room.id).addAll(room.messages)
    }
    eventResult.userMessages.forEach { userMsg ->
      when(userMsg.type) {
        "m.typing" -> {
          val usersTyping = userMsg.content.getArray("user_ids").map { it.asString() }
          roomUserStore.values().forEach { users ->
            users.filter { usersTyping.contains(it.id) }.forEach { it.typing.set(true) }
          }
        }
        "m.presence" -> {
          roomUserStore.values().forEach { users ->
            val userId = userMsg.content.getString("user_id", null)
            users.firstOrNull { it.id == userId }?.let { it.present.set(true) }
          }
        }
        else -> {
          println("Unhandled message: " + userMsg)

        }

      }
    }

  }

  synchronized private fun getRoomChatMessages(roomId: String): ObservableList<Message> {
    return getOrCreate(roomChatMessageStore, roomId, { FXCollections.observableArrayList<Message>() })
  }

  synchronized private fun getRoomUsers(roomId: String): ObservableList<UserState> {
    return getOrCreate(roomUserStore, roomId, {
      FXCollections.observableArrayList<UserState>({ userState -> array(userState.present, userState.displayName, userState.typing) })
    })
  }

  {
    EasyBind.subscribe(currRoomId, {(id: String?) ->
      if (id != null) {
        currChatMessageList.set(getRoomChatMessages(id))
        currUserList.set(getRoomUsers(id))
      } else {
        currChatMessageList.set(SimpleListProperty<Message>())
        currUserList.set(SimpleListProperty<UserState>())
      }
    })
  }

  synchronized private fun getOrCreate<T>(messageStore: HashMap<String, ObservableList<T>>, roomId: String, creator: () -> ObservableList<T>): ObservableList<T> {
    val messages = messageStore.get(roomId)
    if (messages == null) {
      val newList = SimpleListProperty<T>(creator())
      messageStore.put(roomId, newList)
      return newList
    } else {
      return messages
    }

  }

}
