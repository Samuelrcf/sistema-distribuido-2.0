package ui;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import constants.GlobalConstants;

public class Dashboard {

	private static final String TOPICO = "dados_processados/todos";
	private static final String ARQUIVO_DADOS = "banco_6001.txt"; // exemplo
	private static int totalDados = 0;

	// soma dos valores recebidos por região
	private static final Map<String, Double> somaTemperatura = new HashMap<>();
	private static final Map<String, Double> somaUmidade = new HashMap<>();
	private static final Map<String, Double> somaPressao = new HashMap<>();
	private static final Map<String, Double> somaRadiacao = new HashMap<>();

	// quantidade de leituras recebidas por região
	private static final Map<String, Integer> totalTemperatura = new HashMap<>();
	private static final Map<String, Integer> totalUmidade = new HashMap<>();
	private static final Map<String, Integer> totalPressao = new HashMap<>();
	private static final Map<String, Integer> totalRadiacao = new HashMap<>();

	public static void main(String[] args) throws Exception {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Escolha uma opção:");
		System.out.println("1 - Acompanhar dados em tempo real (MQTT)");
		System.out.println("2 - Processar dados históricos do banco (arquivo)");
		System.out.print("Opção: ");
		int escolha = scanner.nextInt();
		scanner.nextLine(); 

		if (escolha == 1) {
			iniciarMQTT();
		} else if (escolha == 2) {
			processarArquivo(ARQUIVO_DADOS);
			exibirDashboard();
		} else {
			System.out.println("Opção inválida.");
		}

		scanner.close();
	}

	private static void iniciarMQTT() throws MqttException {
		MqttClient client = new MqttClient(GlobalConstants.BROKER_MQTT, "dashboard");
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);
		options.setCleanSession(false);

		client.connect(options);
		System.out.println("Dashboard MQTT conectado ao broker.");

		client.subscribe(TOPICO, new IMqttMessageListener() {
		    @Override
		    public void messageArrived(String topic, MqttMessage msg) throws Exception {
		        String dado = new String(msg.getPayload());
		        System.out.println("Dado recebido: " + dado);
		        processarDado(dado);
		        exibirDashboard();
		    }
		});

	}

	private static void processarArquivo(String caminhoArquivo) {
		System.out.println("Lendo dados do arquivo: " + caminhoArquivo);
		try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
			String linha;
			while ((linha = br.readLine()) != null) {
				if (!linha.isBlank()) {
					processarDado(linha);
				}
			}
		} catch (IOException e) {
			System.out.println("Erro ao ler arquivo: " + e.getMessage());
		}
	}

	private static void processarDado(String dado) {
		try {
			String[] partes = dado.split("\\] \\["); // [NORTE] [24.5 | 60.0 | 1013.0 | 500.0]
			String regiao = partes[0].replace("[", "").trim();
			String[] valores = partes[1].replace("]", "").split("\\|");

			double temperatura = Double.parseDouble(valores[0].trim().replace(",", "."));
			double umidade = Double.parseDouble(valores[1].trim().replace(",", "."));
			double pressao = Double.parseDouble(valores[2].trim().replace(",", "."));
			double radiacao = Double.parseDouble(valores[3].trim().replace(",", "."));

			totalDados += 4;

			// soma valores por região
			if (somaTemperatura.containsKey(regiao)) {
				somaTemperatura.put(regiao, somaTemperatura.get(regiao) + temperatura);
			} else {
				somaTemperatura.put(regiao, temperatura);
			}

			if (somaUmidade.containsKey(regiao)) {
				somaUmidade.put(regiao, somaUmidade.get(regiao) + umidade);
			} else {
				somaUmidade.put(regiao, umidade);
			}

			if (somaPressao.containsKey(regiao)) {
				somaPressao.put(regiao, somaPressao.get(regiao) + pressao);
			} else {
				somaPressao.put(regiao, pressao);
			}

			if (somaRadiacao.containsKey(regiao)) {
				somaRadiacao.put(regiao, somaRadiacao.get(regiao) + radiacao);
			} else {
				somaRadiacao.put(regiao, radiacao);
			}

			// conta quantas leituras foram feitas por região
			if (totalTemperatura.containsKey(regiao)) {
				totalTemperatura.put(regiao, totalTemperatura.get(regiao) + 1);
			} else {
				totalTemperatura.put(regiao, 1);
			}

			if (totalUmidade.containsKey(regiao)) {
				totalUmidade.put(regiao, totalUmidade.get(regiao) + 1);
			} else {
				totalUmidade.put(regiao, 1);
			}

			if (totalPressao.containsKey(regiao)) {
				totalPressao.put(regiao, totalPressao.get(regiao) + 1);
			} else {
				totalPressao.put(regiao, 1);
			}

			if (totalRadiacao.containsKey(regiao)) {
				totalRadiacao.put(regiao, totalRadiacao.get(regiao) + 1);
			} else {
				totalRadiacao.put(regiao, 1);
			}


		} catch (Exception e) {
			System.out.println("Erro ao processar dado: " + e);
		}
	}

	private static void exibirDashboard() {
		System.out.println("\n=========== DASHBOARD CLIMÁTICO ===========");
		System.out.println("Total de dados recebidos: " + totalDados);

		int qtdTemp = 0;
		for (Integer valor : totalTemperatura.values()) { // Temperatura: [3, 2, 5, 4]
		    qtdTemp += valor;
		}

		int qtdUmid = 0;
		for (Integer valor : totalUmidade.values()) {
		    qtdUmid += valor;
		}

		int qtdPres = 0;
		for (Integer valor : totalPressao.values()) {
		    qtdPres += valor;
		}

		int qtdRad = 0;
		for (Integer valor : totalRadiacao.values()) {
		    qtdRad += valor;
		}

		System.out.println("\nTotal de dados por elemento climático:");
		System.out.println("Temperatura: " + qtdTemp);
		System.out.println("Umidade: " + qtdUmid);
		System.out.println("Pressão: " + qtdPres);
		System.out.println("Radiação: " + qtdRad);

		System.out.println("\nContribuição percentual por região:");

		Map<String, Double> mediasTemp = calcularMedias(somaTemperatura, totalTemperatura);
		Map<String, Double> mediasUmid = calcularMedias(somaUmidade, totalUmidade);
		Map<String, Double> mediasPres = calcularMedias(somaPressao, totalPressao);
		Map<String, Double> mediasRad = calcularMedias(somaRadiacao, totalRadiacao);

		exibirPercentuais("Temperatura", mediasTemp);
		exibirPercentuais("Umidade", mediasUmid);
		exibirPercentuais("Pressão", mediasPres);
		exibirPercentuais("Radiação", mediasRad);

		System.out.println("===========================================\n");
	}

	private static Map<String, Double> calcularMedias(Map<String, Double> soma, Map<String, Integer> contagem) {
		Map<String, Double> medias = new HashMap<>();
		for (String regiao : soma.keySet()) {
			int count = contagem.getOrDefault(regiao, 0);
			if (count > 0) {
				medias.put(regiao, soma.get(regiao) / count);
			}
		}
		return medias;
	}

	private static void exibirPercentuais(String titulo, Map<String, Double> medias) {
		System.out.println("\n" + titulo + ":");
		double somaMediasAbs = medias.values().stream().mapToDouble(Math::abs).sum();
		for (Map.Entry<String, Double> entry : medias.entrySet()) {
			String regiao = entry.getKey();
			double media = entry.getValue();
			double percentual = (Math.abs(media) / somaMediasAbs) * 100.0;
			System.out.printf("%s: %.2f%% (média: %.2f)\n", regiao, percentual, media);
		}
	}
}
