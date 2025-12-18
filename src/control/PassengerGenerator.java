package control;

import model.Direction;
import util.GuiLogger;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PassengerGenerator {
    private final Dispatcher dispatcher;
    private final int maxFloors;
    private final Random random = new Random();
    private int nextUserId = 1;
    private ScheduledExecutorService scheduler;
    private volatile boolean isRunning = false;

    public PassengerGenerator(Dispatcher dispatcher, int maxFloors) {
        this.dispatcher = dispatcher;
        this.maxFloors = maxFloors;
    }

    public void start(int intervalMs) {
        if (isRunning) {
            return;
        }

        isRunning = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning) {
                return;
            }

            try {
                generateRandomRequest();
            } catch (Exception e) {
                GuiLogger.log("Ошибка генератора: " + e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        GuiLogger.log("Генератор запросов запущен (интервал: " + intervalMs + "мс)");
    }

    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        GuiLogger.log("Генератор запросов остановлен");
    }

    private void generateRandomRequest() {
        // Генерация случайного этажа вызова
        int fromFloor = random.nextInt(maxFloors) + 1;

        // Генерация целевого этажа
        int toFloor;
        do {
            toFloor = random.nextInt(maxFloors) + 1;
        } while (toFloor == fromFloor);

        // Определение направления
        Direction requestedDirection = (toFloor > fromFloor) ? Direction.UP : Direction.DOWN;

        // Создание запроса
        int userId = nextUserId++;

        GuiLogger.log("Пользователь " + userId +
                " вызывает лифт с этажа " + fromFloor +
                " (направление: " + requestedDirection + ")" +
                " на этаж " + toFloor);

        // Отправка запроса диспетчеру
        dispatcher.submitRequest(userId, fromFloor, requestedDirection, toFloor);
    }

    public boolean isRunning() {
        return isRunning;
    }
}