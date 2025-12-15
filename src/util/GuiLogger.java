package util;
import javax.swing.*;

public class GuiLogger {

    private static JTextArea area;

    private GuiLogger() {}

    public static void init(JTextArea textArea) {
        area = textArea;
    }

    public static void log(String message) {
        SwingUtilities.invokeLater(() ->
                area.append(message + "\n")
        );
    }
}
