package control;
import model.Elevator;
import model.Direction;
import util.GuiLogger;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Dispatcher implements Runnable {

    /*класс запроса */
    private static class Request {
        final int userId;
        final int fromFloor;
        final Direction requestedDirection;
        final int toFloor;

        Request(int userId, int fromFloor, Direction requestedDirection, int toFloor) {
            this.userId = userId;
            this.fromFloor = fromFloor;
            this.requestedDirection = requestedDirection;
            this.toFloor = toFloor;
        }
    }

    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final List<Elevator> elevators;

    public Dispatcher(List<Elevator> elevators) {
        this.elevators = elevators;
    }

    /* Поступление запроса пользователя */
    public void submitRequest(int userId, int fromFloor, Direction requestedDirection, int toFloor) {
        GuiLogger.log(
                "Поступил запрос: пользователь " + userId +
                        ", этаж " + fromFloor +
                        " (направление: " + requestedDirection + ")" +
                        " → цель " + toFloor
        );
        requests.offer(new Request(userId, fromFloor, requestedDirection, toFloor));
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Request request = requests.take();

                Elevator elevator = chooseOptimalElevator(request.fromFloor, request.requestedDirection);

                if (elevator == null) {
                    GuiLogger.log(
                            "Пользователь " + request.userId +
                                    ": все лифты заняты"
                    );
                } else {
                    GuiLogger.log(
                            "Пользователь " + request.userId +
                                    ": назначен лифт " + elevator.getId() +
                                    " (текущий этаж: " + elevator.getCurrentFloor() + ")"
                    );


                    elevator.addExternalTarget(request.fromFloor, request.requestedDirection);
                    elevator.addTarget(request.toFloor);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GuiLogger.log("Диспетчер остановлен");
        } catch (Exception e) {
            GuiLogger.log("Ошибка диспетчера: " + e.getMessage());
        } finally {
            GuiLogger.log("Диспетчер завершил работу");
        }
    }

    /* Выбор оптимального лифта с учетом направления */
    private Elevator chooseOptimalElevator(int floor, Direction requestedDirection) {
        return elevators.stream()
                .filter(e -> isSuitable(e, floor, requestedDirection))
                .min(Comparator.comparingInt(
                        e -> calculateScore(e, floor)))
                .orElseGet(() -> elevators.stream()
                        .min(Comparator.comparingInt(
                                e -> Math.abs(e.getCurrentFloor() - floor)))
                        .orElse(null));
    }


    private boolean isSuitable(Elevator elevator, int floor, Direction requestedDirection) {
        if (!elevator.isAvailable() && !elevator.isMovingToward(floor, requestedDirection)) {
            return false;
        }

        // Лифт должен двигаться в нужном направлении или быть свободным
        Direction elevatorDirection = elevator.getDirection();
        return elevatorDirection == Direction.IDLE ||
                elevatorDirection == requestedDirection;
    }

    /* оценка приоритета лифта */
    private int calculateScore(Elevator elevator, int floor) {
        int distance = Math.abs(elevator.getCurrentFloor() - floor);
        int stops = elevator.getTargetsCount();


        return distance * 10 + stops * 5;
    }
}