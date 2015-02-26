### unplug

A cross-platform [matrix](https://matrix.org) client.

### Howto run
Download the `.jar` file from the release page. Then,

```
java -jar unplug-version.jar
```

This will launch the GUI. A CLI version will soon be available and launching it would be as simple as passing
`-c` as an argument:

```
java -jar unplug-version.jar -c
```

### Chat
[#unplug:matrix.org](https://matrix.org/beta/#/room/#unplug:matrix.org)

### Status
Consider this alpha quality, with only rudimentary features.

Currently, the application doesn't persist any state. The Matrix protocol is very convenient in this regard;
it allows quick bootstrap of the client state with a single API call.

However, later versions will persist preferences, chat history, etc.

#### Roadmap

##### Version 0.1
* Join / Leave chat rooms
* History back-filling (scroll up to see earlier chat messages)
* Sorting of Users and Room names by activity
* Some UI polish

##### Version 0.2 and beyond
* Persistence of preferences, etc
* CLI interface
* More UI polish

### Legal
* License: GPL3
* Copyright: Uproot Labs 2015
* CLA: TODO
