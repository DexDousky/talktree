import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * SERVIDOR DA CENTRAL DE BRIGADA
 * Basicamente o cerebro que gerencia as torres na mata.
 */
public class Servidor {
    // Mapa que associa cada radio ao nome da torre
    // Pra gente saber quem é quem na frequencia
    private static Map<PrintWriter, String> clientes = new HashMap<>();

    public static void main(String[] args) {
        // Forcar UTF-8 pq o Windows e chato com acentos
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
            
        }

        System.out.println("--- CENTRAL DA BRIGADA (SERVIDOR) INICIADA ---");

        // Porta 12345 pq e facil de decorar
        try (ServerSocket servidorSocket = new ServerSocket(12345)) {
            while (true) {
                // Fica esperando algum brigadista sintonizar
                new ManipuladorCliente(servidorSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    // Essa classe cuida de cada radio individualmente pra nao travar tudo
    private static class ManipuladorCliente extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nomeTorre;

        public ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // O radio tem que mandar LOGIN/nome logo que liga
                String login = in.readLine();
                if (login == null || !login.startsWith("LOGIN|")) {
                    socket.close();
                    return;
                }
                nomeTorre = login.substring(6);

                synchronized (clientes) {
                    // Manda a lista de quem ja ta na mata pro novato
                    StringBuilder lista = new StringBuilder("LIST|");
                    for (String nome : clientes.values()) {
                        lista.append(nome).append(",");
                    }
                    out.println(lista.toString());

                    // Avisa a geral que chegou mais outro radio na rede
                    for (PrintWriter escritor : clientes.keySet()) {
                        escritor.println("JOIN|" + nomeTorre);
                    }

                    // Guarda o radio no nosso mapa
                    clientes.put(out, nomeTorre);
                }

                // Loop principal pra ficar ouvindo as mensagens
                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    // O radio envia no formato: nome|texto
                    System.out.println("[" + nomeTorre + "]: " + mensagem);
                    synchronized (clientes) {
                        for (PrintWriter escritor : clientes.keySet()) {
                            escritor.println(mensagem); // Repassa pra todo mundo
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conex�o perdida com " + nomeTorre);
            } finally {
                // Se o brigadista sumir, limpa ele da lista e avisa os outros
                if (nomeTorre != null) {
                    synchronized (clientes) {
                        clientes.remove(out);
                        for (PrintWriter escritor : clientes.keySet()) {
                            escritor.println("LEAVE|" + nomeTorre);
                        }
                    }
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
