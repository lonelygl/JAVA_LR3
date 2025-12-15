package elevator;

import gui.ElevatorFrame;

public class Main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ElevatorFrame frame = new ElevatorFrame();
            frame.setVisible(true);
            frame.setLocationRelativeTo(null);
        });
    }
}