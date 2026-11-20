# Terminal Video & Webcam

A Java webcam preview controlled from the terminal. View the live camera feed, save snapshots, and stop the app with a terminal command or the preview window.

## Requirements

- Java 11 or newer
- Maven
- A connected webcam and desktop session

## Run

From this folder:

```powershell
mvn compile exec:java
```

Type `snap` in the terminal to save a JPEG under `captures/`. Type `help` to see commands or `quit` to exit. Closing the preview window also stops the app.

Choose a camera index and snapshot folder if needed:

```powershell
mvn compile exec:java "-Dexec.args=1 my-captures"
```

Camera indexes start at `0`. Maven downloads the webcam library on the first run.
