package usuario;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Usuario implements ConsultaDrone {

    private final String hostCentroDados;
    private final int portaCentroDados;

    public Usuario(String hostCentroDados, int portaCentroDados) {
        this.hostCentroDados = hostCentroDados;
        this.portaCentroDados = portaCentroDados;
    }

    @Override
    public void solicitarDados() { // criar lógica para escolher o balanceamento em tempo de execução
        Scanner scanner = new Scanner(System.in);

        try (
            // 1. Conecta ao centro de dados só para descobrir o servidor
            Socket socketCentro = new Socket(hostCentroDados, portaCentroDados);
            PrintWriter outCentro = new PrintWriter(socketCentro.getOutputStream(), true);
            BufferedReader inCentro = new BufferedReader(new InputStreamReader(socketCentro.getInputStream()))
        ) {
        	
        	String conexao;
        	do {
        	    System.out.print("[1] - Iniciar \n[0] - Sair\n");
        	    String comando = scanner.nextLine().trim();
        	    outCentro.println(comando);

        	    conexao = inCentro.readLine();
        	    if (conexao == null || conexao.contains("Conexão encerrada")) {
        	        System.out.println("Conexão encerrada.");
        	        scanner.close();
        	        return;
        	    }

        	    if (conexao.contains("Comando inválido")) {
        	        System.out.println("Comando inválido. Tente novamente.");
        	    }

        	} while (conexao.contains("Comando inválido"));
        	
            if (conexao == null || !conexao.contains(":")) {
                System.out.println("Endereço do servidor inválido.");
                return;
            }

            String[] partes = conexao.split(":");
            String ipServidor = partes[0];
            int portaServidor = Integer.parseInt(partes[1]);

            // 2. Conecta ao servidor diretamente
            try (
                Socket servidorSocket = new Socket(ipServidor, portaServidor);
                PrintWriter servidorOut = new PrintWriter(servidorSocket.getOutputStream(), true);
                BufferedReader servidorIn = new BufferedReader(new InputStreamReader(servidorSocket.getInputStream()))
            ) {
                System.out.println("Conectado ao servidor da região. Digite 'sair' para encerrar.");

                String resposta;
                while (true) {
                    System.out.print("Digite a região desejada (ou 'sair'): ");
                    String novaRegiao = scanner.nextLine().trim();

                    servidorOut.println(novaRegiao);

                    if (novaRegiao.equalsIgnoreCase("sair")) {
                        System.out.println("Conexão encerrada.");
                        scanner.close();
                        break;
                    }

                    while ((resposta = servidorIn.readLine()) != null) {
                        if (resposta.equals("__FIM__")) break;
                        System.out.println("Resposta do servidor: " + resposta);
                    }
                }

            } catch (IOException e) {
                System.out.println("Erro ao se conectar ao servidor: " + e.getMessage());
            }

        } catch (IOException e) {
            System.out.println("Erro ao se comunicar com o centro de dados: " + e.getMessage());
        }
    }

}
