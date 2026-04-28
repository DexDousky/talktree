import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Servidor Central da Brigada de Incêndio
 * Esse aqui recebe as mensagens das torres e espalha pra todo mundo.
 */
public class Servidor {
    // Lista de quem tá conectado pra gente conseguir mandar mensagem pra geral
    private static Set<PrintWriter> escritores = new HashSet<>();

    public static void main(String[] args) {
        // Força o console a usar UTF-8 pra não quebrar os acentos aqui
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {}

        System.out.println("--- CENTRAL DA BRIGADA (SERVIDOR) INICIADA ---");
        
        // Porta 12345 pq sim
        try (ServerSocket servidorSocket = new ServerSocket(12345)) {
            while (true) {
                // Fica esperando alguém conectar (o rádio de alguma torre)
                new ManipuladorCliente(servidorSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    // Classe interna pra cuidar de cada brigadista que conecta
    private static class ManipuladorCliente extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Adiciona esse novo rádio na nossa lista de transmissão
                synchronized (escritores) {
                    escritores.add(out);
                }

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    System.out.println("Mensagem recebida: " + mensagem);
                    // Manda pra todo mundo que tá online
                    synchronized (escritores) {
                        for (PrintWriter escritor : escritores) {
                            escritor.println(mensagem);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Alguém desconectou.");
            } finally {
                // Se o cara saiu, a gente limpa ele da lista
                if (out != null) {
                    synchronized (escritores) {
                        escritores.remove(out);
                    }
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
