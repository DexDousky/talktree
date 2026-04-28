import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Servidor {
    // Mapa que associa cada PrintWriter ao nome da torre
    private static Map<PrintWriter, String> clientes = new HashMap<>();

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {}

        System.out.println("--- CENTRAL DA BRIGADA (SERVIDOR) INICIADA ---");

        try (ServerSocket servidorSocket = new ServerSocket(12345)) {
            while (true) {
                new ManipuladorCliente(servidorSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

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

                // Lê o login no formato original: LOGIN|nome
                String login = in.readLine();
                if (login == null || !login.startsWith("LOGIN|")) {
                    socket.close();
                    return;
                }
                nomeTorre = login.substring(6);

                synchronized (clientes) {
                    // Envia a lista completa de torres para o recém-chegado
                    StringBuilder lista = new StringBuilder("LIST|");
                    for (String nome : clientes.values()) {
                        lista.append(nome).append(",");
                    }
                    out.println(lista.toString());

                    // Avisa os demais que uma nova torre entrou
                    for (PrintWriter escritor : clientes.keySet()) {
                        escritor.println("JOIN|" + nomeTorre);
                    }

                    // Adiciona o novo cliente
                    clientes.put(out, nomeTorre);
                }

                // Loop principal de mensagens
                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    // O cliente envia no formato original: nome|texto
                    System.out.println("[" + nomeTorre + "]: " + mensagem);
                    synchronized (clientes) {
                        for (PrintWriter escritor : clientes.keySet()) {
                            escritor.println(mensagem); // ecoa exatamente como recebeu
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão perdida com " + nomeTorre);
            } finally {
                // Remove a torre ao desconectar e avisa os outros
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