package usuario;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Usuario implements DadosClimaticos {

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
		try (Socket socketCentro = new Socket(hostCentroDados, portaCentroDados);
				PrintWriter outCentro = new PrintWriter(socketCentro.getOutputStream(), true);
				BufferedReader inCentro = new BufferedReader(new InputStreamReader(socketCentro.getInputStream()))) {
			System.out.print("Identifique-se (nome ou ID): ");
			nome = scanner.nextLine().trim();
			outCentro.println(nome);

			escolherBalanceamento(outCentro);
			String conexao = menuUsuario(outCentro, inCentro);
			if (conexao == null)
				return;

			String[] partes = conexao.split(":");
			String ipServidor = partes[0];
			int portaServidor = Integer.parseInt(partes[1]);

			conectarAoServidor(ipServidor, portaServidor);

		} catch (IOException e) {
			System.out.println("Erro ao se comunicar com o centro de dados: " + e.getMessage());
		}
	}

	private void escolherBalanceamento(PrintWriter outCentro) {
		String tipoBalanceamento;
		do {
			System.out.print("Escolha o algoritmo de balanceamento:\n[1] WLC\n[2] Round-Robin\n> ");
			tipoBalanceamento = scanner.nextLine().trim();
		} while (!tipoBalanceamento.equals("1") && !tipoBalanceamento.equals("2"));

		outCentro.println(tipoBalanceamento);
	}

	private String menuUsuario(PrintWriter outCentro, BufferedReader inCentro) throws IOException {
		String conexao;
		String comando;
		do {
			System.out.print("[1] - Iniciar \n[0] - Sair\n> ");
			comando = scanner.nextLine().trim();
		} while (!comando.equals("1") && !comando.equals("0"));

		outCentro.println(comando);

		conexao = inCentro.readLine();
		if (conexao == null || conexao.contains("Conexão encerrada")) {
			System.out.println("Conexão encerrada.");
			return null;
		}

		if (conexao.contains("Comando inválido")) {
			System.out.println("Comando inválido. Tente novamente.");
		}

		if (!conexao.contains(":")) {
			System.out.println("Endereço do servidor inválido.");
			return null;
		}

		return conexao;
	}

	private void conectarAoServidor(String ip, int porta) {
		try (Socket servidorSocket = new Socket(ip, porta);
			 PrintWriter servidorOut = new PrintWriter(servidorSocket.getOutputStream(), true);
			 BufferedReader servidorIn = new BufferedReader(new InputStreamReader(servidorSocket.getInputStream()))) {

			servidorOut.println(nome);

			System.out.println("Conectado ao servidor da região. Digite 'sair' para encerrar.\n");

			exibirInstrucoesDeBusca();

			while (true) {
				System.out.print("\nDigite uma região ou comando de busca: ");
				String novaEntrada = scanner.nextLine().trim();
				servidorOut.println(novaEntrada);

				if (novaEntrada.equalsIgnoreCase("sair")) {
					System.out.println("Conexão encerrada.");
					break;
				}

				String resposta;
				while ((resposta = servidorIn.readLine()) != null) {
					if (resposta.equals("FIM"))
						break;
					System.out.println("Resposta do servidor: " + resposta);
				}
			}

		} catch (IOException e) {
			System.out.println("Erro ao se conectar ao servidor: " + e.getMessage());
		}
	}
	
	private void exibirInstrucoesDeBusca() {
		System.out.println("=========================================================");
		System.out.println("COMANDOS DISPONÍVEIS:");
		System.out.println("- Buscar por região: NORTE, SUL, LESTE, OESTE");
		System.out.println("- Buscar por intervalo de valores:");
		System.out.println("  Ex: BUSCAR_T:20:30     → temperatura entre 20 e 30");
		System.out.println("      BUSCAR_U:50:70     → umidade entre 50 e 70");
		System.out.println("- Buscar por campo + região:");
		System.out.println("  Ex: BUSCAR_R_SUL:600:900  → radiação entre 600 e 900 no sul");
		System.out.println("      BUSCAR_P_NORTE:900    → pressão acima de 900 no norte");
		System.out.println("- Buscar por valores mínimos (qualquer região):");
		System.out.println("  Ex: BUSCAR_T:25        → temperatura maior ou igual a 25");
		System.out.println("      BUSCAR_P:1000      → pressão maior ou igual a 1000");
		System.out.println();
		System.out.println("LEGENDA DOS CAMPOS:");
		System.out.println("  T = Temperatura  |  U = Umidade");
		System.out.println("  P = Pressão      |  R = Radiação");
		System.out.println("=========================================================");
	}

}
