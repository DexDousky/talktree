import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Servidor {
    private static Map<PrintWriter, String> clientes = new HashMap<>();
    private static Map<PrintWriter, String> canais = new HashMap<>();
    private static java.util.List<String> listaCanais = new java.util.ArrayList<>(java.util.Arrays.asList("Canal 1", "Canal 2"));

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
        }

        System.out.println("--- CENTRAL DA BRIGADA (SERVIDOR) INICIADA ---");

        iniciarDiscoveryBroadcast();

        try (ServerSocket servidorSocket = new ServerSocket(12345)) {
            while (true) {
                new ManipuladorCliente(servidorSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    private static void iniciarDiscoveryBroadcast() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                byte[] buffer = "TALKTREE_SERVER".getBytes();
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), 8888);
                        socket.send(packet);
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        Thread.sleep(2000);
                    }
                }
            } catch (Exception e) {
            }
        }).start();
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
                    clientes.put(out, nomeTorre);
                    canais.put(out, "Canal 1");

                    StringBuilder sbCanais = new StringBuilder("CANAIS|");
                    synchronized (listaCanais) {
                        for (String c : listaCanais) {
                            sbCanais.append(c).append(",");
                        }
                    }
                    out.println(sbCanais.toString());

                    StringBuilder lista = new StringBuilder("LISTA|");
                    for (PrintWriter escritor : clientes.keySet()) {
                        lista.append(clientes.get(escritor)).append(",");
                    }
                    out.println(lista.toString());

                    for (PrintWriter escritor : clientes.keySet()) {
                        if (escritor != out) {
                            escritor.println("ENTROU|" + nomeTorre);
                        }
                    }
                }

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.startsWith("CRIAR_CANAL|")) {
                        String novoC = mensagem.substring(12);
                        synchronized (listaCanais) {
                            if (!listaCanais.contains(novoC)) {
                                listaCanais.add(novoC);
                            }
                        }
                        StringBuilder sbCanais = new StringBuilder("CANAIS|");
                        synchronized (listaCanais) {
                            for (String c : listaCanais) {
                                sbCanais.append(c).append(",");
                            }
                        }
                        String broadcastCanais = sbCanais.toString();
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                escritor.println(broadcastCanais);
                            }
                        }
                    } else if (mensagem.startsWith("MUDAR_CANAL|")) {
                        String novoCanal = mensagem.substring(12);
                        String canalAntigo = canais.get(out);

                        if (novoCanal.equals(canalAntigo)) {
                            continue;
                        }

                        synchronized (clientes) {
                            canais.put(out, novoCanal);
                        }
                    } else if (mensagem.equals("REFRESH") || mensagem.equals("ATUALIZAR")) {
                        StringBuilder lista = new StringBuilder("LISTA|");
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                lista.append(clientes.get(escritor)).append(",");
                            }
                        }
                        out.println(lista.toString());
                    } else {
                        String textoRecebido = mensagem;
                        if (mensagem.contains("|")) {
                            textoRecebido = mensagem.substring(mensagem.indexOf("|") + 1);
                        }

                        if (textoRecebido.startsWith("PV|")) {
                            String[] partesPv = textoRecebido.split("\\|", 3);
                            if (partesPv.length == 3) {
                                String destinatario = partesPv[1];
                                String msgPrivada = partesPv[2];
                                String mensagemFinal = nomeTorre + "|PV|" + destinatario + "|" + msgPrivada;
                                String logConsole = msgPrivada.startsWith("FILE|") || msgPrivada.startsWith("STICKER|") ? "[ARQUIVO/STICKER ANEXADO]" : msgPrivada;
                                System.out.println(nomeTorre + " -> " + destinatario + " [PV]: " + logConsole);
                                synchronized (clientes) {
                                    for (Map.Entry<PrintWriter, String> entrada : clientes.entrySet()) {
                                        if (entrada.getValue().equals(destinatario) || entrada.getValue().equals(nomeTorre)) {
                                            entrada.getKey().println(mensagemFinal);
                                        }
                                    }
                                }
                                continue;
                            }
                        }

                        LocalTime agora = LocalTime.now();
                        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("HH:mm");
                        String horaFormatada = agora.format(formatador);

                        String mensagemFinal = nomeTorre + "|" + textoRecebido;

                        String logConsole = (textoRecebido.startsWith("FILE|") || textoRecebido.startsWith("STICKER|")) ? "[ARQUIVO/STICKER ANEXADO]" : textoRecebido;
                        System.out.println(nomeTorre + "|[" + horaFormatada + "] " + logConsole);

                        synchronized (clientes) {
                            String canalRemetente = canais.get(out);
                            for (PrintWriter escritor : clientes.keySet()) {
                                if (canais.get(escritor).equals(canalRemetente)) {
                                    escritor.println(mensagemFinal);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão perdida com " + nomeTorre);
            } finally {
                if (nomeTorre != null) {
                    synchronized (clientes) {
                        String canalAntigo = canais.remove(out);
                        clientes.remove(out);
                        for (PrintWriter escritor : clientes.keySet()) {
                            if (canais.get(escritor).equals(canalAntigo)) {
                                escritor.println("SAIU|" + nomeTorre);
                            }
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
}clientes.remove(out);
                        canais.remove(out);
                        for (PrintWriter escritor : clientes.keySet()) {
                            escritor.println("SAIU|" + nomeTorre);