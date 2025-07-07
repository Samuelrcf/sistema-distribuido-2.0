package consumers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ConsumidorRabbitMQ {

	private static final String ARQUIVO_DADOS = "dados/dados_recebidos.txt";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Escolha uma opção:");
        System.out.println("1 - Ver dados em tempo real");
        System.out.println("2 - Consultar histórico salvo em arquivo");
        System.out.print("Opção: ");
        int escolha = scanner.nextInt();
        scanner.nextLine(); // consumir \n

        if (escolha == 1) {
            iniciarModoTempoReal(scanner);
        } else if (escolha == 2) {
            consultarHistoricoArquivo();
        } else {
            System.out.println("Opção inválida.");
        }

        scanner.close();
    }

    private static void iniciarModoTempoReal(Scanner scanner) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        System.out.println("Escolha a fila para receber dados:");
        System.out.println("1 - dados_processados_todos");
        System.out.println("2 - dados_processados_norte");
        System.out.println("3 - dados_processados_sul");
        System.out.println("4 - dados_processados_leste");
        System.out.println("5 - dados_processados_oeste");
        System.out.print("Opção: ");
        int opcao = scanner.nextInt();
        scanner.nextLine();

        String queueName;
        switch (opcao) {
            case 2: queueName = "dados_processados_norte"; break;
            case 3: queueName = "dados_processados_sul"; break;
            case 4: queueName = "dados_processados_leste"; break;
            case 5: queueName = "dados_processados_oeste"; break;
            default: queueName = "dados_processados_todos"; break;
        }

        com.rabbitmq.client.Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        System.out.println("Aguardando mensagens da fila: " + queueName);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String dado = new String(delivery.getBody(), "UTF-8");
            System.out.println("Recebido: " + dado);
            salvarEmArquivo(dado);
        };
        
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private static void salvarEmArquivo(String dado) {
        File dir = new File("dados");
        if (!dir.exists()) {
            dir.mkdirs(); // cria o diretório se não existir
        }

        File arquivo = new File(dir, "dados_recebidos.txt");

        try (FileWriter fw = new FileWriter(arquivo, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(dado);
            System.out.println("Salvo em arquivo: " + dado);

        } catch (IOException e) {
            System.out.println("Erro ao salvar no arquivo: " + e.getMessage());
        }
    }

    private static void consultarHistoricoArquivo() {
        System.out.println("=== Histórico de Dados Salvos ===");
        try (Scanner leitor = new Scanner(new java.io.File(ARQUIVO_DADOS))) {
            while (leitor.hasNextLine()) {
                System.out.println(leitor.nextLine());
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler arquivo de histórico: " + e.getMessage());
        }
    }
}




