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
    private static Map<PrintWriter, String> clientes = new HashMap<>();

    public static void main(String[] args) {
        // Forcar UTF-8 pq o Windows é chato com acentos
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
        }

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

                String login = in.readLine();
                if (login == null || !login.startsWith("LOGIN|")) {
                    socket.close();
                    return;
                }
                nomeTorre = login.substring(6);

                synchronized (clientes) {
                    // Manda a lista atual para o novato
                    StringBuilder lista = new StringBuilder("LIST|");
                    for (String nome : clientes.values()) {
                        lista.append(nome).append(",");
                    }
                    out.println(lista.toString());

                    // Avisa todos que um novo usuário entrou
                    for (PrintWriter escritor : clientes.keySet()) {
                        escritor.println("JOIN|" + nomeTorre);
                    }
                    clientes.put(out, nomeTorre);
                }

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    // COMANDO ESPECIAL: REFRESH
                    if (mensagem.equals("REFRESH")) {
                        // Envia a lista atualizada APENAS para este cliente
                        StringBuilder lista = new StringBuilder("LIST|");
                        synchronized (clientes) {
                            for (String nome : clientes.values()) {
                                lista.append(nome).append(",");
                            }
                        }
                        out.println(lista.toString());
                    } else {
                        // Mensagem normal: repassa para todos (formato "nome|texto")
                        System.out.println("[" + nomeTorre + "]: " + mensagem);
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                escritor.println(mensagem);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão perdida com " + nomeTorre);
            } finally {
                if (nomeTorre != null) {
                    synchronized (clientes) {
                        clientes.remove(out);
                        for (PrintWriter escritor : clientes.keySet()) {
                            escritor.println("LEAVE|" + nomeTorre);
                        }
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
}