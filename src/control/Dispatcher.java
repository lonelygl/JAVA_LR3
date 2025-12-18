package control;

import model.Elevator;
import model.Direction;
import util.GuiLogger;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Dispatcher implements Runnable {

    /*запросы пассажиров */
    private static class PassengerRequest {
        final int userId;
        final int fromFloor;
        final Direction requestedDirection;
        final int toFloor;

        PassengerRequest(int userId, int fromFloor, Direction requestedDirection, int toFloor) {
            this.userId = userId;
            this.fromFloor = fromFloor;
            this.requestedDirection = requestedDirection;
            this.toFloor = toFloor;
        }
    }

    private final BlockingQueue<PassengerRequest> requests = new LinkedBlockingQueue<>();
    private final List<Elevator> elevators;
    private final int maxFloors;

    // общая статистика
    private int totalRequestsProcessed = 0;
    private int requestsAssigned = 0;
    private int requestsRejected = 0;

    public Dispatcher(List<Elevator> elevators, int maxFloors) {
        this.elevators = elevators;
        this.maxFloors = maxFloors;
        GuiLogger.log("Диспетчер инициализирован для дома из " + maxFloors + " этажей");
    }


    public synchronized List<Elevator> getElevators() {
        return new ArrayList<>(elevators); // Возвращаем копию для безопасности
    }


    public synchronized String getStatistics() {
        return String.format(
                "Статистика диспетчера:\n" +
                        "Всего запросов: %d\n" +
                        "Назначено: %d\n" +
                        "Отклонено: %d\n" +
                        "Эффективность: %.1f%%",
                totalRequestsProcessed,
                requestsAssigned,
                requestsRejected,
                totalRequestsProcessed > 0 ? (requestsAssigned * 100.0 / totalRequestsProcessed) : 0.0
        );
    }


    public void submitRequest(int userId, int fromFloor, Direction requestedDirection, int toFloor) {
        synchronized (this) {
            totalRequestsProcessed++;
        }

        GuiLogger.log(
                "Поступил запрос: пользователь " + userId +
                        ", этаж " + fromFloor +
                        " (направление: " + requestedDirection + ")" +
                        " → цель " + toFloor
        );

        // проверка корректности запроса
        if (fromFloor < 1 || fromFloor > maxFloors || toFloor < 1 || toFloor > maxFloors) {
            GuiLogger.log("ОШИБКА: Некорректные этажи в запросе " + userId);
            synchronized (this) {
                requestsRejected++;
            }
            return;
        }

        if (fromFloor == toFloor) {
            GuiLogger.log("ОШИБКА: Этаж вызова совпадает с целевым этажом в запросе " + userId);
            synchronized (this) {
                requestsRejected++;
            }
            return;
        }

        requests.offer(new PassengerRequest(userId, fromFloor, requestedDirection, toFloor));
    }

    @Override
    public void run() {
        try {
            GuiLogger.log("Диспетчер начал работу");

            while (!Thread.currentThread().isInterrupted()) {
                PassengerRequest request = requests.take();

                // выбор оптимального лифта
                Elevator selectedElevator = selectOptimalElevator(request);

                if (selectedElevator == null) {
                    GuiLogger.log(
                            "Пользователь " + request.userId +
                                    ": ВСЕ ЛИФТЫ ЗАНЯТЫ, запрос поставлен в очередь"
                    );
                    synchronized (this) {
                        requestsRejected++;
                    }

                    requests.offer(request);
                    Thread.sleep(1000);
                } else {
                    synchronized (this) {
                        requestsAssigned++;
                    }

                    GuiLogger.log(
                            "Пользователь " + request.userId +
                                    ": назначен лифт " + selectedElevator.getId() +
                                    " (текущий этаж: " + selectedElevator.getCurrentFloor() +
                                    ", направление: " + selectedElevator.getDirection() + ")"
                    );


                    selectedElevator.addExternalTarget(request.fromFloor, request.requestedDirection);
                    selectedElevator.addTarget(request.toFloor);


                    logAssignmentDetails(selectedElevator, request);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GuiLogger.log("Диспетчер остановлен по запросу");
        } catch (Exception e) {
            GuiLogger.log("ОШИБКА диспетчера: " + e.getMessage());
            e.printStackTrace();
        } finally {
            GuiLogger.log("Диспетчер завершил работу");
            GuiLogger.log(getStatistics());
        }
    }

    /* логирование деталей назначения */
    private void logAssignmentDetails(Elevator elevator, PassengerRequest request) {
        int distance = Math.abs(elevator.getCurrentFloor() - request.fromFloor);
        int estimatedTime = distance * 700;

        GuiLogger.log(String.format(
                "Детали назначения (запрос %d):\n" +
                        "  Лифт: %d (этаж %d, %s)\n" +
                        "  Расстояние до пассажира: %d этажей\n" +
                        "  Примерное время ожидания: %.1f сек.",
                request.userId,
                elevator.getId(),
                elevator.getCurrentFloor(),
                elevator.getDirection(),
                distance,
                estimatedTime / 1000.0
        ));
    }

    /* выбор оптимального лифта */
    private Elevator selectOptimalElevator(PassengerRequest request) {
        Elevator bestElevator = null;
        double bestScore = Double.MAX_VALUE;

        for (Elevator elevator : elevators) {
            double score = calculateElevatorScore(elevator, request);

            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevator;
            }
        }


        if (bestScore > 1000) {
            return null;
        }

        return bestElevator;
    }

    /* расчет оценки оптимальности лифта для данного запроса */
    private double calculateElevatorScore(Elevator elevator, PassengerRequest request) {
        int currentFloor = elevator.getCurrentFloor();
        Direction currentDirection = elevator.getDirection();
        int passengerFloor = request.fromFloor;
        Direction passengerDirection = request.requestedDirection;

        double baseScore = 0.0;

        //расстояние до пассажира
        int distance = Math.abs(currentFloor - passengerFloor);
        baseScore = distance * 10.0;

        //количество текущих целей лифта
        int targetsCount = elevator.getTargetsCount();
        baseScore += targetsCount * 5.0;

        // анализ направления движения
        if (currentDirection == Direction.IDLE) {
            // свободный лифт
            baseScore += 0.0;

        } else if (currentDirection == passengerDirection) {
            // движется в нужном направлении

            if (currentDirection == Direction.UP && passengerFloor >= currentFloor) {
                // едет вверх и пассажир выше
                baseScore *= 0.3;

            } else if (currentDirection == Direction.DOWN && passengerFloor <= currentFloor) {
                //едет вниз и пассажир ниже
                baseScore *= 0.3;

            } else {
                // едет в нужном направлении, но пассажир не по пути
                baseScore *= 1.5;
            }

        } else {
            // движется в противоположном направлении

            // расчет времени до разворота
            int furthestTarget = elevator.getFurthestTargetInCurrentDirection();
            int distanceToTurnAround = Math.abs(furthestTarget - currentFloor)
                    + Math.abs(furthestTarget - passengerFloor);

            baseScore += distanceToTurnAround * 15.0;


            baseScore *= 2.0;
        }

        // учет этажа пассажира
        if (passengerFloor == 1 || passengerFloor == maxFloors) {
            baseScore *= 0.8;
        }



        return baseScore;
    }

    /* статистика по всем лифтам */
    public synchronized String getAllElevatorsStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("СТАТУС ВСЕХ ЛИФТОВ\n");

        for (Elevator elevator : elevators) {
            sb.append(String.format(
                    "Лифт %d: этаж %d, %s, цели: %d, пассажиров: %d\n",
                    elevator.getId(),
                    elevator.getCurrentFloor(),
                    elevator.getDirection(),
                    elevator.getTargetsCount(),
                    elevator.getPassengersServed()
            ));
        }


        return sb.toString();
    }

    /* проверка доступности лифтов */
    public synchronized boolean hasAvailableElevators() {
        for (Elevator elevator : elevators) {
            if (elevator.isAvailable()) {
                return true;
            }
        }
        return false;
    }


    public synchronized int getPendingRequestsCount() {
        return requests.size();
    }

    public synchronized String getDispatcherInfo() {
        return String.format(
                "Диспетчер:\n" +
                        "Очередь запросов: %d\n" +
                        "Активных лифтов: %d\n" +
                        "Доступных лифтов: %s",
                getPendingRequestsCount(),
                elevators.size(),
                hasAvailableElevators() ? "Да" : "Нет"
        );
    }
}