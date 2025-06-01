package service;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {
    private final String MULTICAST_IP = "239.0.0.1";
    private final int MULTICAST_PORT = 4446;

    private final String HOST_BD = "localhost";
    private final int PORTA_BD;

    private final int portaUsuarios;
    private final int peso;
    private final AtomicInteger conexoesAtivas = new AtomicInteger(0);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Servidor(int portaBD, int portaUsuarios, int peso) {
        this.PORTA_BD = portaBD;
        this.portaUsuarios = portaUsuarios;
        this.peso = peso;
    }

    public void iniciar() {
        escutarMulticast();
        escutarUsuarios();
    }

    private void escutarMulticast() {
        executor.execute(() -> {
            try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_IP);
                NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), ni);

                byte[] buf = new byte[512];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String dado = new String(packet.getData(), 0, packet.getLength());
                    if (dado.equals("STATUS?")) {
                        responderStatus();
                    } else {
                        processarDado(dado);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void responderStatus() {
        try (Socket socket = new Socket("localhost", 5500);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String resposta = "localhost:" + portaUsuarios + ";" + conexoesAtivas.get() + ";" + peso;
            out.println(resposta);
            System.out.println("Status enviado: " + resposta);
        } catch (IOException e) {
            System.out.println("Erro ao enviar status: " + e.getMessage());
        }
    }

    private void escutarUsuarios() {
        executor.execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(portaUsuarios)) {
                System.out.println("Servidor escutando usuários na porta " + portaUsuarios);
                while (true) {
                    Socket socket = serverSocket.accept();
                    executor.execute(() -> lidarComUsuario(socket));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void lidarComUsuario(Socket socketUsuario) {
        conexoesAtivas.incrementAndGet();
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socketUsuario.getInputStream()));
            PrintWriter out = new PrintWriter(socketUsuario.getOutputStream(), true)
        ) {
            String identificador = in.readLine();
            if (identificador == null || identificador.trim().isEmpty()) {
                identificador = "Desconhecido_" + socketUsuario.getInetAddress().getHostAddress();
            }

            String entrada;
            while ((entrada = in.readLine()) != null) {
                if (entrada.equalsIgnoreCase("sair")) {
                    out.println("Conexão encerrada pelo cliente.");
                    registrarLog("[" + identificador + "] encerrou a conexão.");
                    break;
                }

                String regiao = entrada.trim().toUpperCase();
                registrarLog("[" + identificador + "] solicitou dados da região: " + regiao);

                try (
                    Socket socketBD = new Socket(HOST_BD, PORTA_BD);
                    PrintWriter outBD = new PrintWriter(socketBD.getOutputStream(), true);
                    BufferedReader inBD = new BufferedReader(new InputStreamReader(socketBD.getInputStream()))
                ) {
                    outBD.println("CONSULTAR:" + regiao);

                    boolean encontrou = false;
                    String linha;
                    while ((linha = inBD.readLine()) != null) {
                        if (linha.equals("__FIM__")) break;
                        out.println(linha);
                        out.flush();
                        encontrou = true;
                    }

                    if (!encontrou) {
                        String msg = "Nenhum dado encontrado para a região: " + regiao;
                        out.println(msg);
                        registrarLog("[" + identificador + "] " + msg);
                    } else {
                        registrarLog("[" + identificador + "] recebeu dados da região: " + regiao);
                    }

                    out.println("__FIM__");
                    out.flush();

                } catch (IOException e) {
                    String erro = "Erro ao consultar dados no BD: " + e.getMessage();
                    registrarLog("[" + identificador + "] " + erro);
                    out.println("Erro ao consultar dados no banco de dados.");
                }
            }

        } catch (IOException e) {
            registrarLog("Erro na comunicação com o usuário: " + e.getMessage());
            e.printStackTrace();
        } finally {
            conexoesAtivas.decrementAndGet();
        }
    }


    private void processarDado(String dado) {
        System.out.println("Processando dado: " + dado);
        executor.execute(() -> {
            try {
                Thread.sleep(500); // simula tempo de processamento
                String[] campos = parseDados(dado);
                String regiao = identificarRegiao(dado);
                String formatado = formatarParaBase(campos, regiao);
                enviarParaBD(formatado);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void enviarParaBD(String dadoFormatado) {
        try (Socket socket = new Socket(HOST_BD, PORTA_BD);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(dadoFormatado);
        } catch (IOException e) {
            System.out.println("Erro ao enviar para BD: " + e.getMessage());
        }
    }

    private String[] parseDados(String dados) {
        if (dados.contains("_")) return dados.split("_");
        if (dados.contains(";")) return dados.replace("(", "").replace(")", "").split(";");
        if (dados.contains(",")) return dados.replace("{", "").replace("}", "").split(",");
        return dados.split("#");
    }

    private String identificarRegiao(String dados) {
        if (dados.startsWith("{")) return "LESTE";
        if (dados.startsWith("(")) return "SUL";
        if (dados.contains("_")) return "NORTE";
        if (dados.contains("#")) return "OESTE";
        return "DESCONHECIDA";
    }

    private String formatarParaBase(String[] dados, String regiao) {
        return "[" + regiao + "] [" + dados[2] + "//" + dados[3] + "//" + dados[0] + "//" + dados[1] + "]";
    }
    
    private void registrarLog(String mensagem) {
        try {
            String nomeArquivo = "logs/servidor.log";
            try (FileWriter fw = new FileWriter(nomeArquivo, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                String timestamp = java.time.LocalDateTime.now().toString();
                out.println("[" + timestamp + "] " + mensagem);
            }
        } catch (IOException e) {
            System.out.println("Erro ao registrar log: " + e.getMessage());
        }
    }

}
