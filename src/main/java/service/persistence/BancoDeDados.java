package service.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BancoDeDados {

    private final int porta;
    private final File arquivoSaida;
    private final ExecutorService executor = Executors.newCachedThreadPool(); 
    private final BlockingQueue<String> buffer = new LinkedBlockingQueue<>();
    private final int BUFFER_FLUSH_INTERVAL_MS = 5000; // flush a cada 5 segundos
    private final Thread persistenciaThread;
    private volatile boolean executando = true;

    public BancoDeDados(int porta) {
        this.porta = porta;
        this.arquivoSaida = new File("banco_" + porta + ".txt");
        persistenciaThread = new Thread(() -> persistirBuffer());
        persistenciaThread.start();
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
        }finally {
            executando = false;
            persistenciaThread.interrupt();
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
                out.println("Dado recebido e armazenado com sucesso.");
            }

        } catch (IOException e) {
            System.out.println("Erro ao processar conexão: " + e.getMessage());
        }
    }

    private void processarConsulta(PrintWriter out, String consulta) {
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoSaida))) {
            String linhaArquivo;
            boolean encontrou = false;

            while ((linhaArquivo = reader.readLine()) != null) {
                String linhaUpper = linhaArquivo.toUpperCase();

                if (consulta.startsWith("BUSCAR_")) { //buscas personalizadas
                    if (verificarFiltro(consulta, linhaUpper)) {
                        out.println(linhaArquivo);
                        encontrou = true;
                    }
                } else {
                    if (linhaUpper.startsWith("[" + consulta + "]")) { //busca só usando região
                        out.println(linhaArquivo);
                        encontrou = true;
                    }
                }
            }

            if (!encontrou) {
                out.println("Nenhum dado encontrado para: " + consulta);
            }
            out.println("FIM");

        } catch (IOException e) {
            System.out.println("Erro ao ler arquivo: " + e.getMessage());
            out.println("Erro ao acessar o banco de dados.");
            out.println("FIM");
        }
    }
    
    private boolean verificarFiltro(String consulta, String linha) {
        Map<String, Integer> indices = Map.of(
            "T", 0, "U", 1, "P", 2, "R", 3
        );

        Pattern padrao = Pattern.compile("BUSCAR_([TUPR])(_([A-Z]+))?:([\\d.]+)(:([\\d.]+))?");
        Matcher matcher = padrao.matcher(consulta);

        if (matcher.matches()) {
            String campo = matcher.group(1);
            String regiaoFiltro = matcher.group(3);
            double min = Double.parseDouble(matcher.group(4));
            double max = matcher.group(6) != null ? Double.parseDouble(matcher.group(6)) : Double.MAX_VALUE;

            String[] partes = linha.split("]");
            String regiao = partes[0].replace("[", "").trim();
            String[] valores = partes[1].trim().replace("[", "").replace("]", "").split("//");

            if (regiaoFiltro != null && !regiao.equalsIgnoreCase(regiaoFiltro)) {
                return false;
            }

            int index = indices.getOrDefault(campo, -1);
            if (index == -1 || index >= valores.length) return false;

            try {
                double valorCampo = Double.parseDouble(valores[index]);
                return valorCampo >= min && valorCampo <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    private void salvarDado(String linha) {
        buffer.offer(linha);
    }
    
    private void persistirBuffer() {
        while (executando) {
            try {
                Thread.sleep(BUFFER_FLUSH_INTERVAL_MS);
                flushBufferParaArquivo();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private synchronized void flushBufferParaArquivo() {
        try (FileWriter writer = new FileWriter(arquivoSaida, true);
            BufferedWriter bw = new BufferedWriter(writer)) {
            String dado;
            while ((dado = buffer.poll()) != null) {
                bw.write(dado);
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Erro ao salvar dados do buffer: " + e.getMessage());
        }
    }


}
