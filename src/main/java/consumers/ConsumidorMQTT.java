package consumers;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.Scanner;
import org.eclipse.paho.client.mqttv3.*;

public class ConsumidorMQTT {

    public static void main(String[] args) {
        String broker = "tcp://test.mosquitto.org:1883";

        Scanner scanner = new Scanner(System.in);
        System.out.println("Escolha o tópico para receber dados:");
        System.out.println("1 - Todos");
        System.out.println("2 - Norte");
        System.out.println("3 - Sul");
        System.out.println("4 - Leste");
        System.out.println("5 - Oeste");
        System.out.print("Opção: ");
        int opcao = scanner.nextInt();
        scanner.close();

        String topic;
        switch (opcao) {
            case 1: topic = "dados_processados/todos"; break;
            case 2: topic = "dados_processados/norte"; break;
            case 3: topic = "dados_processados/sul"; break;
            case 4: topic = "dados_processados/leste"; break;
            case 5: topic = "dados_processados/oeste"; break;
            default:
                System.out.println("Opção inválida. Usando tópico 'dados_processados/todos'");
                topic = "dados_processados/todos";
        }

        try {
            MqttClient client = new MqttClient(broker, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            client.connect(options);
            System.out.println("Consumidor MQTT conectado ao broker.");
            System.out.println("Inscrito no tópico: " + topic);

            client.subscribe(topic, (t, msg) -> {
                System.out.println("Mensagem recebida [MQTT]: " + new String(msg.getPayload()));
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}


