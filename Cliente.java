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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

// Novos imports para áudio
import javax.sound.sampled.*;

public class Cliente extends Application {
    private double xOffset = 0;
    private double yOffset = 0;
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
    private static final String[] STICKERS = {
            "...sei la", "2019", "L.", "L.K.", "amor", "anjo", "choro", "demonio",
            "envergonhado", "flerte", "fome", "frisk", "nerd", "piscando", "raiva",
            "rindo", "sono", "sorrindo", "sou foda", "triste"
    };

    private File pastaTemp; // para salvar vídeos e áudios temporariamente
    private ComboBox<String> comboCanais;
    private String canalAtual = "Canal 1";
    private java.util.Map<String, StringBuilder> historicosCanais = new java.util.HashMap<>();
    private java.util.Map<String, Integer> contadoresCanais = new java.util.HashMap<>();

    // Novos campos para gravação de voz
    private Button btnMicrofone;
    private boolean gravando = false;
    private TargetDataLine linhaAudio;
    private ByteArrayOutputStream audioBuffer;
    private Timer timerGravacao;
    private static final int MAX_GRAVACAO_SEGUNDOS = 30;

    @Override
    public void start(Stage palcoPrincipal) {
        palcoPrincipal.initStyle(javafx.stage.StageStyle.UNDECORATED);

        // Cria pasta temporária para vídeos e áudios
        pastaTemp = new File(System.getProperty("java.io.tmpdir"), "talktree_videos");
        if (!pastaTemp.exists())
            pastaTemp.mkdirs();

        this.nomeUsuario = mostrarTelaLogin();
        if (nomeUsuario == null)
            System.exit(0);

        // --- SIDEBAR ---
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("barra-lateral");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(20));

        HBox headerSidebar = new HBox(10);
        headerSidebar.setAlignment(Pos.CENTER_LEFT);
        Label tituloSidebar = new Label("SINAIS DE RADIO");
        tituloSidebar.getStyleClass().add("titulo-barra-lateral");
        Button btnRefresh = new Button("⟳");
        btnRefresh.getStyleClass().add("botao-refresh");
        btnRefresh.setOnAction(e -> solicitarRefresh());
        headerSidebar.getChildren().addAll(tituloSidebar, btnRefresh);

        listaTorres = new ListView<>(torresOnline);
        listaTorres.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.replace("🟢 ", ""));
                    javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4,
                            javafx.scene.paint.Color.web("#55C595"));
                    setGraphic(dot);
                    setGraphicTextGap(10);
                }
            }
        });
        listaTorres.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selecionado = listaTorres.getSelectionModel().getSelectedItem();
                if (selecionado != null) {
                    String nomeOutro = selecionado.replace("🟢 ", "").replace(" [VOCÊ]", "").trim();
                    if (!nomeOutro.equals(nomeUsuario)) {
                        iniciarPvCom(nomeOutro);
                    }
                }
            }
        });
        VBox.setVgrow(listaTorres, Priority.ALWAYS);
        sidebar.getChildren().addAll(headerSidebar, listaTorres);

        // --- CHAT ---
        VBox painelChat = new VBox();
        HBox header = new HBox(15);
        header.getStyleClass().add("cabecalho-chat");
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setAlignment(Pos.CENTER_LEFT);

        Label labelCanal = new Label("SINTONIZADO:");
        labelCanal.setStyle("-fx-text-fill: #A0A0AA; -fx-font-family: 'Monospaced'; -fx-font-weight: bold;");

        comboCanais = new ComboBox<>();
        comboCanais.getItems().addAll("Canal 1", "Canal 2");
        comboCanais.setValue("Canal 1");
        comboCanais.getStyleClass().add("combo-canais");
        comboCanais.setPrefWidth(200);
        comboCanais.setOnAction(e -> mudarCanalSelecionado());

        Button btnCriar = new Button("+");
        btnCriar.getStyleClass().add("botao-criar-canal");
        btnCriar.setOnAction(e -> criarNovoCanal());

        header.getChildren().addAll(labelCanal, comboCanais, btnCriar);

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
        btnEmoji.setOnAction(e -> mostrarMenuEmoji(btnEmoji));

        Button btnAnexo = new Button("📎");
        btnAnexo.getStyleClass().add("botao-anexo");
        btnAnexo.setPrefWidth(50);
        btnAnexo.setPrefHeight(45);
        btnAnexo.setOnAction(e -> enviarAnexo());

        // Botão de microfone (gravação de voz)
        btnMicrofone = new Button("🎙️");
        btnMicrofone.getStyleClass().add("botao-microfone");
        btnMicrofone.setPrefWidth(50);
        btnMicrofone.setPrefHeight(45);
        configurarGravacaoVoz();

        Button btnTransmitir = new Button("TRANSMITIR");
        btnTransmitir.getStyleClass().add("botao-transmitir");
        btnTransmitir.setPrefHeight(45);
        btnTransmitir.setPrefWidth(130);

        rodape.getChildren().addAll(campoMensagem, btnEmoji, btnAnexo, btnMicrofone, btnTransmitir);
        painelChat.getChildren().addAll(header, areaChat, rodape);

        // --- BARRA DE TÍTULO SIMPLES ---
        HBox barraTitulo = new HBox();
        barraTitulo.getStyleClass().add("barra-titulo-customizada");
        barraTitulo.setAlignment(Pos.CENTER_LEFT);
        barraTitulo.setPrefHeight(35);
        barraTitulo.setPadding(new Insets(0, 15, 0, 15));

        Label labelTitulo = new Label("TALKTREE // " + nomeUsuario.toUpperCase());
        labelTitulo.getStyleClass().add("titulo-app-customizado");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnMinimizar = new Button("—");
        btnMinimizar.getStyleClass().add("botao-controle-janela");
        btnMinimizar.setOnAction(e -> palcoPrincipal.setIconified(true));

        Button btnFechar = new Button("✕");
        btnFechar.getStyleClass().add("botao-controle-janela-fechar");
        btnFechar.setOnAction(e -> {
            Platform.exit();
            System.exit(0);
        });

        barraTitulo.getChildren().addAll(labelTitulo, spacer, btnMinimizar, btnFechar);

        barraTitulo.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        barraTitulo.setOnMouseDragged(e -> {
            palcoPrincipal.setX(e.getScreenX() - xOffset);
            palcoPrincipal.setY(e.getScreenY() - yOffset);
        });

        BorderPane raiz = new BorderPane();
        raiz.setTop(barraTitulo);
        raiz.setLeft(sidebar);
        raiz.setCenter(painelChat);

        Scene cena = new Scene(raiz, 1100, 750);

        // --- CARREGAMENTO DO CSS ---
        try {
            String cssUrl = getClass().getResource("estilo.css").toExternalForm();
            cena.getStylesheets().add(cssUrl);
            System.out.println("CSS carregado: " + cssUrl);
        } catch (Exception e) {
            System.out.println("Erro ao carregar CSS: " + e.getMessage());
            try {
                File cssFile = new File("estilo.css");
                if (cssFile.exists()) {
                    cena.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
                    System.out.println("CSS carregado do arquivo local.");
                } else {
                    System.out.println("CSS não encontrado em lugar nenhum.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        palcoPrincipal.setTitle("TALKTREE // " + nomeUsuario);
        palcoPrincipal.setScene(cena);
        palcoPrincipal.show();

        conectarServidor();

        btnTransmitir.setOnAction(e -> enviarMensagem());
        campoMensagem.setOnAction(e -> enviarMensagem());
    }

    // ========== GRAVAÇÃO DE VOZ ==========
    private void configurarGravacaoVoz() {
        btnMicrofone.setOnMousePressed(e -> iniciarGravacao());
        btnMicrofone.setOnMouseReleased(e -> pararGravacaoEEnviar());
    }

    private void iniciarGravacao() {
        if (gravando) return;
        gravando = true;
        // muda estilo do botão
        btnMicrofone.setStyle("-fx-background-color: #E55934; -fx-text-fill: white;");
        btnMicrofone.setText("🔴 GRAVANDO...");

        // Configura áudio: 16kHz, 16-bit, mono, signed, big-endian (padrão)
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            linhaAudio = (TargetDataLine) AudioSystem.getLine(info);
            linhaAudio.open(format);
            linhaAudio.start();
            audioBuffer = new ByteArrayOutputStream();
            // Thread para ler dados da linha
            new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (linhaAudio.isOpen()) {
                    int bytesRead = linhaAudio.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioBuffer.write(buffer, 0, bytesRead);
                    }
                }
            }).start();

            // Timer para parar automaticamente após MAX_GRAVACAO_SEGUNDOS
            timerGravacao = new Timer(true);
            timerGravacao.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> pararGravacaoEEnviar());
                }
            }, MAX_GRAVACAO_SEGUNDOS * 1000L);
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
            mostrarAlerta("Erro", "Microfone não disponível.");
            gravando = false;
            btnMicrofone.setStyle("");
            btnMicrofone.setText("🎙️");
        }
    }

    private void pararGravacaoEEnviar() {
        if (!gravando) return;
        gravando = false;
        if (timerGravacao != null) {
            timerGravacao.cancel();
            timerGravacao = null;
        }
        if (linhaAudio != null) {
            linhaAudio.stop();
            linhaAudio.close();
        }
        byte[] audioBytes = audioBuffer != null ? audioBuffer.toByteArray() : null;
        if (audioBytes == null || audioBytes.length == 0) {
            mostrarAlerta("Aviso", "Nenhum áudio gravado.");
            btnMicrofone.setStyle("");
            btnMicrofone.setText("🎙️");
            return;
        }
        // Limite de tamanho: 5 MB (já que o servidor aceita até 5MB)
        if (audioBytes.length > 5 * 1024 * 1024) {
            mostrarAlerta("Áudio muito longo", "Máximo 5 MB (~30 segundos nessa qualidade).");
            btnMicrofone.setStyle("");
            btnMicrofone.setText("🎙️");
            return;
        }
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        String nomeArquivo = "voz_" + System.currentTimeMillis() + ".wav";
        String mensagem = "VOICE|" + nomeArquivo + "|" + base64Audio;

        if (canalAtual.startsWith("PV:")) {
            String dest = canalAtual.substring(3);
            out.println(nomeUsuario + "|PV|" + dest + "|" + mensagem);
        } else {
            out.println(nomeUsuario + "|" + mensagem);
        }

        btnMicrofone.setStyle("");
        btnMicrofone.setText("🎙️");
    }

    // ========== FECHAMENTO ==========
    @Override
    public void stop() {
        if (out != null)
            out.close();
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Limpa a pasta temporária
        if (pastaTemp != null && pastaTemp.exists()) {
            for (File f : pastaTemp.listFiles()) {
                if (f.isFile())
                    f.delete();
            }
            pastaTemp.delete();
        }
        System.out.println("Cliente desconectado: " + nomeUsuario);
    }

    // ========== HISTÓRICO ==========
    private void iniciarHistorico() {
        historicoHtml = new StringBuilder();
        historicoHtml.append("<html><head>");
        historicoHtml.append(
                "<link href='https://fonts.googleapis.com/css2?family=Outfit:wght@400;700&display=swap' rel='stylesheet'>");
        historicoHtml.append("<style>");
        historicoHtml.append("body { background-color: #0E0E12; background-image: url('")
                .append(new File("assets/sol.png").toURI().toString())
                .append("'); background-repeat: no-repeat; background-position: center; background-size: cover; background-attachment: fixed; color: #DCDCDC; font-family: 'Outfit', sans-serif; margin: 15px; font-size: 14px; }");
        historicoHtml.append("</style></head><body>");
        historicoHtml.append(
                "<div style='color:#555; font-size:11px; margin-bottom:15px;'>--- CONEXAO ESTABELECIDA // SINTONIZADO ---</div>");
        contadoresCanais.put(canalAtual, 0);
        atualizarWebView();
    }

    private void adicionarLinhaHtml(String linhaHtml) {
        int contador = contadoresCanais.getOrDefault(canalAtual, 0);
        if (contador >= MAX_HISTORICO) {
            String atual = historicoHtml.toString();
            int primeiroDiv = atual.indexOf("<div");
            if (primeiroDiv != -1) {
                int fimPrimeiro = atual.indexOf("</div>", primeiroDiv);
                if (fimPrimeiro != -1) {
                    historicoHtml = new StringBuilder(
                            atual.substring(0, primeiroDiv) + atual.substring(fimPrimeiro + 6));
                    contador--;
                }
            }
        }
        historicoHtml.append(linhaHtml);
        contadoresCanais.put(canalAtual, contador + 1);
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

        // VOICE
        if (texto.startsWith("VOICE|")) {
            String[] partes = texto.split("\\|", 3);
            if (partes.length == 3) {
                String nomeArquivo = partes[1];
                String base64Audio = partes[2];
                String htmlAudio = gerarHtmlAudio(nome, nomeArquivo, base64Audio);
                adicionarLinhaHtml(htmlAudio);
                return;
            }
        }

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

        if (texto.startsWith("STICKER|")) {
            String[] partes = texto.split("\\|", 3);
            if (partes.length == 3 && partes[0].equals("STICKER")) {
                String nomeEmoji = partes[1];
                String base64Data = partes[2];
                String htmlSticker = gerarHtmlSticker(nome, nomeEmoji, base64Data);
                adicionarLinhaHtml(htmlSticker);
                return;
            }
        }

        if (nome.equals("SISTEMA") || nome.equals("ERRO")) {
            String linhaHtml = String.format(
                    "<div style='color:#555; font-size:11px; margin-top:10px; margin-bottom:10px;'>[%s] // %s</div>",
                    hora, texto);
            adicionarLinhaHtml(linhaHtml);
            return;
        }

        String corNome = nome.equals(nomeUsuario) ? "#E55934" : "#9898A6";
        String linhaHtml = String.format(
                "<div style='margin-bottom:8px; background:rgba(20,20,26,0.8); padding:10px 14px; border-radius:6px; border-left:3px solid %s;'>"
                        +
                        "<span style='color:#666; font-size:11px;'>[%s]</span> <b style='color:%s;'>%s:</b> <span style='color:#eee;'>%s</span>"
                        +
                        "</div>",
                corNome, hora, corNome, nome, escaparHtml(texto));
        adicionarLinhaHtml(linhaHtml);
    }

    private void iniciarPvCom(String nomeOutro) {
        String pvCanal = "PV:" + nomeOutro;
        historicosCanais.put(canalAtual, historicoHtml);
        canalAtual = pvCanal;
        Platform.runLater(() -> {
            comboCanais.setOnAction(null);
            if (!comboCanais.getItems().contains(pvCanal)) {
                comboCanais.getItems().add(pvCanal);
            }
            comboCanais.setValue(pvCanal);
            comboCanais.setOnAction(e -> mudarCanalSelecionado());
        });
        historicoHtml = historicosCanais.get(pvCanal);
        if (historicoHtml == null) {
            iniciarHistoricoPv(nomeOutro);
        } else {
            atualizarWebView();
        }
    }

    private void iniciarHistoricoPv(String nomeOutro) {
        historicoHtml = new StringBuilder();
        historicoHtml.append("<html><head>");
        historicoHtml.append(
                "<link href='https://fonts.googleapis.com/css2?family=Outfit:wght@400;700&display=swap' rel='stylesheet'>");
        historicoHtml.append("<style>");
        historicoHtml.append("body { background-color: #0E0E12; background-image: url('")
                .append(new File("rascunhos/sol.gif").toURI().toString())
                .append("'); background-repeat: no-repeat; background-position: center; background-size: cover; background-attachment: fixed; color: #DCDCDC; font-family: 'Outfit', sans-serif; margin: 15px; font-size: 14px; }");
        historicoHtml.append("</style></head><body>");
        historicoHtml.append(
                "<div style='color:#E55934; font-size:11px; margin-bottom:15px; font-family:Monospaced;'>--- FREQUENCIA PRIVADA COM ")
                .append(nomeOutro.toUpperCase()).append(" ---</div>");
        contadoresCanais.put(canalAtual, 0);
        atualizarWebView();
    }

    private void adicionarMensagemPvAoChat(String remetente, String dest, String texto) {
        String outro = remetente.equals(nomeUsuario) ? dest : remetente;
        String pvCanal = "PV:" + outro;
        StringBuilder historicoPv = historicosCanais.get(pvCanal);
        if (historicoPv == null) {
            historicoPv = new StringBuilder();
            historicoPv.append("<html><head>");
            historicoPv.append(
                    "<link href='https://fonts.googleapis.com/css2?family=Outfit:wght@400;700&display=swap' rel='stylesheet'>");
            historicoPv.append("<style>");
            historicoPv.append("body { background-color: #0E0E12; background-image: url('")
                    .append(new File("rascunhos/sol.gif").toURI().toString())
                    .append("'); background-repeat: no-repeat; background-position: center; background-size: cover; background-attachment: fixed; color: #DCDCDC; font-family: 'Outfit', sans-serif; margin: 15px; font-size: 14px; }");
            historicoPv.append("</style></head><body>");
            historicoPv.append(
                    "<div style='color:#E55934; font-size:11px; margin-bottom:15px; font-family:Monospaced;'>--- FREQUENCIA PRIVADA COM ")
                    .append(outro.toUpperCase()).append(" ---</div>");
            historicosCanais.put(pvCanal, historicoPv);
            contadoresCanais.put(pvCanal, 0);
        }
        String linhaHtml;
        if (texto.startsWith("VOICE|")) {
            String[] partes = texto.split("\\|", 3);
            linhaHtml = gerarHtmlAudio(remetente, partes[1], partes[2]);
        } else if (texto.startsWith("FILE|")) {
            String[] partes = texto.split("\\|", 4);
            linhaHtml = gerarHtmlAnexo(remetente, partes[1], partes[2], partes[3]);
        } else if (texto.startsWith("STICKER|")) {
            String[] partes = texto.split("\\|", 3);
            linhaHtml = gerarHtmlSticker(remetente, partes[1], partes[2]);
        } else {
            String hora = formatter.format(new Date());
            String corNome = remetente.equals(nomeUsuario) ? "#E55934" : "#9898A6";
            linhaHtml = String.format(
                    "<div style='margin-bottom:8px; background:rgba(20,20,26,0.8); padding:10px 14px; border-radius:6px; border-left:3px solid %s;'>"
                            +
                            "<span style='color:#666; font-size:11px;'>[%s]</span> <b style='color:%s;'>%s:</b> <span style='color:#eee;'>%s</span>"
                            +
                            "</div>",
                    corNome, hora, corNome, remetente, escaparHtml(texto));
        }
        historicoPv.append(linhaHtml);
        if (canalAtual.equals(pvCanal)) {
            historicoHtml = historicoPv;
            atualizarWebView();
        } else {
            Platform.runLater(() -> {
                comboCanais.setOnAction(null);
                if (!comboCanais.getItems().contains(pvCanal)) {
                    comboCanais.getItems().add(pvCanal);
                }
                comboCanais.setOnAction(e -> mudarCanalSelecionado());
            });
        }
    }

    private String gerarHtmlAudio(String nomeRemetente, String nomeArquivo, String base64Audio) {
        String hora = formatter.format(new Date());
        String corNome = nomeRemetente.equals(nomeUsuario) ? "#E55934" : "#9898A6";
        // Salva o áudio em arquivo temporário para evitar data URI muito grande (opcional)
        String audioPath = salvarAudioTemp(nomeArquivo, base64Audio);
        String playerHtml;
        if (audioPath != null) {
            playerHtml = String.format(
                    "<div><audio controls style='width:250px;' src='%s'></audio><br/><span style='font-size:11px;'>🎤 Mensagem de voz</span></div>",
                    new File(audioPath).toURI().toString());
        } else {
            // fallback para data URI
            playerHtml = String.format(
                    "<div><audio controls style='width:250px;' src='data:audio/wav;base64,%s'></audio><br/><span style='font-size:11px;'>🎤 Mensagem de voz</span></div>",
                    base64Audio);
        }
        return String.format(
                "<div style='margin-bottom:8px; background:rgba(20,20,26,0.8); padding:10px 14px; border-radius:6px; border-left:3px solid %s;'>"
                        +
                        "<span style='color:#666; font-size:11px;'>[%s]</span> <b style='color:%s;'>%s</b> enviou áudio:<br/>"
                        +
                        "%s" +
                        "</div>",
                corNome, hora, corNome, nomeRemetente, playerHtml);
    }

    private String salvarAudioTemp(String nomeOriginal, String base64Data) {
        try {
            String nomeUnico = System.currentTimeMillis() + "_" + nomeOriginal;
            File audioFile = new File(pastaTemp, nomeUnico);
            byte[] dados = Base64.getDecoder().decode(base64Data);
            try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                fos.write(dados);
            }
            audioFile.deleteOnExit();
            return audioFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String gerarHtmlAnexo(String nomeRemetente, String nomeArquivo, String tipo, String base64Data) {
        String hora = formatter.format(new Date());
        String corNome = nomeRemetente.equals(nomeUsuario) ? "#E55934" : "#9898A6";
        String mediaHtml = "";

        if (tipo.equals("image")) {
            String extensao = "";
            int i = nomeArquivo.lastIndexOf('.');
            if (i > 0)
                extensao = nomeArquivo.substring(i + 1).toLowerCase();
            String mimeImg = extensao.equals("gif") ? "gif" : "png";
            if (extensao.equals("jpg") || extensao.equals("jpeg"))
                mimeImg = "jpeg";
            mediaHtml = String.format(
                    "<div><img src='data:image/%s;base64,%s' style='max-width:250px; max-height:200px; border-radius:6px; margin-top:5px;'/></div>",
                    mimeImg, base64Data);
        } else if (tipo.equals("video")) {
            String videoPath = salvarVideoTemp(nomeArquivo, base64Data);
            if (videoPath != null) {
                String videoUrl = new File(videoPath).toURI().toString();
                mediaHtml = String.format(
                        "<div><video controls style='max-width:250px; max-height:200px; border-radius:6px; margin-top:5px;'>"
                                +
                                "<source src='%s' type='video/mp4'>" +
                                "Seu navegador nao suporta video.</video></div>",
                        videoUrl);
            } else {
                mediaHtml = "<div>[Erro ao carregar video]</div>";
            }
        } else {
            mediaHtml = "<div>[Arquivo nao suportado]</div>";
        }

        return String.format(
                "<div style='margin-bottom:8px; background:rgba(20,20,26,0.8); padding:10px 14px; border-radius:6px; border-left:3px solid %s;'>"
                        +
                        "<span style='color:#666; font-size:11px;'>[%s]</span> <b style='color:%s;'>%s</b> enviou <i>%s</i><br/>"
                        +
                        "%s" +
                        "</div>",
                corNome, hora, corNome, nomeRemetente, nomeArquivo, mediaHtml);
    }

    private String gerarHtmlSticker(String nomeRemetente, String nomeEmoji, String base64Data) {
        String hora = formatter.format(new Date());
        String corNome = nomeRemetente.equals(nomeUsuario) ? "#E55934" : "#9898A6";
        return String.format(
                "<div style='margin-bottom:8px; background:rgba(20,20,26,0.8); padding:10px 14px; border-radius:6px; border-left:3px solid %s;'>"
                        +
                        "<span style='color:#666; font-size:11px;'>[%s]</span> <b style='color:%s;'>%s:</b><br/>" +
                        "<div><img src='data:image/png;base64,%s' style='width:90px; height:90px; border-radius:6px; margin-top:5px;' alt='%s'/></div>"
                        +
                        "</div>",
                corNome, hora, corNome, nomeRemetente, base64Data, nomeEmoji);
    }

    private void enviarSticker(String nome, File arquivo) {
        try {
            byte[] bytes = new byte[(int) arquivo.length()];
            try (FileInputStream fis = new FileInputStream(arquivo)) {
                fis.read(bytes);
            }
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mensagem = "STICKER|" + nome + "|" + base64;
            if (out != null) {
                if (canalAtual.startsWith("PV:")) {
                    String dest = canalAtual.substring(3);
                    out.println(nomeUsuario + "|PV|" + dest + "|" + mensagem);
                } else {
                    out.println(nomeUsuario + "|" + mensagem);
                }
            }
        } catch (IOException e) {
            mostrarAlerta("Erro", "Nao foi possivel ler o sticker.");
        }
    }

    private String salvarVideoTemp(String nomeOriginal, String base64Data) {
        try {
            String nomeUnico = System.currentTimeMillis() + "_" + nomeOriginal;
            File videoFile = new File(pastaTemp, nomeUnico);
            byte[] dados = Base64.getDecoder().decode(base64Data);
            try (FileOutputStream fos = new FileOutputStream(videoFile)) {
                fos.write(dados);
            }
            videoFile.deleteOnExit();
            return videoFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
        File arquivo = fileChooser.showOpenDialog(null);
        if (arquivo == null)
            return;

        if (arquivo.length() > 5 * 1024 * 1024) {
            mostrarAlerta("Arquivo muito grande", "Máximo permitido: 5 MB");
            return;
        }

        String nomeArquivo = arquivo.getName();
        String tipo = nomeArquivo.matches(".*\\.(png|jpg|jpeg|gif|bmp)$") ? "image" : "video";

        if (tipo.equals("video") && !nomeArquivo.toLowerCase().endsWith(".mp4")) {
            mostrarAlerta("Atenção", "Apenas vídeos MP4 (codec H.264) funcionarão corretamente.");
        }

        try {
            byte[] bytes = new byte[(int) arquivo.length()];
            try (FileInputStream fis = new FileInputStream(arquivo)) {
                fis.read(bytes);
            }
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String message = "FILE|" + nomeArquivo + "|" + tipo + "|" + base64;
            if (canalAtual.startsWith("PV:")) {
                String dest = canalAtual.substring(3);
                out.println(nomeUsuario + "|PV|" + dest + "|" + message);
            } else {
                out.println(nomeUsuario + "|" + message);
            }
        } catch (IOException e) {
            mostrarAlerta("Erro", "Não foi possível ler o arquivo.");
        }
    }

    // ========== EMOJIS ==========
    private void mostrarMenuEmoji(Button btnEmoji) {
        Stage popup = new Stage();
        popup.initStyle(javafx.stage.StageStyle.UNDECORATED);
        popup.initOwner(campoMensagem.getScene().getWindow());
        popup.initModality(javafx.stage.Modality.NONE);

        TilePane painelGrelha = new TilePane();
        painelGrelha.setPadding(new Insets(15));
        painelGrelha.setHgap(10);
        painelGrelha.setVgap(10);
        painelGrelha.setPrefColumns(5);
        painelGrelha.setStyle("-fx-background-color: #121218;");

        for (String nome : STICKERS) {
            try {
                File arquivo = new File("assets/emojis/" + nome + ".png");
                if (arquivo.exists()) {
                    Image img = new Image(arquivo.toURI().toString(), 60, 60, true, true);
                    ImageView imgView = new ImageView(img);
                    Button btn = new Button();
                    btn.setGraphic(imgView);
                    btn.getStyleClass().add("botao-sticker");
                    btn.setPrefSize(70, 70);
                    btn.setOnAction(e -> {
                        enviarSticker(nome, arquivo);
                        popup.close();
                    });
                    painelGrelha.getChildren().add(btn);
                }
            } catch (Exception ex) {
            }
        }

        ScrollPane scroll = new ScrollPane(painelGrelha);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        scroll.setPrefViewportWidth(400);
        scroll.setStyle("-fx-background: #121218; -fx-border-color: transparent;");

        HBox cabecalho = new HBox();
        cabecalho.setPadding(new Insets(10, 15, 5, 15));
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        Label labelTitulo = new Label("EMOJIS");
        labelTitulo.setStyle(
                "-fx-text-fill: #FF6E00; -fx-font-family: 'Monospaced'; -fx-font-weight: bold; -fx-font-size: 12px;");
        cabecalho.getChildren().add(labelTitulo);

        VBox raizPopup = new VBox();
        raizPopup.setStyle(
                "-fx-background-color: #121218; -fx-border-color: #282835; -fx-border-width: 1; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        raizPopup.getChildren().addAll(cabecalho, scroll);

        Scene cenaPopup = new Scene(raizPopup);
        try {
            String cssUrl = getClass().getResource("estilo.css").toExternalForm();
            cenaPopup.getStylesheets().add(cssUrl);
        } catch (Exception e) {
            try {
                File cssFile = new File("estilo.css");
                if (cssFile.exists()) {
                    cenaPopup.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
                }
            } catch (Exception ex) {
            }
        }
        popup.setScene(cenaPopup);
        popup.setResizable(false);

        try {
            javafx.geometry.Point2D coord = btnEmoji.localToScreen(0, 0);
            if (coord != null) {
                popup.setX(coord.getX() - 175);
                popup.setY(coord.getY() - 380);
            }
        } catch (Exception ex) {
        }

        popup.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                popup.close();
            }
        });

        popup.show();
    }

    // ========== REFRESH ==========
    private void solicitarRefresh() {
        if (out != null)
            out.println("REFRESH");
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
                    if (linha.startsWith("CANAIS|")) {
                        String[] nomes = linha.substring(7).split(",");
                        Platform.runLater(() -> {
                            comboCanais.setOnAction(null);
                            comboCanais.getItems().clear();
                            for (String n : nomes) {
                                if (!n.isEmpty()) {
                                    comboCanais.getItems().add(n);
                                }
                            }
                            comboCanais.setValue(canalAtual);
                            comboCanais.setOnAction(e -> mudarCanalSelecionado());
                        });
                    } else if (linha.startsWith("LISTA|")) {
                        String[] nomes = linha.substring(6).split(",");
                        Platform.runLater(() -> {
                            torresOnline.clear();
                            for (String n : nomes)
                                if (!n.isEmpty())
                                    torresOnline.add("🟢 " + n + (n.equals(nomeUsuario) ? " [VOCÊ]" : ""));
                        });
                    } else if (linha.startsWith("ENTROU|")) {
                        String n = linha.substring(7);
                        Platform.runLater(() -> {
                            if (!torresOnline.contains("🟢 " + n))
                                torresOnline.add("🟢 " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " entrou na frequência.");
                        });
                    } else if (linha.startsWith("SAIU|")) {
                        String n = linha.substring(5);
                        Platform.runLater(() -> {
                            torresOnline.remove("🟢 " + n);
                            adicionarMensagemAoChat("SISTEMA", n + " saiu da frequência.");
                        });
                    } else {
                        String[] partes = linha.split("\\|", 2);
                        if (partes.length == 2) {
                            // Verifica se é mensagem privada
                            if (partes[1].startsWith("PV|")) {
                                String[] pvParts = partes[1].split("\\|", 4);
                                if (pvParts.length == 4 && pvParts[0].equals("PV")) {
                                    String remetente = partes[0];
                                    String destinatario = pvParts[1];
                                    String msgConteudo = pvParts[3];
                                    adicionarMensagemPvAoChat(remetente, destinatario, msgConteudo);
                                }
                            } else {
                                adicionarMensagemAoChat(partes[0], partes[1]);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> adicionarMensagemAoChat("ERRO", "Conexão perdida com a central."));
            }
        }).start();
    }

    private void mudarCanalSelecionado() {
        String selecionado = comboCanais.getValue();
        if (selecionado != null && !selecionado.equals(canalAtual)) {
            historicosCanais.put(canalAtual, historicoHtml);
            canalAtual = selecionado;
            if (out != null) {
                out.println("MUDAR_CANAL|" + canalAtual);
            }
            historicoHtml = historicosCanais.get(canalAtual);
            if (historicoHtml == null) {
                iniciarHistorico();
            } else {
                atualizarWebView();
            }
        }
    }

    private void criarNovoCanal() {
    // Cria um Dialog personalizado
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("NOVO CANAL");
    dialog.setHeaderText(null); // remove cabeçalho padrão

    // Aplica o CSS da aplicação (reutiliza o mesmo arquivo)
    DialogPane dialogPane = dialog.getDialogPane();
    try {
        String cssUrl = getClass().getResource("estilo.css").toExternalForm();
        dialogPane.getStylesheets().add(cssUrl);
    } catch (Exception e) {
        File cssFile = new File("estilo.css");
        if (cssFile.exists()) {
            try {
                dialogPane.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
            } catch (Exception ex) { }
        }
    }
    dialogPane.getStyleClass().add("dialog-custom");

    // Campo de texto
    TextField inputField = new TextField();
    inputField.setPromptText("EX: TECNOLOGIA");
    inputField.getStyleClass().add("campo-mensagem");

    // Labels e layout
    VBox content = new VBox(15);
    content.setPadding(new Insets(20));
    content.setAlignment(Pos.CENTER);
    Label label = new Label("CRIAR FREQUÊNCIA");
    label.getStyleClass().add("dialog-titulo");
    Label sublabel = new Label("Digite o nome do novo canal:");
    sublabel.getStyleClass().add("dialog-subtitulo");

    content.getChildren().addAll(label, sublabel, inputField);
    dialogPane.setContent(content);

    // Botões
    ButtonType criarButtonType = new ButtonType("CRIAR", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelarButtonType = new ButtonType("CANCELAR", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialogPane.getButtonTypes().addAll(criarButtonType, cancelarButtonType);

    // Estiliza os botões
    Node criarButton = dialogPane.lookupButton(criarButtonType);
    Node cancelarButton = dialogPane.lookupButton(cancelarButtonType);
    criarButton.getStyleClass().add("botao-transmitir");
    cancelarButton.getStyleClass().add("botao-cancelar");

    // Habilita o botão "CRIAR" apenas quando o campo não estiver vazio
    final Button btCriar = (Button) criarButton;
    inputField.textProperty().addListener((obs, old, novo) -> 
        btCriar.setDisable(novo.trim().isEmpty())
    );
    btCriar.setDisable(true);

    // Resultado
    dialog.setResultConverter(dialogButton -> {
        if (dialogButton == criarButtonType) {
            return inputField.getText().trim();
        }
        return null;
    });

    // Mostra e processa
    dialog.showAndWait().ifPresent(nome -> {
        if (!nome.isEmpty() && out != null) {
            out.println("CRIAR_CANAL|" + nome);
        }
    });
}

    private void enviarMensagem() {
        String msg = campoMensagem.getText().trim();
        if (!msg.isEmpty() && out != null) {
            if (canalAtual.startsWith("PV:")) {
                String dest = canalAtual.substring(3);
                out.println(nomeUsuario + "|PV|" + dest + "|" + msg);
            } else {
                out.println(nomeUsuario + "|" + msg);
            }
            campoMensagem.clear();
        }
    }

    private String mostrarTelaLogin() {
        Stage loginStage = new Stage();
        loginStage.setTitle("CONECTAR - TALKTREE");
        loginStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        loginStage.setResizable(false);

        VBox painel = new VBox(20);
        painel.setAlignment(Pos.CENTER);
        painel.setPadding(new Insets(30));
        painel.setStyle("-fx-background-color: #0E0E12; -fx-border-color: #21212B; -fx-border-width: 1px;");

        Label titulo = new Label("TALKTREE");
        titulo.setStyle(
                "-fx-text-fill: #E55934; -fx-font-family: 'Outfit'; -fx-font-weight: bold; -fx-font-size: 24px;");

        Label subtitulo = new Label("INSIRA SEU NOME DE USUÁRIO:");
        subtitulo.setStyle("-fx-text-fill: #9898A6; -fx-font-family: 'Outfit'; -fx-font-size: 13px;");

        TextField campoNome = new TextField();
        campoNome.setPromptText("Ex: TORRE_NORTE");
        campoNome.getStyleClass().add("campo-mensagem");
        campoNome.setMaxWidth(300);
        campoNome.setPrefWidth(300);

        Button btnEntrar = new Button("CONECTAR FREQUÊNCIA");
        btnEntrar.getStyleClass().add("botao-transmitir");
        btnEntrar.setPrefWidth(220);
        btnEntrar.setDefaultButton(true);

        Label labelErro = new Label();
        labelErro.setStyle("-fx-text-fill: #E55934; -fx-font-family: 'Outfit'; -fx-font-size: 12px;");

        Runnable acaoLogin = () -> {
            String nome = campoNome.getText().trim();
            if (nome.isEmpty()) {
                labelErro.setText("ERRO: Nome não pode estar vazio.");
            } else {
                loginStage.close();
            }
        };

        btnEntrar.setOnAction(e -> acaoLogin.run());
        campoNome.setOnAction(e -> acaoLogin.run());

        loginStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });

        painel.getChildren().addAll(titulo, subtitulo, campoNome, btnEntrar, labelErro);
        Scene cenaLogin = new Scene(painel, 500, 350);

        try {
            String cssUrl = getClass().getResource("estilo.css").toExternalForm();
            cenaLogin.getStylesheets().add(cssUrl);
        } catch (Exception e) {
        }

        loginStage.setScene(cenaLogin);
        loginStage.showAndWait();

        return campoNome.getText().trim().isEmpty() ? null : campoNome.getText().trim();
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