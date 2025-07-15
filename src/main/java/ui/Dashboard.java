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
	private static int totalMensagens = 0;

	private static final Map<String, Double> somaTemperatura = new HashMap<>();
	private static final Map<String, Double> somaUmidade = new HashMap<>();
	private static final Map<String, Double> somaPressao = new HashMap<>();
	private static final Map<String, Double> somaRadiacao = new HashMap<>();

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
		scanner.nextLine(); // consumir \n

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
			String[] partes = dado.split("\\] \\[");
			String regiao = partes[0].replace("[", "").trim();
			String[] valores = partes[1].replace("]", "").split("\\|");

			double temperatura = Double.parseDouble(valores[0].trim().replace(",", "."));
			double umidade = Double.parseDouble(valores[1].trim().replace(",", "."));
			double pressao = Double.parseDouble(valores[2].trim().replace(",", "."));
			double radiacao = Double.parseDouble(valores[3].trim().replace(",", "."));

			totalMensagens += 4;

			somaTemperatura.merge(regiao, temperatura, Double::sum);
			somaUmidade.merge(regiao, umidade, Double::sum);
			somaPressao.merge(regiao, pressao, Double::sum);
			somaRadiacao.merge(regiao, radiacao, Double::sum);

			totalTemperatura.merge(regiao, 1, Integer::sum);
			totalUmidade.merge(regiao, 1, Integer::sum);
			totalPressao.merge(regiao, 1, Integer::sum);
			totalRadiacao.merge(regiao, 1, Integer::sum);

		} catch (Exception e) {
			System.out.println("Erro ao processar dado: " + e);
		}
	}

	private static void exibirDashboard() {
		System.out.println("\n=========== DASHBOARD CLIMÁTICO ===========");
		System.out.println("Total de dados recebidos: " + totalMensagens);

		int qtdTemp = totalTemperatura.values().stream().mapToInt(Integer::intValue).sum();
		int qtdUmid = totalUmidade.values().stream().mapToInt(Integer::intValue).sum();
		int qtdPres = totalPressao.values().stream().mapToInt(Integer::intValue).sum();
		int qtdRad = totalRadiacao.values().stream().mapToInt(Integer::intValue).sum();

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
