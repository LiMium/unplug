### unplug

A cross-platform [matrix](https://matrix.org) client.

### How to run
Download the `.jar` file from the [release page](https://github.com/UprootLabs/unplug/releases). Then,

```
java -jar unplug-version.jar
```

This will launch the GUI. You can add additional options like this:
```
java -jar unplug-version.jar [userId] [server] [password]
```

A CLI version will soon be available and launching it would be as simple as passing
`-c` as an argument:

```
java -jar unplug-version.jar -c
```

### Discuss
[#unplug:matrix.org](https://matrix.org/beta/#/room/#unplug:matrix.org)

### Status
Consider this alpha quality, with only rudimentary features.

Currently, the application doesn't persist any state. The Matrix protocol is very convenient in this regard;
it allows quick bootstrap of the client state with a single API call.

However, later versions will persist preferences, chat history, etc.

#### Roadmap

##### Version 0.2
* Join / Leave chat rooms
* Handle all types of events (notices, etc)
* History back-filling (scroll up to see earlier chat messages)
* User avatars
* Sorting of Users and Room names by activity
* User presence update (timeout and mark them inactive, etc)
* Some UI polish

##### Version 0.3+
* Persistence of preferences, etc
* CLI interface
* More UI polish

##### Version 1.0 and beyond
* Audio / Video

### Legal
* License: GPL3
* Copyright: Uproot Labs 2015

#### CLA
All contributions to this repo implicitly assign the copyright of those changes to Uproot Labs. In return, Uproot Labs
guarantees that all contributions to this repo will always be available under an open-source License.
