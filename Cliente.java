import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

public class Cliente extends Application {

    private WebView areaChat;
    private TextField campoMensagem;
    private ListView<String> listaTorres;
    private ObservableList<String> torresOnline = FXCollections.observableArrayList();

    private PrintWriter out;
    private Socket socket;
    private String nomeUsuario;
    private StringBuilder historicoHtml = new StringBuilder();
    private SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");

    private static final int MAX_HISTORICO = 200;
    private int contadorMensagens = 0;

    @Override
    public void start(Stage palcoPrincipal) {
        this.nomeUsuario = mostrarTelaLogin();
        if (nomeUsuario == null) System.exit(0);

        // --- SIDEBAR ---
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(20));

        HBox headerSidebar = new HBox(10);
        headerSidebar.setAlignment(Pos.CENTER_LEFT);
        Label tituloSidebar = new Label("SINAIS DE RADIO");
        tituloSidebar.getStyleClass().add("titulo-sidebar");
        Button btnRefresh = new Button("⟳");
        btnRefresh.getStyleClass().add("botao-refresh");
        btnRefresh.setOnAction(e -> solicitarRefresh());
        headerSidebar.getChildren().addAll(tituloSidebar, btnRefresh);

        listaTorres = new ListView<>(torresOnline);
        VBox.setVgrow(listaTorres, Priority.ALWAYS);
        sidebar.getChildren().addAll(headerSidebar, listaTorres);

        // --- CHAT ---
        VBox painelChat = new VBox();
        HBox header = new HBox();
        header.getStyleClass().add("chat-header");
        header.setPadding(new Insets(15, 20, 15, 20));
        Label labelCanal = new Label("SINTONIZADO: CANAL_7_NORTE");
        labelCanal.setStyle("-fx-text-fill: #00FF96; -fx-font-family: 'Monospaced'; -fx-font-weight: bold;");
        header.getChildren().add(labelCanal);

        areaChat = new WebView();
        VBox.setVgrow(areaChat, Priority.ALWAYS);
        iniciarHistorico();

        // --- RODAPÉ ---
        HBox rodape = new HBox(10);
        rodape.getStyleClass().add("rodape");
        rodape.setAlignment(Pos.CENTER);
        rodape.setPrefHeight(90);

        campoMensagem = new TextField();
        campoMensagem.setPromptText("Digite sua mensagem...");
        campoMensagem.getStyleClass().add("campo-mensagem");
        HBox.setHgrow(campoMensagem, Priority.ALWAYS);

        Button btnEmoji = new Button("😀");
        btnEmoji.getStyleClass().add("botao-emoji");
        btnEmoji.setPrefWidth(50);
        btnEmoji.setPrefHeight(45);
        btnEmoji.setOnAction(e -> mostrarMenuEmoji());

        Button btnAnexo = new Button("📎");
        btnAnexo.getStyleClass().add("botao-anexo");
        btnAnexo.setPrefWidth(50);
        btnAnexo.setPrefHeight(45);
        btnAnexo.setOnAction(e -> enviarAnexo());

        Button btnTransmitir = new Button("TRANSMITIR");
        btnTransmitir.getStyleClass().add("botao-transmitir");
        btnTransmitir.setPrefHeight(45);
        btnTransmitir.setPrefWidth(130);

        rodape.getChildren().addAll(campoMensagem, btnEmoji, btnAnexo, btnTransmitir);
        painelChat.getChildren().addAll(header, areaChat, rodape);

        BorderPane raiz = new BorderPane();
        raiz.setLeft(sidebar);
        raiz.setCenter(painelChat);

        Scene cena = new Scene(raiz, 1100, 750);
        try {
            cena.getStylesheets().add(getClass().getResource("estilo.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS não encontrado, usando estilo padrão.");
        }

        palcoPrincipal.setTitle("TALKTREE // OPERACOES - " + nomeUsuario);
        palcoPrincipal.setScene(cena);
        palcoPrincipal.show();

        conectarServidor();

        btnTransmitir.setOnAction(e -> enviarMensagem());
        campoMensagem.setOnAction(e -> enviarMensagem());
    }

    @Override
    public void stop() {
        if (out != null) out.close();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { e.printStackTrace(); }
        System.out.println("Cliente desconectado: " + nomeUsuario);
    }

    // ========== HISTÓRICO ==========
    private void iniciarHistorico() {
        historicoHtml = new StringBuilder();
        historicoHtml.append("<html><body style='background-color:#121218; color:#DCDCDC; font-family:Monospaced; font-size:13px; margin:15px;'>");
        historicoHtml.append("<div style='color:#555;'>--- INICIO DA TRANSMISSAO ---</div>");
        contadorMensagens = 0;
        atualizarWebView();
    }

    private void adicionarLinhaHtml(String linhaHtml) {
        if (contadorMensagens >= MAX_HISTORICO) {
            String atual = historicoHtml.toString();
            int primeiroDiv = atual.indexOf("<div");
            if (primeiroDiv != -1) {
                int fimPrimeiro = atual.indexOf("</div>", primeiroDiv);
                if (fimPrimeiro != -1) {
                    historicoHtml = new StringBuilder(atual.substring(0, primeiroDiv) + atual.substring(fimPrimeiro + 6));
                    contadorMensagens--;
                }
            }
        }
        historicoHtml.append(linhaHtml);
        contadorMensagens++;
        atualizarWebView();
    }

    private void atualizarWebView() {
        Platform.runLater(() -> {
            String completo = historicoHtml.toString() + "</body></html>";
            areaChat.getEngine().loadContent(completo);
        });
    }

    // ========== MENSAGENS ==========
    private void adicionarMensagemAoChat(String nome, String texto) {
        String hora = formatter.format(new Date());
        String corNome = nome.equals(nomeUsuario) ? "#FF6E00" : "#00FF96";

        if (texto.startsWith("FILE|")) {
            String[] partes = texto.split("\\|", 4);
            if (partes.length == 4 && partes[0].equals("FILE")) {
                String nomeArquivo = partes[1];
                String tipo = partes[2];
                String base64Data = partes[3];
                String htmlAnexo = gerarHtmlAnexo(nome, nomeArquivo, tipo, base64Data);
                adicionarLinhaHtml(htmlAnexo);
                return;
            }
        }

        String linhaHtml = String.format(
            "<div style='margin-bottom:10px;'><span style='color:#555;'>[%s]</span> <b style='color:%s;'>%s:</b> <span style='color:#eee;'>%s</span></div>",
            hora, corNome, nome, escaparHtml(texto)
        );
        adicionarLinhaHtml(linhaHtml);
    }

    private String gerarHtmlAnexo(String nomeRemetente, String nomeArquivo, String tipo, String base64Data) {
        String hora = formatter.format(new Date());
        String corNome = nomeRemetente.equals(nomeUsuario) ? "#FF6E00" : "#00FF96";
        String mediaHtml = "";

        String extensao = "";
        int i = nomeArquivo.lastIndexOf('.');
        if (i > 0) extensao = nomeArquivo.substring(i + 1).toLowerCase();

        if (tipo.equals("image")) {
            String mimeImg = extensao.equals("gif") ? "gif" : "png";
            if (extensao.equals("jpg") || extensao.equals("jpeg")) mimeImg = "jpeg";
            mediaHtml = String.format(
                "<div><img src='data:image/%s;base64,%s' style='max-width:250px; max-height:200px; border-radius:8px; margin-top:5px;'/></div>",
                mimeImg, base64Data
            );
        } else if (tipo.equals("video")) {
            String mimeVideo = "video/mp4";
            if (extensao.equals("avi")) mimeVideo = "video/x-msvideo";
            else if (extensao.equals("mov")) mimeVideo = "video/quicktime";
            else if (extensao.equals("webm")) mimeVideo = "video/webm";
            else if (extensao.equals("ogg")) mimeVideo = "video/ogg";
            mediaHtml = String.format(
                "<div><video controls style='max-width:250px; max-height:200px; border-radius:8px;'>" +
                "<source src='data:%s;base64,%s' type='%s'>" +
                "Seu navegador não suporta vídeo.</video></div>",
                mimeVideo, base64Data, mimeVideo
            );
        } else {
            mediaHtml = "<div>[Arquivo não suportado]</div>";
        }

        return String.format(
            "<div style='margin-bottom:15px;'>" +
            "<span style='color:#555;'>[%s]</span> <b style='color:%s;'>%s</b> enviou <i>%s</i><br/>" +
            "%s" +
            "</div>",
            hora, corNome, nomeRemetente, nomeArquivo, mediaHtml
        );
    }

    private String escaparHtml(String texto) {
        return texto.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }

    // ========== ENVIO DE ARQUIVO ==========
    private void enviarAnexo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar arquivo (imagem, GIF ou vídeo)");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("Vídeos", "*.mp4", "*.avi", "*.mov"),
            new FileChooser.ExtensionFilter("Todos os arquivos", "*.*")
        );
        File arquivo = fileChooser.showOpenDialog(null);
        if (arquivo == null) return;

        if (arquivo.length() > 5 * 1024 * 1024) {
            mostrarAlerta("Arquivo muito grande", "Máximo permitido: 5 MB");
            return;
        }

        String nomeArquivo = arquivo.getName();
        String tipo = nomeArquivo.matches(".*\\.(png|jpg|jpeg|gif|bmp)$") ? "image" : "video";

        try {
            byte[] bytes = new byte[(int) arquivo.length()];
            try (FileInputStream fis = new FileInputStream(arquivo)) {
                fis.read(bytes);
            }
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mensagem = "FILE|" + nomeArquivo + "|" + tipo + "|" + base64;
            out.println(nomeUsuario + "|" + mensagem);
        } catch (IOException e) {
            mostrarAlerta("Erro", "Não foi possível ler o arquivo.");
        }
    }

    // ========== EMOJIS ==========
    private void mostrarMenuEmoji() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("😀", "😀", "😂", "😍", "😎", "😢", "🔥", "👍", "❤️", "🎉");
        dialog.setTitle("Emojis");
        dialog.setHeaderText("Escolha um emoji para inserir");
        dialog.setContentText("Emoji:");
        dialog.showAndWait().ifPresent(emoji -> {
            int pos = campoMensagem.getCaretPosition();
            String texto = campoMensagem.getText();
            campoMensagem.setText(texto.substring(0, pos) + emoji + texto.substring(pos));
            campoMensagem.positionCaret(pos + emoji.length());
        });
    }

    // ========== REFRESH ==========
    private void solicitarRefresh() {
        if (out != null) out.println("REFRESH");
    }

    // ========== CONEXÃO COM SERVIDOR ==========
    private void conectarServidor() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("LOGIN|" + nomeUsuario);

                String linha;
                while ((linha = in.readLine()) != null) {
                    if (linha.startsWith("LIST|")) {
                        String[] nomes = linha.substring(5).split(",");
                        Platform.runLater(() -> {
                            torresOnline.clear();
                            for (String n : nomes) if (!n.isEmpty())
                                torresOnline.add("⛰️ " + n + (n.equals(nomeUsuario) ? " [VOCÊ]" : ""));
                        });
                    } else if (linha.startsWith("JOIN|")) {
                        String n = linha.substring(5);
                        Platform.runLater(() -> {
                            if (!torresOnline.contains("⛰️ " + n)) torresOnline.add("⛰️ " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " entrou na frequência.");
                        });
                    } else if (linha.startsWith("LEAVE|")) {
                        String n = linha.substring(6);
                        Platform.runLater(() -> {
                            torresOnline.remove("⛰️ " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " saiu da frequência.");
                        });
                    } else {
                        String[] partes = linha.split("\\|", 2);
                        if (partes.length == 2) adicionarMensagemAoChat(partes[0], partes[1]);
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> adicionarMensagemAoChat("ERRO", "Conexão perdida com a central."));
            }
        }).start();
    }

    private void enviarMensagem() {
        String msg = campoMensagem.getText().trim();
        if (!msg.isEmpty() && out != null) {
            out.println(nomeUsuario + "|" + msg);
            campoMensagem.clear();
        }
    }

    private String mostrarTelaLogin() {
        TextInputDialog login = new TextInputDialog();
        login.setTitle("AUTENTICAÇÃO");
        login.setHeaderText("CENTRAL DE COMANDO TALKTREE");
        login.setContentText("IDENTIFIQUE SUA TORRE:");
        return login.showAndWait().orElse(null);
    }

    private void mostrarAlerta(String titulo, String conteudo) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(conteudo);
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}