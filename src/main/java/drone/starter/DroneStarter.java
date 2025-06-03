package drone.starter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import drone.Drone;
import drone.enums.Regiao;

public class DroneStarter {
    public static void main(String[] args) {
        int qtdDrones = 4;
        ExecutorService executor = Executors.newFixedThreadPool(qtdDrones);
        CountDownLatch latch = new CountDownLatch(qtdDrones);

        try {
            executor.execute(new Drone(Regiao.NORTE, latch));
            executor.execute(new Drone(Regiao.SUL, latch));
            executor.execute(new Drone(Regiao.LESTE, latch));
            executor.execute(new Drone(Regiao.OESTE, latch));

            System.out.println("Drones iniciados.");

            TimeUnit.SECONDS.sleep(60);

            executor.shutdownNow();

            latch.await();

            System.out.println("Todos os drones foram encerrados.");
            
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            executor.shutdownNow();
        }
    }
}
