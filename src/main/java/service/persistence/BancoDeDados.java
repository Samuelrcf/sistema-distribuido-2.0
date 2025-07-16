package service.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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
		} finally {
			executando = false;
			persistenciaThread.interrupt();
			executor.shutdown();
		}
	}

	private void lidarComConexao(Socket socket) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
			String linhaRecebida = in.readLine();

			if (linhaRecebida != null && !linhaRecebida.isBlank()) {
				salvarDado(linhaRecebida);
				out.println("Dado recebido e armazenado com sucesso.");
			}

		} catch (IOException e) {
			System.out.println("Erro ao processar conexão: " + e.getMessage());
		}
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
		try (FileWriter writer = new FileWriter(arquivoSaida, true); BufferedWriter bw = new BufferedWriter(writer)) {
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
