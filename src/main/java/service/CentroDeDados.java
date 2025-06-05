package service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class CentroDeDados implements MqttCallback {
	private final String brokerUrl = "tcp://test.mosquitto.org";
	private final String topic = "clima";
	private MqttClient client;

	private final String MULTICAST_IP = "239.0.0.1";
	private final int MULTICAST_PORT = 4446;

	private final int PORTA_USUARIO = 5000;

	private final List<ServidorInfo> servidores = Arrays.asList(new ServidorInfo("localhost", 7001),
			new ServidorInfo("localhost", 7002));

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<ServidorStatus> statusServidor = new ArrayList<>();
	
	private final AtomicInteger rrIndex = new AtomicInteger(0);
	
	private final List<ServidorStatus> rrList = new ArrayList<>();

	public CentroDeDados() {
		iniciarMQTT();
		escutarUsuarios();
		receberStatus(); 
	}
	
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
	
	private void escutarUsuarios() {
		Executors.newSingleThreadExecutor().execute(() -> {
			try (ServerSocket serverSocket = new ServerSocket(PORTA_USUARIO)) {
				System.out.println("Centro de Dados escutando usuários na porta " + PORTA_USUARIO);
				while (true) {
					Socket socket = serverSocket.accept();
					executor.execute(() -> lidarComUsuario(socket));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void receberStatus() {
		Executors.newSingleThreadExecutor().execute(() -> {
			try (ServerSocket statusSocket = new ServerSocket(5500)) {
				System.out.println("Aguardando status dos servidores na porta 5500...");
				while (true) {
					Socket s = statusSocket.accept();
					executor.execute(() -> {
						try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
							String linha = in.readLine(); // ex: localhost:6001;3;1
							if (linha != null) {
								String[] partes = linha.split(";");
								String[] hostEPorta = partes[0].split(":");
								String host = hostEPorta[0];
								int porta = Integer.parseInt(hostEPorta[1]);
								int conexoes = Integer.parseInt(partes[1]);
								int peso = Integer.parseInt(partes[2]);
								statusServidor.add(new ServidorStatus(host, porta, conexoes, peso));
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
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

	private void lidarComUsuario(Socket socketUsuario) {
	    try (
	        BufferedReader in = new BufferedReader(new InputStreamReader(socketUsuario.getInputStream()));
	        PrintWriter out = new PrintWriter(socketUsuario.getOutputStream(), true)
	    ) {
	        String identificador = identificarUsuario(in, socketUsuario);
	        int tipoBalanceamento = obterEstrategiaBalanceamento(in, socketUsuario, identificador);
	        String clientRequest = aguardarComandoUsuario(in, out);

	        if ("1".equals(clientRequest)) {
	            processarConsulta(out, socketUsuario, tipoBalanceamento, identificador);
	        } else if ("0".equals(clientRequest)) {
	            out.println("Conexão encerrada.");
	            registrarLog("[" + identificador + "] encerrou a conexão.");
	        }

	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	private String identificarUsuario(BufferedReader in, Socket socket) throws IOException {
	    String identificador = in.readLine();
	    String ip = socket.getInetAddress().getHostAddress();
	    registrarLog("[" + identificador + "] " + " (" + ip + ") conectado.");
	    return identificador;
	}

	private int obterEstrategiaBalanceamento(BufferedReader in, Socket socket, String identificador) throws IOException {
	    String estrategia = in.readLine(); 
	    int tipo = estrategia.equals("2") ? 2 : 1; 
	    registrarLog("[" + identificador + "] escolheu algoritmo: " +
	                (tipo == 1 ? "Weighted Least Connections" : "Round-Robin"));
	    return tipo;
	}

	private String aguardarComandoUsuario(BufferedReader in, PrintWriter out) throws IOException {
	    String comando = in.readLine();
	    while (comando != null && !comando.equals("1") && !comando.equals("0")) {
	        out.println("Comando inválido.");
	        comando = in.readLine();
	    }
	    return comando;
	}

	private void processarConsulta(PrintWriter out, Socket socket, int tipo, String identificador) {
	    registrarLog("[" + identificador + "] iniciou a consulta.");

	    List<ServidorInfo> servidoresDisponiveis = servidores.stream()
	        .filter(s -> verificarDisponibilidade(s.host, s.porta))
	        .collect(Collectors.toList());

	    if (servidoresDisponiveis.isEmpty()) {
	        out.println("Nenhum servidor disponível no momento.");
	        registrarLog("[" + identificador + "] Nenhum servidor disponível.");
	        return;
	    }

	    synchronized (this) {
	    	solicitarStatusDosServidores();
		    try {
		        Thread.sleep(1000);
		        
		        if(statusServidor.isEmpty()){
			        out.println("Nenhuma resposta dos servidores.");
			        registrarLog("[" + identificador + "] Nenhuma resposta dos servidores.");
			        return;
		        }
		        
		        ServidorInfo escolhido;
		        
		        if (tipo == 1) {
		            escolhido = escolherWLC(statusServidor);
		        } else {
		            escolhido = escolherRoundRobin(statusServidor);
		        }
		        
			    out.println(escolhido.host + ":" + escolhido.porta);
			    registrarLog("[" + identificador + "] foi direcionado para " + escolhido.host + ":" + escolhido.porta);

		    } catch (InterruptedException e) {
		        e.printStackTrace();
		    }finally {
		    	statusServidor.clear();
		    }
	    }

	}
	
	private void solicitarStatusDosServidores() {
		enviarViaMulticast("STATUS?");
	}

	private boolean verificarDisponibilidade(String host, int porta) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, porta), 1000); // timeout de 1s
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private ServidorInfo escolherWLC(List<ServidorStatus> servidores) {
	    return servidores.stream()
	        .min(Comparator.comparingInt(s -> s.conexoes * s.peso))
	        .map(s -> new ServidorInfo(s.host, s.porta))
	        .orElseThrow(); 
	}

	private ServidorInfo escolherRoundRobin(List<ServidorStatus> servidores) {
		rrList.clear();
	    rrList.addAll(servidores.stream()
	        .sorted(Comparator.comparing(s -> s.host + ":" + s.porta))
	        .toList());

	    int index = rrIndex.getAndIncrement() % rrList.size();
	    ServidorStatus s = rrList.get(index);
	    return new ServidorInfo(s.host, s.porta);
	}

	@Override
	public void connectionLost(Throwable cause) {
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
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

	// classes auxiliares
	static class ServidorInfo {
		String host;
		int porta;

		ServidorInfo(String host, int porta) {
			this.host = host;
			this.porta = porta;
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
