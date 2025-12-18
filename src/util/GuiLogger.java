package util;
import javax.swing.*;

public class GuiLogger {
    private static JTextArea area;
    private static long startTime;

    private GuiLogger() {}

    public static void init(JTextArea textArea) {
        area = textArea;
        startTime = System.currentTimeMillis();
    }

    public static void log(String message) {
        long currentTime = System.currentTimeMillis() - startTime;
        String timeStamp = String.format("[%04d.%03d] ",
                currentTime / 1000, currentTime % 1000);

        SwingUtilities.invokeLater(() ->
                area.append(timeStamp + message + "\n")
        );
    }
}
