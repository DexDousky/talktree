import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Servidor {
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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String login = in.readLine();
                if (login == null || !login.startsWith("LOGIN|")) {
                    socket.close();
                    return;
                }
                nomeTorre = login.substring(6);

                synchronized (clientes) {
                    StringBuilder lista = new StringBuilder("LISTA|");
                    for (String nome : clientes.values()) {
                        lista.append(nome).append(",");
                    }
                    out.println(lista.toString());

                    for (PrintWriter escritor : clientes.keySet()) {
                        escritor.println("ENTROU|" + nomeTorre);
                    }
                    clientes.put(out, nomeTorre);
                }

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.equals("REFRESH") || mensagem.equals("ATUALIZAR")) {
                        StringBuilder lista = new StringBuilder("LISTA|");
                        synchronized (clientes) {
                            for (String nome : clientes.values()) {
                                lista.append(nome).append(",");
                            }
                        }
                        out.println(lista.toString());
                    } else {
                        String textoRecebido = mensagem;
                        if (mensagem.contains("|")) {
                            textoRecebido = mensagem.substring(mensagem.indexOf("|") + 1);
                        }

                        LocalTime agora = LocalTime.now();
                        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("HH:mm");
                        String horaFormatada = agora.format(formatador);

                        String mensagemFinal = nomeTorre + "|[" + horaFormatada + "] " + textoRecebido;
                        
                        System.out.println(mensagemFinal);
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                escritor.println(mensagemFinal);
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
                            escritor.println("SAIU|" + nomeTorre);
                        }
                    }
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}