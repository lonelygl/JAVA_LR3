package gui;

import control.Dispatcher;
import control.PassengerGenerator;
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
    private List<Elevator> elevatorsList = new ArrayList<>();
    private List<Thread> elevatorThreads = new ArrayList<>();
    private Thread dispatcherThread;
    private PassengerGenerator passengerGenerator;

    // GUI компоненты
    private JButton autoButton;
    private JTextField intervalField;
    private JTextField floorsField;
    private JTextField elevatorsField;
    private JButton startButton;
    private JButton stopButton;
    private JButton callButton;
    private JTextField fromField;
    private JTextField toField;
    private JRadioButton upButton;
    private JRadioButton downButton;

    // динамические панели для лифтов
    private List<JTextArea> elevatorStatusAreas = new ArrayList<>();
    private List<JTextArea> statsAreas = new ArrayList<>();

    // контейнеры панелей
    private JPanel statusPanel;
    private JPanel statsPanel;

    // таймеры
    private Timer statusTimer;
    private Timer statsTimer;

    private int generationInterval = 2000;

    public ElevatorFrame() {
        setTitle("Elevator Simulation System");
        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // обработчик закрытия окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAllThreads();
            }
        });

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        util.GuiLogger.init(logArea);

        // инициализация компонентов
        floorsField = new JTextField(4);
        elevatorsField = new JTextField(4);
        startButton = new JButton("Запуск системы");
        stopButton = new JButton("Остановить");
        stopButton.setEnabled(false);


        floorsField.setText("10");
        elevatorsField.setText("3");

        // панель настройки
        JPanel setupPanel = new JPanel();
        setupPanel.setBorder(BorderFactory.createTitledBorder("Настройка системы"));
        intervalField = new JTextField(5);
        intervalField.setText("2000");
        autoButton = new JButton("Автогенерация ВКЛ");
        autoButton.setEnabled(false);

        setupPanel.add(new JLabel("Этажей (N):"));
        setupPanel.add(floorsField);
        setupPanel.add(new JLabel("Лифтов (M):"));
        setupPanel.add(elevatorsField);
        setupPanel.add(startButton);
        setupPanel.add(stopButton);
        setupPanel.add(new JLabel("Интервал (мс):"));
        setupPanel.add(intervalField);
        setupPanel.add(autoButton);

        // панель ручного вызова лифта
        JPanel callPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        callPanel.setBorder(BorderFactory.createTitledBorder("Ручной вызов лифта"));

        JPanel floorPanel = new JPanel();
        fromField = new JTextField(4);
        toField = new JTextField(4);
        callButton = new JButton("Вызвать лифт");
        callButton.setEnabled(false);

        floorPanel.add(new JLabel("Вы на этаже:"));
        floorPanel.add(fromField);
        floorPanel.add(new JLabel("Целевой этаж:"));
        floorPanel.add(toField);
        floorPanel.add(callButton);

        // панель выбора направления
        JPanel directionPanel = new JPanel();
        ButtonGroup directionGroup = new ButtonGroup();
        upButton = new JRadioButton("Вверх");
        downButton = new JRadioButton("Вниз");
        upButton.setSelected(true);

        directionGroup.add(upButton);
        directionGroup.add(downButton);
        directionPanel.add(new JLabel("Направление:"));
        directionPanel.add(upButton);
        directionPanel.add(downButton);

        callPanel.add(floorPanel);
        callPanel.add(directionPanel);

        // инициализация динамических панелей
        statusPanel = new JPanel();
        statusPanel.setBorder(BorderFactory.createTitledBorder("Статус лифтов"));

        statsPanel = new JPanel();
        statsPanel.setBorder(BorderFactory.createTitledBorder("Статистика в реальном времени"));


        createElevatorPanels(1);


        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        centerPanel.add(statsPanel, BorderLayout.SOUTH);

        add(setupPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(statusPanel, BorderLayout.CENTER);
        rightPanel.add(callPanel, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        // обработчик запуска системы
        startButton.addActionListener(e -> {
            try {
                int floors = Integer.parseInt(floorsField.getText());
                int elevatorsCount = Integer.parseInt(elevatorsField.getText());

                if (floors < 2 || floors > 50) {
                    JOptionPane.showMessageDialog(this,
                            "Количество этажей должно быть от 2 до 50",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (elevatorsCount < 1 || elevatorsCount > 8) {
                    JOptionPane.showMessageDialog(this,
                            "Количество лифтов должно быть от 1 до 8",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                maxFloors = floors;
                elevatorsList.clear();
                elevatorThreads.clear();

                // останавливаем старые таймеры
                stopTimers();

                // создаем панели для указанного количества лифтов
                createElevatorPanels(elevatorsCount);

                // запуск лифтов
                for (int i = 0; i < elevatorsCount; i++) {
                    Elevator elevator = new Elevator(i + 1, 1, floors);
                    elevatorsList.add(elevator);
                    Thread elevatorThread = new Thread(elevator, "Elevator-" + (i + 1));
                    elevatorThreads.add(elevatorThread);
                    elevatorThread.start();
                }

                // запуск диспетчера и генератора
                dispatcher = new Dispatcher(elevatorsList, floors);
                passengerGenerator = new PassengerGenerator(dispatcher, floors);
                dispatcherThread = new Thread(dispatcher, "Dispatcher");
                dispatcherThread.start();

                // активация элементов управления
                callButton.setEnabled(true);
                autoButton.setEnabled(true);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                floorsField.setEnabled(false);
                elevatorsField.setEnabled(false);

                // запуск таймеров обновления
                startStatusTimer();
                startStatisticsTimer();

                GuiLogger.log("СИСТЕМА УПРАВЛЕНИЯ ЛИФТАМИ ЗАПУЩЕНА");
                GuiLogger.log("Дом: " + floors + " этажей");
                GuiLogger.log("Лифтов: " + elevatorsCount);


            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Введите числовые значения",
                        "Ошибка ввода",
                        JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка запуска системы: " + ex.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        // обработчик автогенерации запросов
        autoButton.addActionListener(e -> {
            if (passengerGenerator == null) {
                return;
            }

            if (!passengerGenerator.isRunning()) {
                try {
                    int interval = Integer.parseInt(intervalField.getText());
                    if (interval < 300) {
                        interval = 300;
                        intervalField.setText("300");
                        JOptionPane.showMessageDialog(this,
                                "Минимальный интервал установлен в 300мс",
                                "Информация",
                                JOptionPane.INFORMATION_MESSAGE);
                    }

                    if (interval > 10000) {
                        interval = 10000;
                        intervalField.setText("10000");
                        JOptionPane.showMessageDialog(this,
                                "Максимальный интервал установлен в 10000мс",
                                "Информация",
                                JOptionPane.INFORMATION_MESSAGE);
                    }

                    passengerGenerator.start(interval);
                    autoButton.setText("Автогенерация ВЫКЛ");
                    autoButton.setBackground(new Color(255, 200, 200));
                    intervalField.setEnabled(false);
                    GuiLogger.log(" АВТОГЕНЕРАЦИЯ ЗАПУЩЕНА ");
                    GuiLogger.log("Интервал генерации: " + interval + " мс");

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Введите числовое значение интервала (300-10000 мс)",
                            "Ошибка ввода",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                passengerGenerator.stop();
                autoButton.setText("Автогенерация ВКЛ");
                autoButton.setBackground(null);
                intervalField.setEnabled(true);
                GuiLogger.log(" АВТОГЕНЕРАЦИЯ ОСТАНОВЛЕНА ");
            }
        });

        // обработчик остановки системы
        stopButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Вы уверены, что хотите остановить систему?",
                    "Подтверждение остановки",
                    JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                stopAllThreads();
                resetUI();
                GuiLogger.log(" СИСТЕМА ОСТАНОВЛЕНА ");
            }
        });

        // обработчик ручного вызова лифта
        callButton.addActionListener(e -> {
            try {
                int from = Integer.parseInt(fromField.getText());
                int to = Integer.parseInt(toField.getText());

                // проверка диапазонов
                if (maxFloors > 0 && (from < 1 || from > maxFloors || to < 1 || to > maxFloors)) {
                    JOptionPane.showMessageDialog(this,
                            "Этажи должны быть в диапазоне от 1 до " + maxFloors,
                            "Ошибка диапазона",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // нельзя вызвать лифт на тот же этаж
                if (from == to) {
                    JOptionPane.showMessageDialog(this,
                            "Вы уже находитесь на целевом этаже\n" +
                                    "Введите другой номер целевого этажа",
                            "Информация",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }


                Direction requestedDirection = (to > from) ? Direction.UP : Direction.DOWN;

                // проверка согласованности с выбранным направлением
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

                        if (requestedDirection == Direction.UP) {
                            upButton.setSelected(true);
                        } else {
                            downButton.setSelected(true);
                        }
                    } else {
                        requestedDirection = selectedDirection;
                    }
                }

                if (dispatcher != null) {
                    dispatcher.submitRequest(userCounter++, from, requestedDirection, to);

                    fromField.setText("");
                    toField.setText("");

                    GuiLogger.log("РУЧНОЙ ВЫЗОВ: Пользователь " + (userCounter-1) +
                            " вызывает лифт с этажа " + from + " на этаж " + to);
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Некорректный ввод этажей\n" +
                                "Введите числовые значения",
                        "Ошибка ввода",
                        JOptionPane.ERROR_MESSAGE);
            }
        });



        setLocationRelativeTo(null);
    }

    private JTextArea createStatusArea(String title) {
        JTextArea area = new JTextArea(5, 20);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setText(title + ":\nОжидание запуска...");
        area.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return area;
    }

    private JTextArea createStatsArea(String title) {
        JTextArea area = new JTextArea(6, 20);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setText(title + ":\nОжидание запуска...");
        area.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return area;
    }

    private void createElevatorPanels(int elevatorCount) {

        elevatorStatusAreas.clear();
        statsAreas.clear();
        statusPanel.removeAll();
        statsPanel.removeAll();


        if (elevatorCount > 0) {
            statusPanel.setLayout(new GridLayout(elevatorCount, 1, 5, 5));
            statsPanel.setLayout(new GridLayout(1, elevatorCount, 5, 5));
        } else {
            statusPanel.setLayout(new GridLayout(1, 1));
            statsPanel.setLayout(new GridLayout(1, 1));
        }


        for (int i = 0; i < elevatorCount; i++) {
            String elevatorName = "Лифт " + (i + 1);
            JTextArea statusArea = createStatusArea(elevatorName);
            JTextArea statsArea = createStatsArea(elevatorName);

            elevatorStatusAreas.add(statusArea);
            statsAreas.add(statsArea);

            statusPanel.add(new JScrollPane(statusArea));
            statsPanel.add(new JScrollPane(statsArea));
        }


        if (elevatorCount == 0) {
            JTextArea emptyStatus = createStatusArea("Нет лифтов");
            JTextArea emptyStats = createStatsArea("Нет лифтов");
            statusPanel.add(new JScrollPane(emptyStatus));
            statsPanel.add(new JScrollPane(emptyStats));
        }


        statusPanel.revalidate();
        statusPanel.repaint();
        statsPanel.revalidate();
        statsPanel.repaint();
    }

    private void startStatusTimer() {
        if (statusTimer != null) {
            statusTimer.stop();
        }

        statusTimer = new Timer(500, e -> {
            SwingUtilities.invokeLater(() -> {
                if (elevatorsList != null && !elevatorsList.isEmpty()) {
                    try {
                        for (int i = 0; i < elevatorsList.size(); i++) {
                            if (i < elevatorStatusAreas.size()) {
                                elevatorStatusAreas.get(i).setText(
                                        elevatorsList.get(i).getStatusDisplay()
                                );
                            }
                        }
                    } catch (Exception ex) {

                    }
                }
            });
        });
        statusTimer.setInitialDelay(0);
        statusTimer.start();
    }

    private void startStatisticsTimer() {
        if (statsTimer != null) {
            statsTimer.stop();
        }

        statsTimer = new Timer(1000, e -> {
            SwingUtilities.invokeLater(() -> {
                if (elevatorsList != null && !elevatorsList.isEmpty()) {
                    try {
                        for (int i = 0; i < elevatorsList.size(); i++) {
                            if (i < statsAreas.size()) {
                                Elevator elevator = elevatorsList.get(i);
                                statsAreas.get(i).setText(String.format(
                                        "Лифт %d:\n" +
                                                "Пассажиров: %d\n" +
                                                "Остановок: %d\n" +
                                                "Целей: %d\n" +
                                                "Этаж: %d\n" +
                                                "Направление: %s\n" +
                                                "Статус: %s",
                                        elevator.getId(),
                                        elevator.getPassengersServed(),
                                        elevator.getTotalStops(),
                                        elevator.getTargetsCount(),
                                        elevator.getCurrentFloor(),
                                        elevator.getDirection(),
                                        elevator.getStatus()
                                ));
                            }
                        }
                    } catch (Exception ex) {

                    }
                }
            });
        });
        statsTimer.setInitialDelay(0);
        statsTimer.start();
    }

    private void stopTimers() {
        if (statusTimer != null) {
            statusTimer.stop();
            statusTimer = null;
        }

        if (statsTimer != null) {
            statsTimer.stop();
            statsTimer = null;
        }
    }


    private void resetUI() {
        callButton.setEnabled(false);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        autoButton.setEnabled(false);
        autoButton.setText("Автогенерация ВКЛ");
        autoButton.setBackground(null);
        floorsField.setEnabled(true);
        elevatorsField.setEnabled(true);
        intervalField.setEnabled(true);

        // показываем финальную статистику в панелях статуса
        if (elevatorsList != null && !elevatorsList.isEmpty()) {
            for (int i = 0; i < elevatorsList.size(); i++) {
                if (i < elevatorStatusAreas.size()) {
                    Elevator elevator = elevatorsList.get(i);
                    elevatorStatusAreas.get(i).setText(String.format(
                            "Лифт %d (завершен)\n" +
                                    "Пассажиров: %d\n" +
                                    "Остановок: %d\n" +
                                    "Этаж: %d\n" +
                                    "Статус: ОСТАНОВЛЕН",
                            elevator.getId(),
                            elevator.getPassengersServed(),
                            elevator.getTotalStops(),
                            elevator.getCurrentFloor()
                    ));
                }

                if (i < statsAreas.size()) {
                    Elevator elevator = elevatorsList.get(i);
                    statsAreas.get(i).setText(String.format(
                            "Лифт %d (завершен):\n" +
                                    "Пассажиров: %d\n" +
                                    "Остановок: %d\n" +
                                    "Целей: 0\n" +
                                    "Этаж: %d\n" +
                                    "Направление: IDLE\n" +
                                    "Статус: ОСТАНОВЛЕН",
                            elevator.getId(),
                            elevator.getPassengersServed(),
                            elevator.getTotalStops(),
                            elevator.getCurrentFloor()
                    ));
                }
            }
        } else {
            // сброс статусов если лифты не были запущены
            for (JTextArea area : elevatorStatusAreas) {
                area.setText("Ожидание запуска...");
            }
            for (JTextArea area : statsAreas) {
                area.setText("Ожидание запуска...");
            }
        }
    }

    private void stopAllThreads() {

        stopTimers();


        if (passengerGenerator != null && passengerGenerator.isRunning()) {
            passengerGenerator.stop();
        }


        if (dispatcherThread != null && dispatcherThread.isAlive()) {
            dispatcherThread.interrupt();
        }


        for (Thread thread : elevatorThreads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }


        try {
            if (dispatcherThread != null) {
                dispatcherThread.join(1500);
            }

            for (Thread thread : elevatorThreads) {
                if (thread != null) {
                    thread.join(1500);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GuiLogger.log("Прервано ожидание завершения потоков");
        }

        elevatorThreads.clear();
        dispatcher = null;
        passengerGenerator = null;
        dispatcherThread = null;
    }
}