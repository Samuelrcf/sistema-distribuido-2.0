package service.persistence;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BancoDeDados {

    private final int porta;
    private final File arquivoSaida;
    private final ExecutorService executor = Executors.newCachedThreadPool(); 

    public BancoDeDados(int porta) {
        this.porta = porta;
        this.arquivoSaida = new File("banco_" + porta + ".txt");
    }

    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Banco de dados escutando na porta " + porta);

            while (true) {
                Socket socket = serverSocket.accept();
                executor.submit(() -> lidarComConexao(socket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private void lidarComConexao(Socket socket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String linhaRecebida = in.readLine();

            if (linhaRecebida != null && linhaRecebida.startsWith("CONSULTAR:")) {
                String regiaoDesejada = linhaRecebida.split(":", 2)[1].trim().toUpperCase();
                System.out.println("Consulta para a região: " + regiaoDesejada);
                processarConsulta(out, regiaoDesejada);
            } else if (linhaRecebida != null && !linhaRecebida.isBlank()) {
                salvarDado(linhaRecebida);
                System.out.println("Dado salvo: " + linhaRecebida);
                out.println("Dado recebido e armazenado com sucesso.");
            }

        } catch (IOException e) {
            System.out.println("Erro ao processar conexão: " + e.getMessage());
        }
    }

    private void processarConsulta(PrintWriter out, String regiaoDesejada) {
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoSaida))) {
            String linhaArquivo;
            boolean encontrou = false;

            while ((linhaArquivo = reader.readLine()) != null) {
                if (linhaArquivo.toUpperCase().startsWith("[" + regiaoDesejada + "]")) {
                    System.out.println("Encontrado: " + linhaArquivo);
                    out.println(linhaArquivo);
                    encontrou = true;
                }
            }

            if (!encontrou) {
                out.println("Nenhum dado encontrado para: " + regiaoDesejada);
            }
            out.println("__FIM__");

        } catch (IOException e) {
            System.out.println("Erro ao ler arquivo: " + e.getMessage());
            out.println("Erro ao acessar o banco de dados.");
            out.println("__FIM__");
        }
    }

    private synchronized void salvarDado(String linha) {
        try (FileWriter writer = new FileWriter(arquivoSaida, true);
             BufferedWriter bw = new BufferedWriter(writer)) {
            bw.write(linha);
            bw.newLine();
        } catch (IOException e) {
            System.out.println("Erro ao salvar dado: " + e.getMessage());
        }
    }
}
