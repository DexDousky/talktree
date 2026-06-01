/**
 * Servidor do chat TALKTREE (Central da Brigada).
 * Gerencia conexões TCP, canais públicos, mensagens privadas,
 * criação de novos canais e atualização da lista de usuários online.
 * Também envia broadcasts UDP para descoberta automática pelos clientes.
 */

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

    // ========== ESTRUTURAS DE DADOS COMPARTILHADAS ==========
    
    // Mapa que associa o canal de saída (PrintWriter) de cada cliente ao seu nome de usuário
    // Usado para enviar mensagens para clientes específicos e para listar usuários online
    private static Map<PrintWriter, String> clientes = new HashMap<>();
    
    // Mapa que associa o canal de saída de cada cliente ao canal (sala) em que ele está atualmente
    // Os canais podem ser "Canal 1", "Canal 2" ou canais criados dinamicamente
    private static Map<PrintWriter, String> canais = new HashMap<>();
    
    // Lista de todos os canais existentes (públicos e criados dinamicamente)
    // Inicia com dois canais padrão
    private static java.util.List<String> listaCanais = new java.util.ArrayList<>(
        java.util.Arrays.asList("Canal 1", "Canal 2")
    );

    // ================== MÉTODO PRINCIPAL ==================
    public static void main(String[] args) {
        // Configura a saída do console para UTF-8 (evita problemas com acentos)
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
            // ignora falha (não crítica)
        }

        System.out.println("--- CENTRAL DA BRIGADA (SERVIDOR) INICIADA ---");

        // Inicia a thread que envia broadcasts UDP para descoberta automática
        iniciarDiscoveryBroadcast();

        // Cria o socket TCP na porta 12345 e aceita conexões de clientes
        try (ServerSocket servidorSocket = new ServerSocket(12345)) {
            while (true) {
                // Para cada cliente que se conecta, cria uma nova thread para tratá-lo
                new ManipuladorCliente(servidorSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    // ========== DESCOBERTA AUTOMÁTICA (BROADCAST UDP) ==========
    /**
     * Thread que envia continuamente (a cada 2 segundos) um pacote UDP
     * para o endereço de broadcast 255.255.255.255 na porta 8888.
     * O pacote contém a string "TALKTREE_SERVER".
     * Os clientes na mesma rede local escutam essa porta e assim descobrem
     * o IP do servidor automaticamente.
     */
    private static void iniciarDiscoveryBroadcast() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true); // habilita envio de broadcast
                byte[] buffer = "TALKTREE_SERVER".getBytes();
                while (true) {
                    try {
                        // Cria o pacote para o endereço de broadcast na porta 8888
                        DatagramPacket packet = new DatagramPacket(
                            buffer, buffer.length,
                            InetAddress.getByName("255.255.255.255"), 8888
                        );
                        socket.send(packet); // envia o broadcast
                        Thread.sleep(2000);   // espera 2 segundos antes de enviar novamente
                    } catch (Exception e) {
                        Thread.sleep(2000);   // em caso de erro, espera e tenta de novo
                    }
                }
            } catch (Exception e) {
                // Se não conseguir criar o socket, a thread morre silenciosamente
                // (o servidor continua funcionando, mas sem descoberta automática)
            }
        }).start();
    }

    // ========== TRATADOR DE CADA CLIENTE (THREAD INDIVIDUAL) ==========
    private static class ManipuladorCliente extends Thread {
        private Socket socket;          // socket TCP do cliente
        private PrintWriter out;        // stream de saída (envia dados ao cliente)
        private BufferedReader in;      // stream de entrada (recebe dados do cliente)
        private String nomeTorre;       // nome do usuário conectado

        public ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Inicializa os streams com codificação UTF-8 para suportar acentos e caracteres especiais
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // A primeira mensagem do cliente deve ser "LOGIN|nome"
                String login = in.readLine();
                if (login == null || !login.startsWith("LOGIN|")) {
                    socket.close();  // formato inválido, encerra conexão
                    return;
                }
                nomeTorre = login.substring(6); // extrai o nome após "LOGIN|"

                // ========== REGISTRA O CLIENTE NAS ESTRUTURAS COMPARTILHADAS ==========
                synchronized (clientes) {
                    // Adiciona o cliente ao mapa de clientes (associa o PrintWriter ao nome)
                    clientes.put(out, nomeTorre);
                    // Define o canal inicial do cliente como "Canal 1"
                    canais.put(out, "Canal 1");

                    // ENVIA LISTA DE CANAIS EXISTENTES para este cliente
                    StringBuilder sbCanais = new StringBuilder("CANAIS|");
                    synchronized (listaCanais) {
                        for (String c : listaCanais) {
                            sbCanais.append(c).append(",");
                        }
                    }
                    out.println(sbCanais.toString());

                    // ENVIA LISTA DE USUÁRIOS ONLINE para este cliente
                    StringBuilder lista = new StringBuilder("LISTA|");
                    for (PrintWriter escritor : clientes.keySet()) {
                        lista.append(clientes.get(escritor)).append(",");
                    }
                    out.println(lista.toString());

                    // AVISA TODOS OS OUTROS CLIENTES que um novo usuário entrou
                    for (PrintWriter escritor : clientes.keySet()) {
                        if (escritor != out) { // não envia para o próprio cliente (já sabe que entrou)
                            escritor.println("ENTROU|" + nomeTorre);
                        }
                    }
                }

                // ========== LOOP PRINCIPAL: LÊ MENSAGENS DO CLIENTE ==========
                String mensagem;
                while ((mensagem = in.readLine()) != null) {

                    // --- COMANDO: CRIAR NOVO CANAL ---
                    if (mensagem.startsWith("CRIAR_CANAL|")) {
                        String novoC = mensagem.substring(12); // extrai o nome do novo canal
                        synchronized (listaCanais) {
                            if (!listaCanais.contains(novoC)) {
                                listaCanais.add(novoC); // adiciona à lista global de canais
                            }
                        }
                        // Constrói a nova lista de canais para broadcast
                        StringBuilder sbCanais = new StringBuilder("CANAIS|");
                        synchronized (listaCanais) {
                            for (String c : listaCanais) {
                                sbCanais.append(c).append(",");
                            }
                        }
                        String broadcastCanais = sbCanais.toString();
                        // Envia a lista atualizada para TODOS os clientes
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                escritor.println(broadcastCanais);
                            }
                        }
                    }

                    // --- COMANDO: MUDAR DE CANAL ---
                    else if (mensagem.startsWith("MUDAR_CANAL|")) {
                        String novoCanal = mensagem.substring(12); // nome do canal destino
                        String canalAntigo = canais.get(out);      // canal atual do cliente
                        if (novoCanal.equals(canalAntigo)) {
                            continue; // mesmo canal, ignora
                        }
                        synchronized (clientes) {
                            canais.put(out, novoCanal); // atualiza o canal do cliente
                        }
                        // Não envia notificação para os outros (o cliente apenas troca de canal
                        // e continuará recebendo mensagens do novo canal)
                    }

                    // --- COMANDO: ATUALIZAR LISTA DE USUÁRIOS (REFRESH) ---
                    else if (mensagem.equals("REFRESH") || mensagem.equals("ATUALIZAR")) {
                        StringBuilder lista = new StringBuilder("LISTA|");
                        synchronized (clientes) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                lista.append(clientes.get(escritor)).append(",");
                            }
                        }
                        out.println(lista.toString()); // envia só para este cliente
                    }

                    // --- MENSAGEM NORMAL (pode ser texto, sticker, anexo, voz, ou privada) ---
                    else {
                        // A mensagem chega no formato "remetente|conteúdo"
                        // Se for privada, o conteúdo começa com "PV|destinatário|mensagem"
                        String textoRecebido = mensagem;
                        if (mensagem.contains("|")) {
                            // Remove a parte do nome do remetente (já está em nomeTorre)
                            textoRecebido = mensagem.substring(mensagem.indexOf("|") + 1);
                        }

                        // ===== MENSAGEM PRIVADA =====
                        if (textoRecebido.startsWith("PV|")) {
                            String[] partesPv = textoRecebido.split("\\|", 3); // [PV, destinatario, msg]
                            if (partesPv.length == 3) {
                                String destinatario = partesPv[1];
                                String msgPrivada = partesPv[2];
                                // Reconstrói a mensagem no formato que o cliente espera:
                                // "remetente|PV|destinatario|conteudo"
                                String mensagemFinal = nomeTorre + "|PV|" + destinatario + "|" + msgPrivada;

                                // Log no console do servidor (oculta anexos/stickers para não poluir)
                                String logConsole = msgPrivada.startsWith("FILE|") || msgPrivada.startsWith("STICKER|")
                                        ? "[ARQUIVO/STICKER ANEXADO]" : msgPrivada;
                                System.out.println(nomeTorre + " -> " + destinatario + " [PV]: " + logConsole);

                                // Envia a mensagem privada apenas para o remetente e o destinatário
                                synchronized (clientes) {
                                    for (Map.Entry<PrintWriter, String> entrada : clientes.entrySet()) {
                                        if (entrada.getValue().equals(destinatario) || entrada.getValue().equals(nomeTorre)) {
                                            entrada.getKey().println(mensagemFinal);
                                        }
                                    }
                                }
                                continue; // já processou, pula o restante
                            }
                        }

                        // ===== MENSAGEM PÚBLICA (texto, sticker, anexo, voz) =====
                        // Obtém a hora atual para log no servidor
                        LocalTime agora = LocalTime.now();
                        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("HH:mm");
                        String horaFormatada = agora.format(formatador);

                        // Mensagem final no formato "nomeTorre|conteudo"
                        String mensagemFinal = nomeTorre + "|" + textoRecebido;

                        // Log no console
                        String logConsole = (textoRecebido.startsWith("FILE|") || textoRecebido.startsWith("STICKER|"))
                                ? "[ARQUIVO/STICKER ANEXADO]" : textoRecebido;
                        System.out.println(nomeTorre + "|[" + horaFormatada + "] " + logConsole);

                        // Encaminha a mensagem para TODOS os clientes que estão no MESMO CANAL que o remetente
                        synchronized (clientes) {
                            String canalRemetente = canais.get(out);
                            if (canalRemetente != null) { // segurança contra null
                                for (PrintWriter escritor : clientes.keySet()) {
                                    String canalDestino = canais.get(escritor);
                                    if (canalDestino != null && canalDestino.equals(canalRemetente)) {
                                        escritor.println(mensagemFinal);
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (IOException e) {
                // Se a leitura falha, provavelmente o cliente desconectou abruptamente
                System.out.println("Conexão perdida com " + nomeTorre);
            } finally {
                // ========== REMOVE O CLIENTE DAS ESTRUTURAS QUANDO ELE SAI ==========
                if (nomeTorre != null) {
                    synchronized (clientes) {
                        // Remove o cliente dos mapas e obtém o canal em que ele estava
                        String canalAntigo = canais.remove(out);
                        clientes.remove(out);
                        
                        // Notifica os clientes que estão no MESMO CANAL que ele saiu
                        if (canalAntigo != null) {
                            for (PrintWriter escritor : clientes.keySet()) {
                                String canalEscritor = canais.get(escritor);
                                if (canalEscritor != null && canalEscritor.equals(canalAntigo)) {
                                    escritor.println("SAIU|" + nomeTorre);
                                }
                            }
                        }
                    }
                }
                // Fecha o socket do cliente
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignora
                }
            }
        }
    }
}