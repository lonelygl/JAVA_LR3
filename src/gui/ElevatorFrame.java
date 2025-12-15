package gui;

import control.Dispatcher;
import model.Elevator;
import model.Direction;
import util.GuiLogger;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class ElevatorFrame extends JFrame {
    private Dispatcher dispatcher;
    private int userCounter = 1;
    private int maxFloors = 0;
    private List<Thread> elevatorThreads = new ArrayList<>();
    private Thread dispatcherThread;

    public ElevatorFrame() {
        setTitle("Elevator Simulation");
        setSize(900, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Обработчик закрытия окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAllThreads();
            }
        });

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        util.GuiLogger.init(logArea);

        // Панель настройки
        JPanel setupPanel = new JPanel();
        JTextField floorsField = new JTextField(4);
        JTextField elevatorsField = new JTextField(4);
        JButton startButton = new JButton("Запуск");
        JButton stopButton = new JButton("Остановить");
        stopButton.setEnabled(false);

        setupPanel.add(new JLabel("Этажей (N):"));
        setupPanel.add(floorsField);
        setupPanel.add(new JLabel("Лифтов (M):"));
        setupPanel.add(elevatorsField);
        setupPanel.add(startButton);
        setupPanel.add(stopButton);

        // Панель вызова лифта
        JPanel callPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        JPanel floorPanel = new JPanel();
        JTextField fromField = new JTextField(4);
        JTextField toField = new JTextField(4);
        JButton callButton = new JButton("Вызвать лифт");
        callButton.setEnabled(false);

        floorPanel.add(new JLabel("Вы на этаже:"));
        floorPanel.add(fromField);
        floorPanel.add(new JLabel("Цель:"));
        floorPanel.add(toField);
        floorPanel.add(callButton);

        // Панель выбора направления
        JPanel directionPanel = new JPanel();
        ButtonGroup directionGroup = new ButtonGroup();
        JRadioButton upButton = new JRadioButton("Вверх");
        JRadioButton downButton = new JRadioButton("Вниз");
        upButton.setSelected(true);

        directionGroup.add(upButton);
        directionGroup.add(downButton);
        directionPanel.add(new JLabel("Направление:"));
        directionPanel.add(upButton);
        directionPanel.add(downButton);

        callPanel.add(floorPanel);
        callPanel.add(directionPanel);

        add(setupPanel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(callPanel, BorderLayout.SOUTH);

        // Обработчик запуска системы
        startButton.addActionListener(e -> {
            try {
                int floors = Integer.parseInt(floorsField.getText());
                int elevatorsCount = Integer.parseInt(elevatorsField.getText());

                if (floors < 2 || elevatorsCount < 1) {
                    JOptionPane.showMessageDialog(this,
                            "Некорректные параметры дома\n" +
                                    "Этажей должно быть ≥ 2\n" +
                                    "Лифтов должно быть ≥ 1",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                maxFloors = floors;
                List<Elevator> elevators = new ArrayList<>();
                elevatorThreads.clear();

                for (int i = 0; i < elevatorsCount; i++) {
                    Elevator elevator = new Elevator(i + 1, 1, floors);
                    elevators.add(elevator);
                    Thread elevatorThread = new Thread(elevator, "Elevator-" + (i + 1));
                    elevatorThreads.add(elevatorThread);
                    elevatorThread.start();
                }

                dispatcher = new Dispatcher(elevators);
                dispatcherThread = new Thread(dispatcher, "Dispatcher");
                dispatcherThread.start();

                callButton.setEnabled(true);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                floorsField.setEnabled(false);
                elevatorsField.setEnabled(false);

                GuiLogger.log(" Система запущена ");
                GuiLogger.log("Дом: " + floors + " этажей");
                GuiLogger.log("Лифтов: " + elevatorsCount);


            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Введите числовые значения",
                        "Ошибка ввода",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Обработчик остановки системы
        stopButton.addActionListener(e -> {
            stopAllThreads();
            callButton.setEnabled(false);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            floorsField.setEnabled(true);
            elevatorsField.setEnabled(true);
            GuiLogger.log(" Система остановлена ");
        });

        // Обработчик вызова лифта
        callButton.addActionListener(e -> {
            try {
                int from = Integer.parseInt(fromField.getText());
                int to = Integer.parseInt(toField.getText());

                // Проверка диапазонов
                if (maxFloors > 0 && (from < 1 || from > maxFloors || to < 1 || to > maxFloors)) {
                    JOptionPane.showMessageDialog(this,
                            "Этажи должны быть в диапазоне от 1 до " + maxFloors,
                            "Ошибка диапазона",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                //  нельзя вызвать лифт на тот же этаж
                if (from == to) {
                    JOptionPane.showMessageDialog(this,
                            "Вы уже находитесь на выбранном этаже\n" +
                                    "Введите другой номер этажа",
                            "Информация",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // Определение направления вызова
                Direction requestedDirection = (to > from) ? Direction.UP : Direction.DOWN;

                // Проверка согласованности с выбранным направлением
                Direction selectedDirection = upButton.isSelected() ? Direction.UP : Direction.DOWN;
                if (requestedDirection != selectedDirection && maxFloors > 0) {
                    int result = JOptionPane.showConfirmDialog(this,
                            "Выбранное направление (" + selectedDirection +
                                    ") не соответствует движению с " + from + " на " + to +
                                    " (" + requestedDirection + ")\n" +
                                    "Использовать правильное направление?",
                            "Предупреждение",
                            JOptionPane.YES_NO_OPTION);

                    if (result == JOptionPane.YES_OPTION) {
                        requestedDirection = selectedDirection;
                    }
                }

                if (dispatcher != null) {
                    dispatcher.submitRequest(userCounter++, from, requestedDirection, to);


                    fromField.setText("");
                    toField.setText("");

                    GuiLogger.log("Пользователь " + (userCounter-1) +
                            ": ожидание лифта на этаже " + from);
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Некорректный ввод этажей\n" +
                                "Введите числовые значения",
                        "Ошибка ввода",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }


    private void stopAllThreads() {
        // Остановка диспетчера
        if (dispatcherThread != null && dispatcherThread.isAlive()) {
            dispatcherThread.interrupt();
        }

        // Остановка всех лифтов
        for (Thread thread : elevatorThreads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }


        try {
            if (dispatcherThread != null) {
                dispatcherThread.join(1000);
            }

            for (Thread thread : elevatorThreads) {
                if (thread != null) {
                    thread.join(1000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        elevatorThreads.clear();
    }
}