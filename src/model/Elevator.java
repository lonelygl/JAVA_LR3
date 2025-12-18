package model;

import util.GuiLogger;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class Elevator implements Runnable {

    private final int id;
    private int currentFloor;
    private final int maxFloor;

    private Direction direction = Direction.IDLE;
    private ElevatorStatus status = ElevatorStatus.STOPPED;

    //разделение целей по направлениям для эффективной маршрутизации
    private final Set<Integer> internalTargets = new HashSet<>();
    private final Set<Integer> externalUpTargets = new HashSet<>();
    private final Set<Integer> externalDownTargets = new HashSet<>();

    private final BlockingQueue<Integer> executionQueue = new LinkedBlockingQueue<>();

    // общая статистика
    private int passengersServed = 0;
    private int totalStops = 0;

    public Elevator(int id, int startFloor, int maxFloor) {
        this.id = id;
        this.currentFloor = startFloor;
        this.maxFloor = maxFloor;
    }

    public int getId() {
        return id;
    }

    public synchronized int getCurrentFloor() {
        return currentFloor;
    }

    public synchronized Direction getDirection() {
        return direction;
    }

    public synchronized ElevatorStatus getStatus() {
        return status;
    }

    public synchronized int getTargetsCount() {
        return internalTargets.size() + externalUpTargets.size() + externalDownTargets.size();
    }

    public synchronized int getPassengersServed() {
        return passengersServed;
    }

    public synchronized int getTotalStops() {
        return totalStops;
    }

    /* логика доступности лифта: доступен только если стоит и двери закрыты */
    public synchronized boolean isAvailable() {
        return status == ElevatorStatus.STOPPED &&
                internalTargets.isEmpty() &&
                externalUpTargets.isEmpty() &&
                externalDownTargets.isEmpty();
    }

    /*проверка направления */
    public synchronized boolean isMovingToward(int floor, Direction requestedDirection) {
        if (direction == Direction.IDLE || requestedDirection != direction) {
            return false;
        }

        if (direction == Direction.UP) {
            return floor >= currentFloor && (externalUpTargets.contains(floor) || isOnTheWayUp(floor));
        } else {
            return floor <= currentFloor && (externalDownTargets.contains(floor) || isOnTheWayDown(floor));
        }
    }

    private boolean isOnTheWayUp(int floor) {
        return internalTargets.stream().anyMatch(f -> f >= currentFloor && f <= floor) ||
                externalUpTargets.stream().anyMatch(f -> f >= currentFloor && f <= floor);
    }

    private boolean isOnTheWayDown(int floor) {
        return internalTargets.stream().anyMatch(f -> f <= currentFloor && f >= floor) ||
                externalDownTargets.stream().anyMatch(f -> f <= currentFloor && f >= floor);
    }


    public synchronized int getFurthestTargetInCurrentDirection() {
        if (direction == Direction.UP) {
            Optional<Integer> maxInternal = internalTargets.stream().max(Integer::compareTo);
            Optional<Integer> maxExternal = externalUpTargets.stream().max(Integer::compareTo);

            int max = Math.max(
                    maxInternal.orElse(currentFloor),
                    maxExternal.orElse(currentFloor)
            );
            return Math.max(max, currentFloor);

        } else if (direction == Direction.DOWN) {
            Optional<Integer> minInternal = internalTargets.stream().min(Integer::compareTo);
            Optional<Integer> minExternal = externalDownTargets.stream().min(Integer::compareTo);

            int min = Math.min(
                    minInternal.orElse(currentFloor),
                    minExternal.orElse(currentFloor)
            );
            return Math.min(min, currentFloor);
        }
        return currentFloor;
    }

    /* получение всех целей для отображения статуса */
    public synchronized List<Integer> getAllTargets() {
        List<Integer> all = new ArrayList<>(internalTargets);
        all.addAll(externalUpTargets);
        all.addAll(externalDownTargets);
        return all;
    }

    /* добавление цели из кабины  */
    public void addTarget(int floor) {
        synchronized (this) {
            if (floor < 1 || floor > maxFloor) {
                GuiLogger.log("Лифт " + id + ": игнорирую некорректный этаж " + floor);
                return;
            }

            if (floor == currentFloor) {
                GuiLogger.log("Лифт " + id + ": уже на этаже " + floor);
                return;
            }

            if (!internalTargets.contains(floor)) {
                internalTargets.add(floor);
                GuiLogger.log("Лифт " + id + ": добавлена внутренняя цель - этаж " + floor);
                rebuildExecutionQueue();
            }
        }
    }

    /* добавление внешней цели для лифта (вызов с этажа) с учетом направления */
    public void addExternalTarget(int floor, Direction requestedDirection) {
        synchronized (this) {
            if (floor < 1 || floor > maxFloor) {
                GuiLogger.log("Лифт " + id + ": игнорирую некорректный вызов на этаж " + floor);
                return;
            }

            if (floor == currentFloor && status == ElevatorStatus.STOPPED) {
                GuiLogger.log("Лифт " + id + ": уже на месте вызова " + floor);
                return;
            }

            // принимаем вызов только если он вписывается в маршрут
            boolean shouldAccept = false;

            if (direction == Direction.IDLE) {
                shouldAccept = true; // свободный лифт принимает любой вызов
            }
            else if (direction == Direction.UP && requestedDirection == Direction.UP && floor >= currentFloor) {
                shouldAccept = true; //  вверх и вызов сверху по пути
            }
            else if (direction == Direction.DOWN && requestedDirection == Direction.DOWN && floor <= currentFloor) {
                shouldAccept = true; //  вниз и вызов снизу по пути
            }
            // если лифт едет в противоположном направлении, но уже близко к развороту
            else if (isAboutToChangeDirection() && Math.abs(currentFloor - floor) <= 2) {
                shouldAccept = true; // принимаем близкие вызовы перед разворотом
            }

            if (shouldAccept) {
                if (requestedDirection == Direction.UP) {
                    if (!externalUpTargets.contains(floor)) {
                        externalUpTargets.add(floor);
                        GuiLogger.log("Лифт " + id + ": принят вызов ВВЕРХ с этажа " + floor);
                    }
                } else {
                    if (!externalDownTargets.contains(floor)) {
                        externalDownTargets.add(floor);
                        GuiLogger.log("Лифт " + id + ": принят вызов ВНИЗ с этажа " + floor);
                    }
                }
                rebuildExecutionQueue();
            } else {
                GuiLogger.log("Лифт " + id + ": отклонен вызов с этажа " + floor +
                        " (направление: " + requestedDirection +
                        ", текущее направление: " + direction + ")");
            }
        }
    }


    private boolean isAboutToChangeDirection() {
        if (direction == Direction.UP) {
            return internalTargets.stream().noneMatch(f -> f > currentFloor) &&
                    externalUpTargets.stream().noneMatch(f -> f > currentFloor);
        } else if (direction == Direction.DOWN) {
            return internalTargets.stream().noneMatch(f -> f < currentFloor) &&
                    externalDownTargets.stream().noneMatch(f -> f < currentFloor);
        }
        return false;
    }


    private void rebuildExecutionQueue() {
        executionQueue.clear();


        List<Integer> targetsInCurrentDirection = new ArrayList<>();
        List<Integer> targetsInOppositeDirection = new ArrayList<>();

        if (direction == Direction.UP || direction == Direction.IDLE) {
            targetsInCurrentDirection.addAll(
                    internalTargets.stream()
                            .filter(f -> f > currentFloor)
                            .sorted()
                            .collect(Collectors.toList())
            );
            targetsInCurrentDirection.addAll(
                    externalUpTargets.stream()
                            .filter(f -> f > currentFloor)
                            .sorted()
                            .collect(Collectors.toList())
            );

            targetsInOppositeDirection.addAll(
                    internalTargets.stream()
                            .filter(f -> f < currentFloor)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList())
            );
            targetsInOppositeDirection.addAll(
                    externalDownTargets.stream()
                            .filter(f -> f < currentFloor)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList())
            );

        } else if (direction == Direction.DOWN) {
            targetsInCurrentDirection.addAll(
                    internalTargets.stream()
                            .filter(f -> f < currentFloor)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList())
            );
            targetsInCurrentDirection.addAll(
                    externalDownTargets.stream()
                            .filter(f -> f < currentFloor)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList())
            );

            targetsInOppositeDirection.addAll(
                    internalTargets.stream()
                            .filter(f -> f > currentFloor)
                            .sorted()
                            .collect(Collectors.toList())
            );
            targetsInOppositeDirection.addAll(
                    externalUpTargets.stream()
                            .filter(f -> f > currentFloor)
                            .sorted()
                            .collect(Collectors.toList())
            );
        }


        if (!targetsInCurrentDirection.isEmpty()) {
            if (direction == Direction.IDLE) {
                direction = targetsInCurrentDirection.get(0) > currentFloor ? Direction.UP : Direction.DOWN;
            }
            targetsInCurrentDirection.forEach(executionQueue::offer);
            targetsInOppositeDirection.forEach(executionQueue::offer);
        }

        else if (!targetsInOppositeDirection.isEmpty()) {
            direction = targetsInOppositeDirection.get(0) > currentFloor ? Direction.UP : Direction.DOWN;
            targetsInOppositeDirection.forEach(executionQueue::offer);
        }

        else {
            direction = Direction.IDLE;
        }


        if (!executionQueue.isEmpty()) {
            GuiLogger.log("Лифт " + id + ": новый маршрут: " +
                    executionQueue.stream().map(String::valueOf).collect(Collectors.joining(" → ")));
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Integer target = executionQueue.peek();

                if (target != null) {
                    moveTo(target);
                } else {
                    // Нет целей - ждем
                    synchronized (this) {
                        direction = Direction.IDLE;
                        status = ElevatorStatus.STOPPED;
                    }
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GuiLogger.log("Лифт " + id + " остановлен");
        } catch (Exception e) {
            GuiLogger.log("Лифт " + id + " ошибка: " + e.getMessage());
            e.printStackTrace();
        } finally {
            logStatistics();
            GuiLogger.log("Лифт " + id + " завершил работу");
        }
    }

    private void moveTo(int target) throws InterruptedException {
        synchronized (this) {
            direction = target > currentFloor ? Direction.UP : Direction.DOWN;
            status = ElevatorStatus.MOVING;
        }

        GuiLogger.log("Лифт " + id + ": движение с этажа " +
                currentFloor + " на этаж " + target +
                " (направление: " + direction + ")");

        // движение между этажами
        while (currentFloor != target && !Thread.currentThread().isInterrupted()) {
            Thread.sleep(700); // скорость движения

            synchronized (this) {
                currentFloor += (direction == Direction.UP ? 1 : -1);

                // проверка, нужно ли остановиться на текущем этаже
                if (shouldStopAtCurrentFloor()) {
                    handleStop();
                }
            }
        }

        if (!Thread.currentThread().isInterrupted() && currentFloor == target) {
            GuiLogger.log("Лифт " + id + " прибыл на целевой этаж " + currentFloor);
            handleStop();
        }
    }

    private boolean shouldStopAtCurrentFloor() {
        return internalTargets.contains(currentFloor) ||
                externalUpTargets.contains(currentFloor) ||
                externalDownTargets.contains(currentFloor);
    }

    private void handleStop() throws InterruptedException {
        totalStops++;


        boolean isInternalStop = internalTargets.contains(currentFloor);
        boolean isExternalUpStop = externalUpTargets.contains(currentFloor);
        boolean isExternalDownStop = externalDownTargets.contains(currentFloor);


        openDoors();

        // обработка остановки
        if (isInternalStop) {
            passengersServed++;
            GuiLogger.log("Лифт " + id + ": пассажир вышел на этаже " + currentFloor);
        }

        if (isExternalUpStop || isExternalDownStop) {
            GuiLogger.log("Лифт " + id + ": пассажир вошел на этаже " + currentFloor);
        }


        synchronized (this) {
            internalTargets.remove(currentFloor);
            externalUpTargets.remove(currentFloor);
            externalDownTargets.remove(currentFloor);


            rebuildExecutionQueue();
        }


        closeDoors();
    }

    private void openDoors() throws InterruptedException {
        synchronized (this) {
            status = ElevatorStatus.DOORS_OPEN;
        }

        GuiLogger.log("Лифт " + id + " двери открыты на этаже " + currentFloor);
        Thread.sleep(1000);
    }

    private void closeDoors() {
        GuiLogger.log("Лифт " + id + " двери закрыты");

        synchronized (this) {
            if (executionQueue.isEmpty()) {
                status = ElevatorStatus.STOPPED;
                direction = Direction.IDLE;
            } else {
                status = ElevatorStatus.MOVING;
            }
        }
    }

    private void logStatistics() {
        GuiLogger.log("Статистика лифта " + id);
        GuiLogger.log("Обслужено пассажиров: " + passengersServed);
        GuiLogger.log("Всего остановок: " + totalStops);

    }

    /* отображение статуса в GUI */
    public synchronized String getStatusDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("Лифт #").append(id).append("\n");
        sb.append("Этаж: ").append(currentFloor).append("\n");
        sb.append("Статус: ").append(status).append("\n");
        sb.append("Направление: ").append(direction).append("\n");
        sb.append("Цели: ");

        List<Integer> allTargets = getAllTargets();
        if (allTargets.isEmpty()) {
            sb.append("нет");
        } else {
            sb.append(allTargets.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }
        sb.append("\n");
        sb.append("Пассажиров: ").append(passengersServed);

        return sb.toString();
    }
}