package consumers;

import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import constants.GlobalConstants;

public class ConsumidorMQTT {

    public static void main(String[] args) {

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
            default: topic = "dados_processados/todos";
        }

        try {
            MqttClient client = new MqttClient(GlobalConstants.BROKER_MQTT, "client-mqtt-1");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);

            client.connect(options);
            System.out.println("Consumidor MQTT conectado ao broker.");
            System.out.println("Inscrito no tópico: " + topic);

            client.subscribe(topic, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    System.out.println("Mensagem recebida [MQTT]: " + new String(message.getPayload()));
                }
            });


        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}


