package drone.starter;

import drone.Drone;
import drone.enums.Regiao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DroneStarter {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4); 

        try {
            executor.execute(new Drone(Regiao.NORTE));
            executor.execute(new Drone(Regiao.SUL));
            executor.execute(new Drone(Regiao.LESTE));
            executor.execute(new Drone(Regiao.OESTE));

            System.out.println("Drones iniciados.");

            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                System.out.println("Encerrando coletas...");
                executor.shutdownNow(); 
            }

            System.out.println("Execução encerrada.");

        } catch (Exception e) {
            e.printStackTrace();
            executor.shutdownNow(); 
        }
    }
}
