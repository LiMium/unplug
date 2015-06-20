package co.uproot.unplug

import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Service
import javafx.concurrent.Task


class LoginService() : Service<LoginResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  var baseURL: String = ""

  override fun createTask(): Task<LoginResult>? {
    val api = API(baseURL)

    return object : Task<LoginResult>() {
      override fun call(): LoginResult? {
        updateMessage("Logging In")
        val loginResult = api.login(userName.get(), password.get())
        if (loginResult == null) {
          updateMessage("Login Failed")
          failed()
          return null
        } else {
          updateMessage("Logged In Successfully")
          return loginResult
        }
      }
    }
  }
}

class RoomSyncService(val loginResult: LoginResult, val roomId: String) : Service<SyncResult>() {
  override fun createTask(): Task<SyncResult>? {
    val api = loginResult.api
    return object : Task<SyncResult>() {
      override fun call(): SyncResult? {
        updateMessage("Syncing")
        val result = api.roomInitialSync(loginResult.accessToken, roomId)
        if (result == null) {
          updateMessage("Sync Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return result
        }
      }
    }
  }
}


class SyncService(val loginResult: LoginResult) : Service<SyncResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  override fun createTask(): Task<SyncResult>? {
    val api = loginResult.api
    return object : Task<SyncResult>() {
      override fun call(): SyncResult? {
        updateMessage("Syncing")
        val result = api.initialSync(loginResult.accessToken)
        if (result == null) {
          updateMessage("Sync Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return result
        }
      }
    }
  }
}


class CreateRoomService(val loginResult: LoginResult, val roomname: String, val visibility: String) : Service<CreateRoomResult>() {

  override fun createTask(): Task<CreateRoomResult>? {
    return object : Task<CreateRoomResult>() {
      override fun call(): CreateRoomResult? {
        val createRoomResult = loginResult.api.createRoom(loginResult.accessToken, roomname, visibility)
        if (createRoomResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return createRoomResult
        }
      }
    }
  }
}

class JoinRoomService(val loginResult: LoginResult, val room: RoomIdentifier) : Service<JoinRoomResult>() {

  override fun createTask(): Task<JoinRoomResult>? {
    return object : Task<JoinRoomResult>() {
      override fun call(): JoinRoomResult? {
        val joinResult = loginResult.api.joiningRoon(loginResult.accessToken, room)
        if (joinResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return joinResult
        }
      }
    }
  }
}

class InviteMemberService(val loginResult: LoginResult, val room: RoomIdentifier, val memName: String) : Service<InviteMemResult>() {

  override fun createTask(): Task<InviteMemResult>? {
    return object : Task<InviteMemResult>() {
      override fun call(): InviteMemResult? {
        val inviteResult = loginResult.api.invitingMember(loginResult.accessToken, room, memName)
        if (inviteResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return inviteResult
        }
      }
    }
  }
}

class BanRoomService(val loginResult: LoginResult, val room: RoomIdentifier, val memId: String, val appState: AppState) : Service<BanRoomResult>() {

  override fun createTask(): Task<BanRoomResult>? {
    return object : Task<BanRoomResult>() {
      override fun call(): BanRoomResult? {
        val banRoomResult = loginResult.api.banningMember(loginResult.accessToken, room, memId, appState)
        if (banRoomResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return banRoomResult
        }
      }
    }
  }
}

class LeaveRoomService(val loginResult: LoginResult, val roomIdentifier: RoomIdentifier) : Service<LeaveRoomResult>() {

  override fun createTask(): Task<LeaveRoomResult>? {
    return object : Task<LeaveRoomResult>() {
      override fun call(): LeaveRoomResult? {
        val leaveResult = loginResult.api.leavingRoom(loginResult.accessToken, roomIdentifier)
        if (leaveResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return leaveResult
        }
      }
    }
  }
}

class EventService(val loginResult: LoginResult) : Service<EventResult>() {
  private var from: String? = null

  override fun createTask(): Task<EventResult>? {
    return object : Task<EventResult>() {
      override fun call(): EventResult? {
        val eventResult = loginResult.api.getEvents(loginResult.accessToken, from)
        if (eventResult == null) {
          updateMessage("Events Failed")
          failed()
          return null
        } else {
          updateMessage("")
          from = eventResult.end
          return eventResult
        }
      }
    }
  }
}

class SendResult(eventId: String)

class SendMessageService(val loginResult: LoginResult, val roomId: String, val msg: String) : Service<SendResult>() {

  override fun createTask(): Task<SendResult>? {
    return object : Task<SendResult>() {
      override fun call(): SendResult? {
        val eventId = loginResult.api.sendMessage(loginResult.accessToken, roomId, msg)
        if (eventId == null) {
          updateMessage("Sending Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return SendResult(eventId)
        }
      }
    }
  }
}
