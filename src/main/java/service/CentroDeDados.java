package service;

import org.eclipse.paho.client.mqttv3.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class CentroDeDados implements MqttCallback {
    private final String brokerUrl = "tcp://localhost:1883";
    private final String topic = "clima";
    private MqttClient client;

    private final String MULTICAST_IP = "239.0.0.1";
    private final int MULTICAST_PORT = 4446;

    private final int PORTA_USUARIO = 5000;

    private final List<ServidorInfo> servidores = Arrays.asList(
            new ServidorInfo("localhost", 6001, 1),
            new ServidorInfo("localhost", 6002, 2)
    );

    private final ExecutorService executorUsuarios = Executors.newCachedThreadPool();

    public CentroDeDados() {
        iniciarMQTT();
        escutarUsuarios();
    }

    // === Parte MQTT ===
    private void iniciarMQTT() {
        try {
            client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.setCallback(this);
            client.connect(options);
            client.subscribe(topic);
            System.out.println("Centro de Dados inscrito no tópico: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String dado = new String(message.getPayload());
        System.out.println("Dado recebido via MQTT: " + dado);
        enviarViaMulticast(dado);
    }

    private void enviarViaMulticast(String mensagem) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buf = mensagem.getBytes();
            InetAddress groupName = InetAddress.getByName(MULTICAST_IP);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, groupName, MULTICAST_PORT);
            socket.send(packet);
        } catch (Exception e) {
            System.out.println("Erro ao enviar multicast: " + e.getMessage());
        }
    }

    // === Parte Socket com Usuário ===
    private void escutarUsuarios() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORTA_USUARIO)) {
                System.out.println("Centro de Dados escutando usuários na porta " + PORTA_USUARIO);
                while (true) {
                    Socket socket = serverSocket.accept();
                    executorUsuarios.execute(() -> lidarComUsuario(socket));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void lidarComUsuario(Socket socketUsuario) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socketUsuario.getInputStream()));
                PrintWriter out = new PrintWriter(socketUsuario.getOutputStream(), true)
        ) {
            String clientRequest = in.readLine();
            while (clientRequest != null && !clientRequest.equals("1") && !clientRequest.equals("0")) {
                out.println("Comando inválido.");
                clientRequest = in.readLine();
            }

            if ("1".equals(clientRequest)) {
                System.out.println("Usuário iniciou conexão.");

                solicitarStatusDosServidores();
                List<ServidorStatus> respostas = receberStatusDosServidores(servidores.size());
                ServidorInfo escolhido = escolherServidor(respostas);

                out.println(escolhido.host + ":" + escolhido.porta);
            } else if ("0".equals(clientRequest)) {
                out.println("Conexão encerrada.");
            }

            socketUsuario.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void solicitarStatusDosServidores() {
        enviarViaMulticast("STATUS?");
    }

    private List<ServidorStatus> receberStatusDosServidores(int quantidade) {
        List<ServidorStatus> respostas = new ArrayList<>();
        try (ServerSocket statusSocket = new ServerSocket(5500)) {
            for (int i = 0; i < quantidade; i++) {
                Socket s = statusSocket.accept();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String linha = in.readLine(); // Exemplo: localhost:6001;3;1
                    String[] partes = linha.split(";");
                    String[] hostEPorta = partes[0].split(":");
                    String host = hostEPorta[0];
                    int porta = Integer.parseInt(hostEPorta[1]);
                    int conexoes = Integer.parseInt(partes[1]);
                    int peso = Integer.parseInt(partes[2]);
                    respostas.add(new ServidorStatus(host, porta, conexoes, peso));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return respostas;
    }

    private ServidorInfo escolherServidor(List<ServidorStatus> servidores) {
        ServidorStatus melhor = Collections.min(servidores,
                Comparator.comparingDouble(s -> s.conexoes / (double) s.peso));
        return new ServidorInfo(melhor.host, melhor.porta, melhor.peso);
    }

    @Override
    public void connectionLost(Throwable cause) {
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    // === Classes auxiliares ===
    static class ServidorInfo {
        String host;
        int porta;
        int peso;

        ServidorInfo(String host, int porta, int peso) {
            this.host = host;
            this.porta = porta;
            this.peso = peso;
        }
    }

    static class ServidorStatus {
        String host;
        int porta;
        int conexoes;
        int peso;

        ServidorStatus(String host, int porta, int conexoes, int peso) {
            this.host = host;
            this.porta = porta;
            this.conexoes = conexoes;
            this.peso = peso;
        }
    }
}
