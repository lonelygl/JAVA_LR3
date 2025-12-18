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

    // GUI
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

    // панели статуса лифтов
    private JTextArea elevator1StatusArea;
    private JTextArea elevator2StatusArea;
    private JTextArea elevator3StatusArea;

    // панели статистики
    private JTextArea stats1Area;
    private JTextArea stats2Area;
    private JTextArea stats3Area;

    // таймеры
    private Timer statusTimer;
    private Timer statsTimer;

    private int generationInterval = 2000;

    public ElevatorFrame() {
        setTitle("Elevator Simulation System");
        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAllThreads();
            }
        });

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        util.GuiLogger.init(logArea);

        // инициализация компонентов до их использования
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

        // панель статуса лифтов
        JPanel statusPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Статус лифтов"));

        elevator1StatusArea = createStatusArea("Лифт 1");
        elevator2StatusArea = createStatusArea("Лифт 2");
        elevator3StatusArea = createStatusArea("Лифт 3");

        statusPanel.add(new JScrollPane(elevator1StatusArea));
        statusPanel.add(new JScrollPane(elevator2StatusArea));
        statusPanel.add(new JScrollPane(elevator3StatusArea));

        // панель статистики
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Статистика в реальном времени"));

        stats1Area = createStatsArea("Лифт 1");
        stats2Area = createStatsArea("Лифт 2");
        stats3Area = createStatsArea("Лифт 3");

        statsPanel.add(new JScrollPane(stats1Area));
        statsPanel.add(new JScrollPane(stats2Area));
        statsPanel.add(new JScrollPane(stats3Area));


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
                    GuiLogger.log("АВТОГЕНЕРАЦИЯ ЗАПУЩЕНА");
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
                GuiLogger.log("АВТОГЕНЕРАЦИЯ ОСТАНОВЛЕНА");
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
                GuiLogger.log("СИСТЕМА ОСТАНОВЛЕНА");
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

                // определение направления вызова
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
                        // автоматически переключаем радиокнопку
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

        // добавляем подсказки
        addToolTips();

        // центрируем окно
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

    private void startStatusTimer() {
        if (statusTimer != null) {
            statusTimer.stop();
        }

        statusTimer = new Timer(500, e -> {
            SwingUtilities.invokeLater(() -> {
                if (elevatorsList != null && !elevatorsList.isEmpty()) {
                    try {
                        if (elevatorsList.size() > 0) {
                            elevator1StatusArea.setText(elevatorsList.get(0).getStatusDisplay());
                        }
                        if (elevatorsList.size() > 1) {
                            elevator2StatusArea.setText(elevatorsList.get(1).getStatusDisplay());
                        }
                        if (elevatorsList.size() > 2) {
                            elevator3StatusArea.setText(elevatorsList.get(2).getStatusDisplay());
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

        statsTimer = new Timer(1000, e -> { // Обновлять каждую секунду
            SwingUtilities.invokeLater(() -> {
                if (elevatorsList != null && !elevatorsList.isEmpty()) {
                    try {
                        // Лифт 1
                        if (elevatorsList.size() > 0) {
                            Elevator e1 = elevatorsList.get(0);
                            stats1Area.setText(String.format(
                                    "Лифт 1:\n" +
                                            "Пассажиров: %d\n" +
                                            "Остановок: %d\n" +
                                            "Целей: %d\n" +
                                            "Этаж: %d\n" +
                                            "Направление: %s\n" +
                                            "Статус: %s",
                                    e1.getPassengersServed(),
                                    e1.getTotalStops(),
                                    e1.getTargetsCount(),
                                    e1.getCurrentFloor(),
                                    e1.getDirection(),
                                    e1.getStatus()
                            ));
                        }

                        // Лифт 2
                        if (elevatorsList.size() > 1) {
                            Elevator e2 = elevatorsList.get(1);
                            stats2Area.setText(String.format(
                                    "Лифт 2:\n" +
                                            "Пассажиров: %d\n" +
                                            "Остановок: %d\n" +
                                            "Целей: %d\n" +
                                            "Этаж: %d\n" +
                                            "Направление: %s\n" +
                                            "Статус: %s",
                                    e2.getPassengersServed(),
                                    e2.getTotalStops(),
                                    e2.getTargetsCount(),
                                    e2.getCurrentFloor(),
                                    e2.getDirection(),
                                    e2.getStatus()
                            ));
                        }

                        // Лифт 3
                        if (elevatorsList.size() > 2) {
                            Elevator e3 = elevatorsList.get(2);
                            stats3Area.setText(String.format(
                                    "Лифт 3:\n" +
                                            "Пассажиров: %d\n" +
                                            "Остановок: %d\n" +
                                            "Целей: %d\n" +
                                            "Этаж: %d\n" +
                                            "Направление: %s\n" +
                                            "Статус: %s",
                                    e3.getPassengersServed(),
                                    e3.getTotalStops(),
                                    e3.getTargetsCount(),
                                    e3.getCurrentFloor(),
                                    e3.getDirection(),
                                    e3.getStatus()
                            ));
                        }
                    } catch (Exception ex) {

                    }
                }
            });
        });
        statsTimer.setInitialDelay(0);
        statsTimer.start();
    }

    private void addToolTips() {
        floorsField.setToolTipText("Введите количество этажей в доме (2-50)");
        elevatorsField.setToolTipText("Введите количество лифтов (1-8)");
        intervalField.setToolTipText("Интервал генерации запросов в миллисекундах (300-10000)");
        fromField.setToolTipText("Этаж, на котором вы находитесь");
        toField.setToolTipText("Этаж, на который хотите поехать");
        upButton.setToolTipText("Вызов лифта для движения вверх");
        downButton.setToolTipText("Вызов лифта для движения вниз");
        autoButton.setToolTipText("Включить/выключить автоматическую генерацию запросов");
        callButton.setToolTipText("Вызвать лифт вручную");
        startButton.setToolTipText("Запустить систему управления лифтами");
        stopButton.setToolTipText("Остановить все лифты и диспетчер");
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

        // показать финальную статистику из логов
        if (elevatorsList != null && !elevatorsList.isEmpty()) {
            // Лифт 1 статистика
            if (elevatorsList.size() > 0) {
                stats1Area.setText(String.format(
                        "Лифт 1 (завершен):\n" +
                                "Пассажиров: %d\n" +
                                "Остановок: %d\n" +
                                "Целей: 0\n" +
                                "Этаж: %d\n" +
                                "Направление: IDLE\n" +
                                "Статус: ОСТАНОВЛЕН",
                        elevatorsList.get(0).getPassengersServed(),
                        elevatorsList.get(0).getTotalStops(),
                        elevatorsList.get(0).getCurrentFloor()
                ));
            }

            // Лифт 2 статистика
            if (elevatorsList.size() > 1) {
                stats2Area.setText(String.format(
                        "Лифт 2 (завершен):\n" +
                                "Пассажиров: %d\n" +
                                "Остановок: %d\n" +
                                "Целей: 0\n" +
                                "Этаж: %d\n" +
                                "Направление: IDLE\n" +
                                "Статус: ОСТАНОВЛЕН",
                        elevatorsList.get(1).getPassengersServed(),
                        elevatorsList.get(1).getTotalStops(),
                        elevatorsList.get(1).getCurrentFloor()
                ));
            }

            // Лифт 3 статистика
            if (elevatorsList.size() > 2) {
                stats3Area.setText(String.format(
                        "Лифт 3 (завершен):\n" +
                                "Пассажиров: %d\n" +
                                "Остановок: %d\n" +
                                "Целей: 0\n" +
                                "Этаж: %d\n" +
                                "Направление: IDLE\n" +
                                "Статус: ОСТАНОВЛЕН",
                        elevatorsList.get(2).getPassengersServed(),
                        elevatorsList.get(2).getTotalStops(),
                        elevatorsList.get(2).getCurrentFloor()
                ));
            }

            // обновить статусы
            elevator1StatusArea.setText("Лифт 1 завершил работу");
            elevator2StatusArea.setText("Лифт 2 завершил работу");
            elevator3StatusArea.setText("Лифт 3 завершил работу");
        } else {
            // сброс статусов если лифты не были запущены
            elevator1StatusArea.setText("Лифт 1:\nОжидание запуска...");
            elevator2StatusArea.setText("Лифт 2:\nОжидание запуска...");
            elevator3StatusArea.setText("Лифт 3:\nОжидание запуска...");

            stats1Area.setText("Лифт 1:\nОжидание запуска...");
            stats2Area.setText("Лифт 2:\nОжидание запуска...");
            stats3Area.setText("Лифт 3:\nОжидание запуска...");
        }
    }

    private void stopAllThreads() {
        // остановить таймеры
        if (statusTimer != null) {
            statusTimer.stop();
            statusTimer = null;
        }

        if (statsTimer != null) {
            statsTimer.stop();
            statsTimer = null;
        }

        // остановка генератора
        if (passengerGenerator != null && passengerGenerator.isRunning()) {
            passengerGenerator.stop();
        }

        // остановка диспетчера
        if (dispatcherThread != null && dispatcherThread.isAlive()) {
            dispatcherThread.interrupt();
        }

        // остановка всех лифтов
        for (Thread thread : elevatorThreads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }

        // ожидание завершения потоков
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
        elevatorsList.clear();


        dispatcher = null;
        passengerGenerator = null;
        dispatcherThread = null;
    }
}