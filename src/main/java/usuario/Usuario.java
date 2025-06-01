package usuario;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Usuario implements ConsultaDrone {

    private final String hostCentroDados;
    private final int portaCentroDados;
    private String nome;
    private final Scanner scanner = new Scanner(System.in);

    public Usuario(String hostCentroDados, int portaCentroDados) {
        this.hostCentroDados = hostCentroDados;
        this.portaCentroDados = portaCentroDados;
    }

    @Override
    public void solicitarDados() {
        try (
            Socket socketCentro = new Socket(hostCentroDados, portaCentroDados);
            PrintWriter outCentro = new PrintWriter(socketCentro.getOutputStream(), true);
            BufferedReader inCentro = new BufferedReader(new InputStreamReader(socketCentro.getInputStream()))
        ) {
            System.out.print("Identifique-se (nome ou ID): ");
            nome = scanner.nextLine().trim();
            outCentro.println(nome);

            escolherBalanceamento(outCentro);
            String conexao = menuUsuario(outCentro, inCentro);
            if (conexao == null) return;

            String[] partes = conexao.split(":");
            String ipServidor = partes[0];
            int portaServidor = Integer.parseInt(partes[1]);

            conectarAoServidor(ipServidor, portaServidor);

        } catch (IOException e) {
            System.out.println("Erro ao se comunicar com o centro de dados: " + e.getMessage());
        }
    }

    private void escolherBalanceamento(PrintWriter outCentro) {
        System.out.print("Escolha o algoritmo de balanceamento:\n[1] Weighted Score\n[2] Round-Robin\n> ");
        String tipoBalanceamento = scanner.nextLine().trim();
        outCentro.println(tipoBalanceamento);
    }

    private String menuUsuario(PrintWriter outCentro, BufferedReader inCentro) throws IOException {
        String conexao;
        do {
            System.out.print("[1] - Iniciar \n[0] - Sair\n");
            String comando = scanner.nextLine().trim();
            outCentro.println(comando);

            conexao = inCentro.readLine();
            if (conexao == null || conexao.contains("Conexão encerrada")) {
                System.out.println("Conexão encerrada.");
                return null;
            }

            if (conexao.contains("Comando inválido")) {
                System.out.println("Comando inválido. Tente novamente.");
            }

        } while (conexao.contains("Comando inválido"));

        if (!conexao.contains(":")) {
            System.out.println("Endereço do servidor inválido.");
            return null;
        }

        return conexao;
    }

    private void conectarAoServidor(String ip, int porta) {
        try (
            Socket servidorSocket = new Socket(ip, porta);
            PrintWriter servidorOut = new PrintWriter(servidorSocket.getOutputStream(), true);
            BufferedReader servidorIn = new BufferedReader(new InputStreamReader(servidorSocket.getInputStream()))
        ) {
        	servidorOut.println(nome);
            System.out.println("Conectado ao servidor da região. Digite 'sair' para encerrar.");

            while (true) {
                System.out.print("Digite a região desejada (ou 'sair'): ");
                String novaRegiao = scanner.nextLine().trim();
                servidorOut.println(novaRegiao);

                if (novaRegiao.equalsIgnoreCase("sair")) {
                    System.out.println("Conexão encerrada.");
                    break;
                }

                String resposta;
                while ((resposta = servidorIn.readLine()) != null) {
                    if (resposta.equals("__FIM__")) break;
                    System.out.println("Resposta do servidor: " + resposta);
                }
            }

        } catch (IOException e) {
            System.out.println("Erro ao se conectar ao servidor: " + e.getMessage());
        }
    }
}
