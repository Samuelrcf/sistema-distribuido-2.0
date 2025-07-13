package drone;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import constants.GlobalConstants;
import drone.enums.Regiao;

public class Drone implements Runnable {
	private final Regiao regiao;
	private final MqttClient client;
	private final Random random = new Random();
	private final CountDownLatch latch;
	private volatile boolean running = true;

	public Drone(Regiao regiao, CountDownLatch latch) throws MqttException {
		this.regiao = regiao;
		this.latch = latch;
		this.client = new MqttClient(GlobalConstants.BROKER, MqttClient.generateClientId());
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);
		options.setCleanSession(false);
		client.connect(options);
	}

	@Override
	public void run() {
		String topico = "dados_climaticos";
		try {
			while (running) {
				double pressao = 950 + random.nextDouble() * 100;
				double radiacao = 100 + random.nextDouble() * 900;
				double temperatura = -10 + random.nextDouble() * 50;
				double umidade = 10 + random.nextDouble() * 90;

				String dadoFormatado = formatarDados(pressao, radiacao, temperatura, umidade);

				MqttMessage mensagem = new MqttMessage(dadoFormatado.getBytes());
				mensagem.setQos(1);
				
				client.publish(topico, mensagem);

				System.out.println("Drone " + regiao + " publicou: " + dadoFormatado);

				TimeUnit.SECONDS.sleep(2 + random.nextInt(4));
			}

		} catch (InterruptedException e) {
			System.out.println("Drone " + regiao + " interrompido durante sleep.");
			Thread.currentThread().interrupt();
		} catch (MqttException e) {
			e.printStackTrace();
		} finally {
			latch.countDown();
		}
	}

	public void stop() {
		running = false;
		try {
			if (client.isConnected()) {
				client.disconnect();
			}
			client.close();
		} catch (MqttException e) {
			e.printStackTrace();
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
