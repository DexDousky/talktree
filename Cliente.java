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

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TALKTREE - Radio de Brigada Florestal (Versao JavaFX)
 * Tivemos que usar JavaFX pq o Swing tava feio demais pqp
 */
public class Cliente extends Application {

    private WebView areaChat;     
    private TextField campoMensagem; 
    private ListView<String> listaTorres; 
    private ObservableList<String> torresOnline = FXCollections.observableArrayList();
    
    private PrintWriter out;
    private String nomeUsuario;
    // Comeca o HTML do chat com um estilo escuro
    private String historicoHtml = "<html><body style='background-color:#121218; color:#DCDCDC; font-family:Monospaced; font-size:13px; margin:15px;'>";
    private SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");

    @Override
    public void start(Stage palcoPrincipal) {
        // Pede o nome antes de abrir a central
        this.nomeUsuario = mostrarTelaLogin();
        if (nomeUsuario == null) System.exit(0);

        // SIDEBAR
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(20));

        Label tituloSidebar = new Label("SINAIS DE RADIO");
        tituloSidebar.getStyleClass().add("titulo-sidebar");

        listaTorres = new ListView<>(torresOnline);
        VBox.setVgrow(listaTorres, Priority.ALWAYS);
        sidebar.getChildren().addAll(tituloSidebar, listaTorres);

        // --- MONTAGEM DO CHAT ---
        VBox painelChat = new VBox();
        HBox header = new HBox();
        header.getStyleClass().add("chat-header");
        header.setPadding(new Insets(15, 20, 15, 20));
        Label labelCanal = new Label("SINTONIZADO: CANAL_7_NORTE");
        labelCanal.setStyle("-fx-text-fill: #00FF96; -fx-font-family: 'Monospaced'; -fx-font-weight: bold;");
        header.getChildren().add(labelCanal);

        areaChat = new WebView(); // Usando webview pra aceitar as cores do HTML
        VBox.setVgrow(areaChat, Priority.ALWAYS);
        areaChat.getEngine().loadContent(historicoHtml + "<div style='color:#555;'>--- INICIO DA TRANSMISSAO ---</div></body></html>");

        // --- RODAPE E INPUT ---
        HBox rodape = new HBox(15);
        rodape.getStyleClass().add("rodape");
        rodape.setAlignment(Pos.CENTER);
        rodape.setPrefHeight(90);

        campoMensagem = new TextField();
        campoMensagem.setPromptText("Transmita sua mensagem...");
        campoMensagem.getStyleClass().add("campo-mensagem");
        HBox.setHgrow(campoMensagem, Priority.ALWAYS);

        Button btnTransmitir = new Button("TRANSMITIR");
        btnTransmitir.getStyleClass().add("botao-transmitir");
        btnTransmitir.setPrefHeight(45);
        btnTransmitir.setPrefWidth(150);

        rodape.getChildren().addAll(campoMensagem, btnTransmitir);
        painelChat.getChildren().addAll(header, areaChat, rodape);

        BorderPane raiz = new BorderPane();
        raiz.setLeft(sidebar);
        raiz.setCenter(painelChat);

        Scene cena = new Scene(raiz, 1100, 750);
        try {
            // Tenta puxar o CSS pra nao ficar o visual padrao do java
            cena.getStylesheets().add(getClass().getResource("estilo.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Deu erro ao carregar o CSS, mas segue o baile");
        }

        palcoPrincipal.setTitle("TALKTREE // OPERACOES - " + nomeUsuario);
        palcoPrincipal.setScene(cena);
        palcoPrincipal.show();

        conectarServidor();

        // Faz o botao e o Enter funcionarem
        btnTransmitir.setOnAction(e -> enviarMensagem());
        campoMensagem.setOnAction(e -> enviarMensagem());
    }

    private String mostrarTelaLogin() {
        TextInputDialog login = new TextInputDialog();
        login.setTitle("AUTENTICACAO");
        login.setHeaderText("CENTRAL DE COMANDO TALKTREE");
        login.setContentText("IDENTIFIQUE SUA TORRE:");
        return login.showAndWait().orElse(null);
    }

    private void enviarMensagem() {
        String msg = campoMensagem.getText().trim();
        if (!msg.isEmpty() && out != null) {
            // Manda pro servidor no formato nome/texto
            out.println(nomeUsuario + "|" + msg);
            campoMensagem.clear();
        }
    }

    private void adicionarMensagemAoChat(String nome, String texto) {
        String hora = formatter.format(new Date());
        String corNome = nome.equals(nomeUsuario) ? "#FF6E00" : "#00FF96";
        
        // Criar a linha em HTML pra ficar melhor que aquela bagaça de antes
        String novaLinha = String.format("<div style='margin-bottom:10px;'><span style='color:#555;'>[%s]</span> <b style='color:%s;'>%s:</b> <span style='color:#eee;'>%s</span></div>", hora, corNome, nome, texto);
        historicoHtml += novaLinha;
        
        // WebView rodando na thread da interface
        Platform.runLater(() -> areaChat.getEngine().loadContent(historicoHtml + "</body></html>"));
    }

    private void conectarServidor() {
        new Thread(() -> {
            try {
                Socket socket = new Socket("localhost", 12345);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Primeiro comando tem que ser o LOGIN
                out.println("LOGIN|" + nomeUsuario);

                String linha;
                while ((linha = in.readLine()) != null) {
                    if (linha.startsWith("LIST|")) {
                        String[] nomes = linha.substring(5).split(",");
                        Platform.runLater(() -> {
                            torresOnline.clear();
                            for (String n : nomes) if (!n.isEmpty()) torresOnline.add("o " + n + (n.equals(nomeUsuario) ? " [VOCE]" : ""));
                        });
                    } else if (linha.startsWith("JOIN|")) {
                        String n = linha.substring(5);
                        Platform.runLater(() -> {
                            if (!torresOnline.contains("o " + n)) torresOnline.add("o " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " entrou.");
                        });
                    } else if (linha.startsWith("LEAVE|")) {
                        String n = linha.substring(6);
                        Platform.runLater(() -> {
                            torresOnline.remove("o " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " saiu.");
                        });
                    } else {
                        // Se cair aqui e mensagem normal
                        String[] partes = linha.split("\\|", 2);
                        if (partes.length == 2) adicionarMensagemAoChat(partes[0], partes[1]);
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> adicionarMensagemAoChat("ERRO", "Central offline."));
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
