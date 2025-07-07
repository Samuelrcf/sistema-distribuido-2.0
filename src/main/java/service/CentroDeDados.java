package service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class CentroDeDados implements MqttCallback {
	private final String brokerMQTTUrl = "tcp://test.mosquitto.org:1883";
	private MqttClient client;
    private final String HOST_BD = "localhost";
    private final int PORTA_BD;
    
    private MqttClient clientPublicador;

    private Connection rabbitConnection;
    private Channel rabbitChannel;
	
	public CentroDeDados(int portaBD) {
		this.PORTA_BD = portaBD;
		iniciarReceptorMQTT();
		iniciarPublicadorMQTT();
		iniciarRabbitMQ();
	}
	
	private void iniciarReceptorMQTT() {
		try {
			client = new MqttClient(brokerMQTTUrl, MqttClient.generateClientId());
			MqttConnectOptions options = new MqttConnectOptions();
			options.setAutomaticReconnect(true);
			options.setCleanSession(true);
			client.setCallback(this);
			client.connect(options);
			client.subscribe("dados_climaticos/+"); // assina todos os sub-tópicos
			System.out.println("Centro de Dados inscrito nos tópicos.");
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) { 
	    String dado = new String(message.getPayload());
	    System.out.println("Dado recebido de [" + topic + "]: " + dado);

	    String dadoNormalizado = normalizarDado(dado);
	    if (dadoNormalizado != null) {
	        distribuirParaUsuarios(dadoNormalizado);
	    	if(topic.equals("dados_climaticos/todos")) {
	    		enviarParaBanco(dadoNormalizado);
	    	}
	    }
	}

	@Override
	public void connectionLost(Throwable cause) {
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
	}
	
	private void iniciarPublicadorMQTT() {
	    try {
	        clientPublicador = new MqttClient(brokerMQTTUrl, MqttClient.generateClientId());
	        MqttConnectOptions options = new MqttConnectOptions();
	        options.setAutomaticReconnect(true);
	        options.setCleanSession(true);
	        clientPublicador.connect(options);
	        System.out.println("CentroDeDados conectado ao broker MQTT para publicação.");
	    } catch (MqttException e) {
	        System.out.println("Erro ao conectar ao broker MQTT de saída: " + e.getMessage());
	    }
	}
	
	private void iniciarRabbitMQ() {
	    ConnectionFactory factory = new ConnectionFactory();
	    factory.setHost("localhost"); 
	    try {
	        rabbitConnection = factory.newConnection();
	        rabbitChannel = rabbitConnection.createChannel();

	        rabbitChannel.queueDeclare("dados_norte", false, false, false, null);
	        rabbitChannel.queueDeclare("dados_sul", false, false, false, null);
	        rabbitChannel.queueDeclare("dados_leste", false, false, false, null);
	        rabbitChannel.queueDeclare("dados_oeste", false, false, false, null);
	        rabbitChannel.queueDeclare("dados_todos", false, false, false, null);

	        System.out.println("CentroDeDados conectado ao RabbitMQ.");
	    } catch (Exception e) {
	        System.out.println("Erro ao conectar ao RabbitMQ: " + e.getMessage());
	    }
	}

	private void distribuirParaUsuarios(String dado) {
	    try {
	        if (clientPublicador != null && clientPublicador.isConnected()) {
	            MqttMessage msg = new MqttMessage(dado.getBytes());
	            msg.setQos(0);

	            clientPublicador.publish("dados_processados/todos", msg);

	            String regiao = extrairRegiao(dado);
	            if (regiao != null) {
	                clientPublicador.publish("dados_processados/" + regiao.toLowerCase(), msg);
	            }
	        }

	        if (rabbitChannel != null && rabbitChannel.isOpen()) {
	            rabbitChannel.basicPublish("", "dados_processados/todos", null, dado.getBytes());

	            String regiao = extrairRegiao(dado);
	            if (regiao != null) {
	                rabbitChannel.basicPublish("", "dados_processados_" + regiao.toLowerCase(), null, dado.getBytes());
	            }
	        }
	    } catch (Exception e) {
	        System.out.println("Erro ao distribuir dado para usuários: " + e.getMessage());
	    }
	}
	
	private String extrairRegiao(String dado) {
	    if (dado.startsWith("[NORTE]")) return "norte";
	    if (dado.startsWith("[SUL]")) return "sul";
	    if (dado.startsWith("[LESTE]")) return "leste";
	    if (dado.startsWith("[OESTE]")) return "oeste";
	    return null;
	}
	
	private String normalizarDado(String dadoBruto) {
	    double temperatura, umidade, pressao, radiacao;
	    String regiao = "INDEFINIDA";

	    try {
	        String[] partes;
	        if (dadoBruto.contains("_")) {
	            partes = dadoBruto.split("_");
	            regiao = "NORTE";
	        } else if (dadoBruto.startsWith("(")) {
	            partes = dadoBruto.replaceAll("[()]", "").split(";");
	            regiao = "SUL";
	        } else if (dadoBruto.startsWith("{")) {
	            partes = dadoBruto.replaceAll("[{}]", "").split(",");
	            regiao = "LESTE";
	        } else if (dadoBruto.contains("#")) {
	            partes = dadoBruto.split("#");
	            regiao = "OESTE";
	        } else {
	            return null;
	        }

	        pressao = Double.parseDouble(partes[0]);
	        radiacao = Double.parseDouble(partes[1]);
	        temperatura = Double.parseDouble(partes[2]);
	        umidade = Double.parseDouble(partes[3]);

	        return String.format("[%s] [%.2f | %.2f | %.2f | %.2f]", regiao, temperatura, umidade, pressao, radiacao);
	    } catch (Exception e) {
	        System.out.println("Erro ao normalizar dado: " + e.getMessage());
	        return null;
	    }
	}

	private void enviarParaBanco(String dadoFormatado) {
	    try (Socket socket = new Socket(HOST_BD, PORTA_BD);
	        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
	        out.println(dadoFormatado);
	    } catch (IOException e) {
	        System.out.println("Erro ao enviar para o banco: " + e.getMessage());
	    }
	}
	
	public void encerrar() {
	    try {
	        if (client != null && client.isConnected()) client.disconnect();
	        if (clientPublicador != null && clientPublicador.isConnected()) clientPublicador.disconnect();
	        if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
	        if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
	    } catch (Exception e) {
	        System.out.println("Erro ao encerrar conexões: " + e.getMessage());
	    }
	}
	
	private void registrarLog(String mensagem) {
	    try {
	        File logDir = new File("logs");
	        if (!logDir.exists()) {
	            logDir.mkdirs();
	        }

	        try (FileWriter fw = new FileWriter("logs/centro_dados.log", true);
	             BufferedWriter bw = new BufferedWriter(fw);
	             PrintWriter out = new PrintWriter(bw)) {
	            String timestamp = java.time.LocalDateTime.now().toString();
	            out.println("[" + timestamp + "] " + mensagem);
	        }
	    } catch (IOException e) {
	        System.out.println("Erro ao registrar log: " + e.getMessage());
	    }
	}

}
