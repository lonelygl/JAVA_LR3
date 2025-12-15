package model;

import util.GuiLogger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashSet;
import java.util.Set;

public class Elevator implements Runnable {

    private final int id;
    private int currentFloor;
    private final int maxFloor;

    private Direction direction = Direction.IDLE;
    private ElevatorStatus status = ElevatorStatus.STOPPED;
    private final Set<Integer> internalTargets = new HashSet<>();
    private final BlockingQueue<Integer> externalTargets = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> allTargets = new LinkedBlockingQueue<>();

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
        return internalTargets.size() + externalTargets.size();
    }

    /* лифт доступен только если стоит и двери закрыты */
    public synchronized boolean isAvailable() {
        return status == ElevatorStatus.STOPPED &&
                internalTargets.isEmpty() &&
                externalTargets.isEmpty();
    }

    /* движется ли лифт к указанному этажу в нужном направлении */
    public synchronized boolean isMovingToward(int floor, Direction requestedDirection) {
        if (direction == Direction.IDLE || requestedDirection != direction) {
            return false;
        }

        if (direction == Direction.UP) {
            return floor >= currentFloor;
        } else { // DOWN
            return floor <= currentFloor;
        }
    }

    /* добавление цели из кабины */
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
                consolidateTargets();
            }
        }
    }

    /* Добавление внешней цели (вызов с этажа) */
    public void addExternalTarget(int floor, Direction requestedDirection) {
        synchronized (this) {
            if (floor < 1 || floor > maxFloor) {
                GuiLogger.log("Лифт " + id + ": игнорирую некорректный вызов на этаж " + floor);
                return;
            }

            if (floor == currentFloor && status == ElevatorStatus.STOPPED) {
                GuiLogger.log("Лифт " + id + ": уже на месте вызова " + floor);
                try {
                    openDoors();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }


            if (direction == Direction.IDLE ||
                    (direction == requestedDirection &&
                            ((direction == Direction.UP && floor >= currentFloor) ||
                                    (direction == Direction.DOWN && floor <= currentFloor)))) {

                externalTargets.offer(floor);
                GuiLogger.log("Лифт " + id + ": добавлен вызов с этажа " + floor +
                        " (направление: " + requestedDirection + ")");
                consolidateTargets();
            }
        }
    }

    /* объединение и сортировка всех целей */
    private void consolidateTargets() {
        allTargets.clear();


        Set<Integer> all = new HashSet<>(internalTargets);
        externalTargets.forEach(all::add);


        all.stream()
                .sorted((a, b) -> {
                    if (direction == Direction.UP) {
                        return Integer.compare(a, b);
                    } else if (direction == Direction.DOWN) {
                        return Integer.compare(b, a);
                    } else {

                        int distA = Math.abs(a - currentFloor);
                        int distB = Math.abs(b - currentFloor);
                        return Integer.compare(distA, distB);
                    }
                })
                .forEach(allTargets::offer);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Integer target = allTargets.poll();
                if (target != null) {
                    moveTo(target);
                } else {

                    synchronized (this) {
                        if (allTargets.isEmpty()) {
                            direction = Direction.IDLE;
                            status = ElevatorStatus.STOPPED;
                        }
                    }
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GuiLogger.log("Лифт " + id + " остановлен");
        } catch (Exception e) {
            GuiLogger.log("Лифт " + id + " ошибка: " + e.getMessage());
        } finally {
            GuiLogger.log("Лифт " + id + " завершил работу");
        }
    }

    private void moveTo(int target) throws InterruptedException {
        synchronized (this) {
            direction = target > currentFloor ? Direction.UP : Direction.DOWN;
            status = ElevatorStatus.MOVING;
        }

        GuiLogger.log("Лифт " + id + ": начал движение с этажа " +
                currentFloor + " на этаж " + target +
                " (направление: " + direction + ")");

        while (currentFloor != target && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(700); // движение между этажами
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }

            synchronized (this) {
                currentFloor += (direction == Direction.UP ? 1 : -1);

                // не нужно ли остановиться на промежуточном этаже
                if (shouldStopAtCurrentFloor()) {
                    openDoors();
                    removeCurrentFloorFromTargets();
                }
            }
        }

        if (!Thread.currentThread().isInterrupted()) {
            GuiLogger.log("Лифт " + id + " прибыл на целевой этаж " + currentFloor);
            openDoors();
            removeCurrentFloorFromTargets();
        }
    }

    private boolean shouldStopAtCurrentFloor() {
        return internalTargets.contains(currentFloor) ||
                externalTargets.contains(currentFloor);
    }

    private void removeCurrentFloorFromTargets() {
        synchronized (this) {
            internalTargets.remove(currentFloor);
            externalTargets.removeIf(floor -> floor == currentFloor);
            consolidateTargets();
        }
    }

    private void openDoors() throws InterruptedException {
        synchronized (this) {
            status = ElevatorStatus.DOORS_OPEN;
        }

        GuiLogger.log("Лифт " + id + " двери открыты на этаже " + currentFloor);
        try {
            Thread.sleep(1000); // двери открыты
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        GuiLogger.log("Лифт " + id + " двери закрыты");

        synchronized (this) {
            if (allTargets.isEmpty()) {
                status = ElevatorStatus.STOPPED;
                direction = Direction.IDLE;
            } else {
                status = ElevatorStatus.MOVING;
            }
        }
    }
}