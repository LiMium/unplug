package co.uproot.unplug.gui

import javafx.application.Application
import javafx.stage.Stage
import kotlinfx.builders.*
import kotlinfx.kalium.*
import kotlinfx.properties.*
import javafx.beans.property.SimpleStringProperty
import org.fxmisc.easybind.EasyBind
import javafx.scene.control.ListCell
import javafx.util.Callback
import javafx.scene.layout.Priority
import javafx.beans.value.ObservableNumberValue
import javafx.collections.FXCollections
import javafx.concurrent.Worker
import co.uproot.unplug.*
import javafx.geometry.Pos

fun main(args: Array<String>) {
  Application.launch(javaClass<UnplugApp>(), *args)
}

class UnplugApp : Application() {
  override fun start(stage: Stage?) {
    val args = getParameters().getRaw()
    val userIdInit = if (args.size() > 0) args[0] else ""
    val serverInit = if (args.size() > 1) args[1] else null
    val passwordInit = if (args.size() > 2) args[2] else ""

    val loginService = LoginService()
    val userId = TextField(userIdInit) { promptText = "Eg: bob" }

    // TODO: Use the auto-complete box from here: https://github.com/privatejava/javafx-autocomplete-field
    val serverCommonUrls = FXCollections.observableArrayList("https://matrix.org", "http://localhost:8008")
    val serverCombo = ComboBox(serverCommonUrls) {
      editable = true

      if (serverInit == null) {
        getSelectionModel().select(0)
      } else {
        getEditor().text = serverInit
      }
    }
    val password = PasswordField() {text = passwordInit}
    password.setOnAction {
      val serverText = serverCombo.editor.text()
      startLogin(loginService, serverText, stage!!)
    }

    val login = Button("Login")
    val loginStatus = Label("")

    loginStatus.textProperty().bind(loginService.messageProperty())
    loginService.userName.bind(userId.textProperty())
    loginService.password.bind(password.textProperty())

    val loginForm = GridPane(padding=Insets(left=10.0)) {
      hgap = 10.0
      vgap = 10.0
      addRow(0, Label("User id: "), userId)
      addRow(1, Label("Server: "), serverCombo)
      addRow(2, Label("Password: "), password)
      addRow(3, login)
      addRow(4, loginStatus)
    }

    login.setOnAction {
      val serverText = serverCombo.editor.text()
      startLogin(loginService, serverText, stage!!)
    }

    loginForm.disableProperty().bind(loginService.stateProperty().isNotEqualTo(Worker.State.READY))
    login.disable {
      userId.text().length() == 0 || serverCombo.editor.text().length() == 0
    }
    loginStatus.visible { loginStatus.text().length() > 0 }

    Stage(stage, title="unplug") {
      scene = Scene {
        stylesheets.add("/chat.css")
        root = VBox(spacing=10.0, padding=Insets(10.0)) {
          + loginForm
          + loginStatus
        }
      }
    }.show()
  }

  private fun startLogin(loginService: LoginService, serverText:String, stage: Stage) {
    // logginIn u true
    loginService.baseURL = "$serverText/"
    loginService.setOnSucceeded {
      val loginResult = loginService.getValue();
      if (loginResult == null) {
        // logginIn u false
        loginService.reset()
      } else {
        stage.title = "[unplug] " + loginService.userName.get() + " : " + serverText
        postLogin(stage, loginResult)
      }
    }
    loginService.setOnFailed {
      loginService.reset()
    }
    loginService.restart()
  }

  val status = SimpleStringProperty("Loading")

  val appState = AppState()

  fun postLogin(stage: Stage, loginResult: LoginResult) {
    val statusLabel = Label()
    statusLabel.textProperty().bind(status)
    statusLabel.visible { status.get().length() > 0 }

    val userListView = ListView(appState.currUserList.get()) {
      getStyleClass().add("user-list")
    }
    userListView.itemsProperty().bind(appState.currUserList)

    userListView.setCellFactory (object : Callback<javafx.scene.control.ListView<UserState>, ListCell<UserState>> {
      override fun call(list:javafx.scene.control.ListView<UserState>):ListCell<UserState> {
        return UserFormatCell()
      }
    })

    val roomListView = ListView(appState.roomNameList)

    val selectedRoomIndexProperty = roomListView.selectionModel.selectedIndexProperty()
    EasyBind.subscribe(selectedRoomIndexProperty, {indexNum ->
      val index = indexNum as Int
      if (index >= 0) {
        val room = appState.roomStateList.get(index)
        appState.currRoomId.set(room.id)
      } else {
        appState.currRoomId.set(null)
      }

    })

    val messageListView = ListView(appState.currChatMessageList.get()) {
      fixedCellSize = -1.0
    }

    messageListView.itemsProperty().bind(appState.currChatMessageList)

    messageListView.setCellFactory (object : Callback<javafx.scene.control.ListView<Message>, ListCell<Message>> {
      override fun call(list:javafx.scene.control.ListView<Message>):ListCell<Message> {
        return MessageFormatCell(messageListView.widthProperty().add(-20))
      }
    })

    val messageInputView = TextField()
    messageInputView.setOnAction {
      val msg = messageInputView.text
      messageInputView.text = ""
      val sendService = SendMessageService(loginResult, appState.currRoomId.get(), msg)
      sendService.start()
    }

    val messageView = VBox(spacing = 10.0) {
      +messageListView
      +messageInputView
    }

    javafx.scene.layout.HBox.setHgrow(messageView, Priority.ALWAYS)
    javafx.scene.layout.HBox.setHgrow(messageInputView, Priority.ALWAYS)
    javafx.scene.layout.VBox.setVgrow(messageListView, Priority.ALWAYS)


    val chatView = HBox(spacing=10.0, padding=Insets(10.0)) {
      + roomListView
      + messageView
      + userListView
    }

    javafx.scene.layout.VBox.setVgrow(chatView, Priority.ALWAYS)

    stage.setScene(Scene {
      stylesheets.add("/chat.css")

      root = VBox(spacing=10.0, padding=Insets(10.0)) {
        + statusLabel
        + chatView
      }
    })

    stage.centerOnScreen()

    val syncService = SyncService(loginResult)
    status.bind(syncService.messageProperty())
    syncService.setOnSucceeded {
      val syncResult = syncService.getValue()
      if (syncResult == null) {
        showSyncError()
      } else {
        appState.processSyncResult(syncResult, loginResult.api)
        postSync(loginResult)
      }
    }
    syncService.setOnFailed { showSyncError() }

    syncService.start()
  }

  private fun showSyncError() {
    Stage(null, "Error") {
      scene = Scene() {
        root = VBox() {
          +Label ("Error while syncing")
        }
      }
    }
  }

  fun postSync(loginResult: LoginResult) {
    val eventsService = EventService(loginResult)
    status.bind(eventsService.messageProperty())
    eventsService.setOnSucceeded {
      val eventResult = eventsService.getValue()
      if (eventResult != null) {
        appState.processEventsResult(eventResult, loginResult.api)
      }
      eventsService.restart()
    }

    eventsService.setOnFailed {
      eventsService.restart()
    }

    eventsService.start()
  }
}

class UserFormatCell() : ListCell<UserState>() {

  override fun updateItem(us: UserState?, empty: Boolean) {
    super.updateItem(us, empty)
    if (us == null || empty) {
      setGraphic(null)
    } else {
      val typing = us.typing.get()
      val present = us.present.get()
      val typingStr = if(typing) "⌨" else ""
      val id = Text("${us.id} $typingStr") {
        getStyleClass().add("unplug-text")
        getStyleClass().add("user-id")
      }
      val displayName = Text("${us.displayName.get()}") {
        getStyleClass().add("unplug-text")
        getStyleClass().add("user-displayname")
      }
      val userDetails =  VBox(spacing=2.0, padding=Insets(0.0)) {
        +id
        +displayName
      }

      val image = us.avatarImage.get()
      val avatar = ImageView(image) {
        setFitWidth(32.0)
        setFitHeight(32.0)
        setCache(true)
      }

      val graphic =  HBox(spacing=10.0, padding=Insets(2.0)) {
          +avatar
          +userDetails

        setAlignment(Pos.CENTER_LEFT)

        if (typing || present) {
          if (typing) {
            getStyleClass().add("user-typing")
          }
          if (present) {
            getStyleClass().add("user-present")
          }
        } else {
          getStyleClass().add("user-default")
        }
      }
      setGraphic(graphic)
    }

  }
}

class MessageFormatCell(val containerWidthProperty: ObservableNumberValue) : ListCell<Message>() {

  override fun updateItem(message: Message?, empty: Boolean) {
    super.updateItem(message, empty)
    val (renderText, renderClass) = if (message == null) {
      Pair("", null)
    } else {
      when (message.type) {
        "m.room.create" -> Pair("Room created by " + message.content.getString("creator", "(unexpected missing creator)"), "chat-message-meta")
        "m.room.member" -> {
          val status = if(message.content.getString("membership", "") == "join") "Joined" else "Left"
          val displayName = message.content.getString("displayname", null)?:message.userId
          Pair("$status: $displayName", "chat-message-meta")
        }
        "m.room.message" -> Pair(message.userId + ": " + message.content.getString("body", "(unexpected empty body)"), "chat-message-text")

        else -> {
          Pair("Unknown message type: ${message.type}", "chat-message-error")
        }
      }
    }

    setGraphic(Text(renderText) {
      if (renderClass != null) {
        getStyleClass().add(renderClass)
      } else {
        getStyleClass().add("unplug-text")
      }
      setWrapText(true)
      wrappingWidthProperty().bind(containerWidthProperty)
    })
  }
}

