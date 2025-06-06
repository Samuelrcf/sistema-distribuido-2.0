package drone;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import drone.enums.Regiao;

public class Drone implements Runnable {
    private final Regiao regiao;
    private final MqttClient client;
    private final Random random = new Random();
    private final CountDownLatch latch;
    private final String brokerUrl = "tcp://test.mosquitto.org";

    public Drone(Regiao regiao, CountDownLatch latch) throws MqttException {
        this.regiao = regiao;
        this.latch = latch;
        this.client = new MqttClient(brokerUrl, MqttClient.generateClientId());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        client.connect(options);
    }

    @Override
    public void run() {
    	String topico = "clima";
    	MqttMessage mensagem;
        try {
            while (!Thread.currentThread().isInterrupted()) { // enquanto n chegar no shutdownNow ela continua enviando dados
                double pressao = 950 + random.nextDouble() * 100;
                double radiacao = 100 + random.nextDouble() * 900;
                double temperatura = -10 + random.nextDouble() * 50;
                double umidade = 10 + random.nextDouble() * 90;

                String dadoFormatado = formatarDados(pressao, radiacao, temperatura, umidade);
                
                mensagem = new MqttMessage(dadoFormatado.getBytes());
                mensagem.setQos(1);
                client.publish(topico, mensagem);

                System.out.println("Drone " + regiao + " publicou: " + dadoFormatado);

                TimeUnit.SECONDS.sleep(2 + random.nextInt(4));
            }
            
            mensagem = new MqttMessage("FIM".getBytes());
            client.publish(topico, mensagem);
            
            client.disconnect();
            client.close();
        } catch (InterruptedException e) {
            System.out.println("Drone " + regiao + " interrompido.");
            Thread.currentThread().interrupt(); 
        } catch (MqttException e) {
            e.printStackTrace();
        } finally {
            latch.countDown(); 
        }
    }

    private String formatarDados(double pressao, double radiacao, double temperatura, double umidade) {
        switch (regiao) {
            case NORTE:
                return String.format(Locale.US, "%.2f_%.2f_%.2f_%.2f", pressao, radiacao, temperatura, umidade);
            case SUL:
                return String.format(Locale.US, "(%.2f;%.2f;%.2f;%.2f)", pressao, radiacao, temperatura, umidade);
            case LESTE:
                return String.format(Locale.US, "{%.2f,%.2f,%.2f,%.2f}", pressao, radiacao, temperatura, umidade);
            case OESTE:
                return String.format(Locale.US, "%.2f#%.2f#%.2f#%.2f", pressao, radiacao, temperatura, umidade);
            default:
                throw new IllegalArgumentException("Região inválida: " + regiao);
        }
    }
}
