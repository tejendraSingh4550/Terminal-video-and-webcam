import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WebcamTerminal {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private WebcamTerminal() { }

    public static void main(String[] args) throws Exception {
        int cameraIndex = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        Path outputDir = Paths.get(args.length > 1 ? args[1] : "captures");

        Webcam[] cameras = Webcam.getWebcams().toArray(new Webcam[0]);
        if (cameraIndex < 0 || cameraIndex >= cameras.length) {
            System.err.println("Camera index " + cameraIndex + " not found. Available cameras: " + cameras.length);
            System.err.println("Run with: mvn exec:java -Dexec.args=\"<camera-index> [output-folder]\"");
            return;
        }

        Webcam camera = cameras[cameraIndex];
        if (!camera.open()) {
            System.err.println("Could not open camera: " + camera.getName());
            return;
        }

        Files.createDirectories(outputDir);
        BlockingQueue<String> commands = new LinkedBlockingQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
        JFrame window = createPreview(camera.getName(), running);

        Thread inputThread = new Thread(() -> readCommands(commands), "terminal-input");
        inputThread.setDaemon(true);
        inputThread.start();

        System.out.println("Preview open for " + camera.getName() + ". Commands: snap, help, quit. Press Q/Esc in preview to exit.");
        try {
            while (running.get()) {
                BufferedImage frame = camera.getImage();
                if (frame != null) {
                    latestFrame.set(frame);
                    SwingUtilities.invokeLater(() -> {
                        if (running.get()) {
                            JLabel label = (JLabel) window.getContentPane().getComponent(0);
                            label.setIcon(new ImageIcon(frame));
                            label.setText("");
                        }
                    });
                }

                String command = commands.poll();
                if (command != null) {
                    switch (command) {
                        case "q":
                        case "quit":
                        case "exit":
                            running.set(false);
                            break;
                        case "h":
                        case "help":
                        case "?":
                            System.out.println("Commands: snap (save a snapshot), help, quit");
                            break;
                        case "s":
                        case "snap":
                        case "snapshot":
                            saveSnapshot(latestFrame.get(), outputDir);
                            break;
                        case "":
                            break;
                        default:
                            System.out.println("Unknown command: " + command + ". Type 'help' for commands.");
                    }
                }
                Thread.sleep(30);
            }
        } finally {
            running.set(false);
            camera.close();
            SwingUtilities.invokeLater(window::dispose);
        }
    }

    private static JFrame createPreview(String cameraName, AtomicBoolean running) throws Exception {
        AtomicReference<JFrame> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JLabel image = new JLabel("Starting camera…", SwingConstants.CENTER);
            JFrame frame = new JFrame("Webcam Terminal — " + cameraName);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    running.set(false);
                }
            });
            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "stop-preview");
            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop-preview");
            frame.getRootPane().getActionMap().put("stop-preview", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    running.set(false);
                }
            });
            frame.add(image);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            result.set(frame);
        });
        return result.get();
    }

    private static void readCommands(BlockingQueue<String> commands) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String command = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
                commands.offer(command);
                if (command.equals("q") || command.equals("quit") || command.equals("exit")) {
                    return;
                }
            }
        }
        commands.offer("quit");
    }

    private static void saveSnapshot(BufferedImage frame, Path outputDir) {
        if (frame == null) {
            System.out.println("No camera frame is ready yet.");
            return;
        }
        Path destination = outputDir.resolve("webcam_" + LocalDateTime.now().format(FILE_TIME) + ".jpg");
        try {
            ImageIO.write(frame, "jpg", destination.toFile());
            System.out.println("Saved snapshot: " + destination.toAbsolutePath());
        } catch (IOException exception) {
            System.err.println("Could not save snapshot: " + exception.getMessage());
        }
    }
}
