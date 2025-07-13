package drone.starter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttException;

import drone.Drone;
import drone.enums.Regiao;

public class DroneStarter {
    public static void main(String[] args) throws InterruptedException {
        int qtdDrones = 4;
        ExecutorService executor = Executors.newFixedThreadPool(qtdDrones);
        CountDownLatch latch = new CountDownLatch(qtdDrones);

        Drone[] drones = new Drone[qtdDrones];

        try {
            drones[0] = new Drone(Regiao.NORTE, latch);
            drones[1] = new Drone(Regiao.SUL, latch);
            drones[2] = new Drone(Regiao.LESTE, latch);
			drones[3] = new Drone(Regiao.OESTE, latch);
		} catch (MqttException e) {
			e.printStackTrace();
		}

        for (Drone drone : drones) {
            executor.execute(drone);
        }

        System.out.println("Drones iniciados.");

        TimeUnit.SECONDS.sleep(30);

        for (Drone drone : drones) {
            drone.stop();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        latch.await();

        System.out.println("Todos os drones foram encerrados.");
    }
}

