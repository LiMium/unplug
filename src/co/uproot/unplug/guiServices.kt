package co.uproot.unplug

import javafx.concurrent.Service
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Task

data class LoginResult(val accessToken: AccessToken, val api: API)

class LoginService() : Service<LoginResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  var baseURL:String = ""

  override fun createTask(): Task<LoginResult>? {
    val api = API(baseURL)

    return object : Task<LoginResult>() {
      override fun call(): LoginResult? {
        updateMessage("Logging In")
        val accessToken = api.login(userName.get(), password.get())
        if (accessToken == null) {
          updateMessage("Login Failed")
          failed()
          return null
        } else {
          updateMessage("Logged In Successfully")
          return LoginResult(accessToken, api)
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

class EventService(val loginResult : LoginResult) : Service<EventResult>() {
  private var from : String? = null

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

class SendResult(eventId:String)

class SendMessageService(val loginResult : LoginResult, val roomId:String, val msg: String) : Service<SendResult>() {

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
