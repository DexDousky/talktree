import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;

public class Cliente extends JFrame {
    private JTextPane areaChat;
    private JTextField campoMensagem;
    private DefaultListModel<String> modeloUsuarios;
    private PrintWriter out;
    private String nomeUsuario;
    private SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");

    public Cliente() {
        nomeUsuario = mostrarLogin();

        setTitle("TALKTREE // OPERACOES FLORESTAIS");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(18, 18, 24));
        setLayout(new BorderLayout(0, 0));

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(new Color(24, 24, 32));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(40, 40, 50)));

        JLabel tituloSidebar = new JLabel("  SINAIS DE RADIO");
        tituloSidebar.setFont(new Font("Monospaced", Font.BOLD, 15));
        tituloSidebar.setForeground(new Color(255, 110, 0));
        tituloSidebar.setPreferredSize(new Dimension(0, 60));
        sidebar.add(tituloSidebar, BorderLayout.NORTH);

        modeloUsuarios = new DefaultListModel<>();
        modeloUsuarios.addElement("  o " + nomeUsuario + " [VOCE]");
        JList<String> lista = new JList<>(modeloUsuarios);
        lista.setBackground(new Color(24, 24, 32));
        lista.setForeground(new Color(160, 160, 170));
        lista.setFont(new Font("Monospaced", Font.PLAIN, 14));
        lista.setFixedCellHeight(45);
        sidebar.add(new JScrollPane(lista), BorderLayout.CENTER);
        add(sidebar, BorderLayout.WEST);

        JPanel painelChat = new JPanel(new BorderLayout());
        painelChat.setBackground(new Color(18, 18, 24));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        header.setBackground(new Color(22, 22, 30));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 50)));
        JLabel statusCanal = new JLabel("SINTONIZADO: CANAL_7_NORTE");
        statusCanal.setFont(new Font("Monospaced", Font.BOLD, 13));
        statusCanal.setForeground(new Color(0, 255, 150));
        header.add(statusCanal);
        painelChat.add(header, BorderLayout.NORTH);

        areaChat = new JTextPane();
        areaChat.setEditable(false);
        areaChat.setContentType("text/html");
        areaChat.setBackground(new Color(18, 18, 24));
        areaChat.setText("<html><body style='font-family:Monospaced; color:#aaa; font-size:12px;'><div style='color:#555;'>--- INICIO DA TRANSMISSAO ---</div></body></html>");

        JScrollPane scroll = new JScrollPane(areaChat);
        scroll.setBorder(null);
        painelChat.add(scroll, BorderLayout.CENTER);

        JPanel rodape = new JPanel(new BorderLayout(15, 0));
        rodape.setBackground(new Color(22, 22, 30));
        rodape.setBorder(new EmptyBorder(20, 20, 20, 20));

        campoMensagem = new JTextField();
        campoMensagem.setBackground(new Color(30, 30, 40));
        campoMensagem.setForeground(Color.WHITE);
        campoMensagem.setCaretColor(new Color(255, 110, 0));
        campoMensagem.setFont(new Font("Monospaced", Font.PLAIN, 15));
        // Borda arredondada em vez da linha reta
        campoMensagem.setBorder(new RoundedBorder(new Color(60, 60, 75), 1, 15));

        JButton btnEnviar = new JButton("TRANSMITIR");
        btnEnviar.setBackground(new Color(255, 110, 0));
        btnEnviar.setForeground(Color.BLACK);
        btnEnviar.setFont(new Font("Monospaced", Font.BOLD, 13));
        btnEnviar.setFocusPainted(false);
        btnEnviar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEnviar.setBorder(new RoundedBorder(new Color(255, 110, 0), 2, 20));
        btnEnviar.setContentAreaFilled(false);
        btnEnviar.setOpaque(false);

        rodape.add(campoMensagem, BorderLayout.CENTER);
        rodape.add(btnEnviar, BorderLayout.EAST);
        painelChat.add(rodape, BorderLayout.SOUTH);
        add(painelChat, BorderLayout.CENTER);

        ActionListener acaoEnvio = e -> enviarMensagem();
        btnEnviar.addActionListener(acaoEnvio);
        campoMensagem.addActionListener(acaoEnvio);

        setVisible(true);
        conectar();
    }

    private void enviarMensagem() {
        String texto = campoMensagem.getText().trim();
        if (!texto.isEmpty() && out != null) {
            // Formato original: nome|mensagem
            out.println(nomeUsuario + "|" + texto);
            campoMensagem.setText("");
        }
    }

    private String mostrarLogin() {
        JDialog login = new JDialog((Frame) null, "TALKTREE // AUTENTICACAO", true);
        login.setSize(400, 250);
        login.setLocationRelativeTo(null);
        login.getContentPane().setBackground(new Color(24, 24, 32));
        login.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;

        JLabel lbl = new JLabel("IDENTIFIQUE SUA TORRE:");
        lbl.setForeground(new Color(255, 110, 0));
        lbl.setFont(new Font("Monospaced", Font.BOLD, 14));
        gbc.gridy = 0;
        login.add(lbl, gbc);

        JTextField txt = new JTextField(15);
        txt.setBackground(new Color(30, 30, 40));
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(new Color(255, 110, 0));
        txt.setBorder(new RoundedBorder(new Color(60, 60, 75), 1, 15));
        txt.setFont(new Font("Monospaced", Font.PLAIN, 14));
        gbc.gridy = 1;
        login.add(txt, gbc);

        JButton btn = new JButton("ENTRAR NA FREQUENCIA");
        btn.setBackground(new Color(255, 110, 0));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Monospaced", Font.BOLD, 13));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new RoundedBorder(new Color(255, 110, 0), 2, 20));
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        gbc.gridy = 2;
        login.add(btn, gbc);

        final String[] nome = {null};
        btn.addActionListener(e -> {
            nome[0] = txt.getText().trim();
            login.dispose();
        });
        txt.addActionListener(e -> btn.doClick());

        login.setVisible(true);
        return (nome[0] == null || nome[0].isEmpty()) ? "Vigia_" + (int) (Math.random() * 100) : nome[0];
    }

    private void adicionarMensagem(String nome, String msg) {
        String hora = formatter.format(new Date());
        String corNome = nome.equals(nomeUsuario) ? "#FF6E00" : "#00FF96";
        String htmlMsg = String.format(
            "<div style='margin-bottom:8px;'><span style='color:#555;'>[%s]</span> <b style='color:%s;'>%s:</b> <span style='color:#eee;'>%s</span></div>",
            hora, corNome, nome, msg
        );
        SwingUtilities.invokeLater(() -> {
            try {
                String content = areaChat.getText();
                int bodyEnd = content.lastIndexOf("</body>");
                if (bodyEnd != -1) {
                    areaChat.setText(content.substring(0, bodyEnd) + htmlMsg + "</body></html>");
                } else {
                    areaChat.setText("<html><body>" + htmlMsg + "</body></html>");
                }
                areaChat.setCaretPosition(areaChat.getDocument().getLength());
            } catch (Exception ignored) {}
        });
    }

    private void conectar() {
        new Thread(() -> {
            int tentativas = 0;
            while (tentativas < 5) {
                try {
                    Socket s = new Socket("localhost", 12345);
                    out = new PrintWriter(s.getOutputStream(), true);

                    // Login no formato que o servidor espera
                    out.println("LOGIN|" + nomeUsuario);

                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        // Interpretação da linha recebida
                        if (line.startsWith("LIST|")) {
                            // Atualiza a barra lateral sem aparecer no chat
                            String lista = line.substring(5);
                            SwingUtilities.invokeLater(() -> {
                                modeloUsuarios.clear();
                                if (!lista.isEmpty()) {
                                    for (String u : lista.split(",")) {
                                        String entrada = "  o " + u;
                                        if (u.equals(nomeUsuario)) entrada += " [VOCE]";
                                        modeloUsuarios.addElement(entrada);
                                    }
                                }
                                // Garante que o próprio usuário esteja visível
                                if (!modeloUsuarios.contains("  o " + nomeUsuario + " [VOCE]")) {
                                    modeloUsuarios.addElement("  o " + nomeUsuario + " [VOCE]");
                                }
                            });
                        } else if (line.startsWith("JOIN|")) {
                            String nome = line.substring(5);
                            SwingUtilities.invokeLater(() -> {
                                if (!modeloUsuarios.contains("  o " + nome)) {
                                    modeloUsuarios.addElement("  o " + nome);
                                }
                                adicionarMensagem("SISTEMA", nome + " entrou na frequencia.");
                            });
                        } else if (line.startsWith("LEAVE|")) {
                            String nome = line.substring(6);
                            SwingUtilities.invokeLater(() -> {
                                modeloUsuarios.removeElement("  o " + nome);
                                adicionarMensagem("SISTEMA", nome + " saiu da frequencia.");
                            });
                        } else {
                            // Formato antigo: nome|mensagem (pode ter o pipe no meio)
                            String[] partes = line.split("\\|", 2);
                            if (partes.length == 2) {
                                adicionarMensagem(partes[0], partes[1]);
                            } else {
                                adicionarMensagem("SISTEMA", line);
                            }
                        }
                    }
                    // Se sair do loop, a conexão caiu
                    break;
                } catch (Exception ex) {
                    tentativas++;
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                }
            }
            if (out == null) {
                adicionarMensagem("ERRO", "Central offline após várias tentativas.");
            }
        }).start();
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> new Cliente());
    }
}

/**
 * Borda arredondada simples.
 */
class RoundedBorder extends AbstractBorder {
    private final Color color;
    private final int thickness;
    private final int radius;

    public RoundedBorder(Color color, int thickness, int radius) {
        this.color = color;
        this.thickness = thickness;
        this.radius = radius;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        for (int i = 0; i < thickness; i++) {
            g2.drawRoundRect(x + i, y + i, width - 1 - 2 * i, height - 1 - 2 * i, radius, radius);
        }
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(thickness, thickness, thickness, thickness);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.right = insets.top = insets.bottom = thickness;
        return insets;
    }
}