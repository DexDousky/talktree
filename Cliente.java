import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 Rádio do Brigadista (Cliente)
 Tentei deixar com a vibe Firewatch/Fears to Fathom.
 */
public class Cliente extends JFrame {
    private JTextArea areaChat;
    private JTextField campoMensagem;
    private DefaultListModel<String> modeloUsuarios;
    private JList<String> listaUsuarios;
    private PrintWriter out;
    private String nomeUsuario;

    public Cliente() {
        // Pede o nome antes de abrir tudo
        nomeUsuario = JOptionPane.showInputDialog(this, "Identifique sua Torre (Nome):", "Login de Brigadista", JOptionPane.PLAIN_MESSAGE);
        if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) nomeUsuario = "Vigia Desconhecido";

        // Configuração da Janela
        setTitle("TalkTree - Sala de Operações (" + nomeUsuario + ")");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(20, 20, 25)); // Cor de fundo bem escura
        setLayout(new BorderLayout(5, 5));

        // --- SIDEBAR (USUÁRIOS ONLINE) ---
        JPanel painelEsquerda = new JPanel(new BorderLayout());
        painelEsquerda.setPreferredSize(new Dimension(180, 0));
        painelEsquerda.setBackground(new Color(30, 30, 35));
        
        JLabel labelUsuarios = new JLabel(" TORRES ONLINE");
        labelUsuarios.setForeground(new Color(255, 100, 0)); // Laranja Firewatch
        labelUsuarios.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        painelEsquerda.add(labelUsuarios, BorderLayout.NORTH);

        modeloUsuarios = new DefaultListModel<>();
        modeloUsuarios.addElement("\uD83D\uDFE2 " + nomeUsuario + " (Voc\u00EA)");
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setBackground(new Color(30, 30, 35));
        listaUsuarios.setForeground(Color.LIGHT_GRAY);
        painelEsquerda.add(new JScrollPane(listaUsuarios), BorderLayout.CENTER);

        add(painelEsquerda, BorderLayout.WEST);

        // --- ÁREA CENTRAL (CHAT) ---
        JPanel painelCentro = new JPanel(new BorderLayout());
        painelCentro.setBackground(new Color(20, 20, 25));

        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setBackground(new Color(15, 15, 20));
        areaChat.setForeground(new Color(220, 220, 220));
        areaChat.setFont(new Font("Monospaced", Font.PLAIN, 14));
        areaChat.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane scrollChat = new JScrollPane(areaChat);
        scrollChat.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 60)));
        painelCentro.add(scrollChat, BorderLayout.CENTER);

        // --- CAMPO DE ENVIAR ---
        JPanel painelSul = new JPanel(new BorderLayout(5, 0));
        painelSul.setBackground(new Color(20, 20, 25));
        painelSul.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        campoMensagem = new JTextField();
        campoMensagem.setBackground(new Color(40, 40, 45));
        campoMensagem.setForeground(Color.WHITE);
        campoMensagem.setCaretColor(Color.WHITE);
        campoMensagem.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 110)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JButton botaoEnviar = new JButton("TRANSMITIR");
        botaoEnviar.setBackground(new Color(255, 100, 0));
        botaoEnviar.setForeground(Color.BLACK);
        botaoEnviar.setFocusPainted(false);
        botaoEnviar.setFont(new Font("Arial", Font.BOLD, 12));

        painelSul.add(campoMensagem, BorderLayout.CENTER);
        painelSul.add(botaoEnviar, BorderLayout.EAST);
        painelCentro.add(painelSul, BorderLayout.SOUTH);

        add(painelCentro, BorderLayout.CENTER);

        // Lógica de envio
        ActionListener enviarAcao = e -> {
            String texto = campoMensagem.getText();
            if (!texto.isEmpty()) {
                out.println("[" + nomeUsuario + "]: " + texto);
                campoMensagem.setText("");
            }
        };
        botaoEnviar.addActionListener(enviarAcao);
        campoMensagem.addActionListener(enviarAcao);

        setVisible(true);
        conectarServidor();
    }

    private void conectarServidor() {
        try {
            Socket socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Thread pra ficar ouvindo o que chega do servidor sem travar a tela
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        areaChat.append(msg + "\n");
                        areaChat.setCaretPosition(areaChat.getDocument().getLength());
                    }
                } catch (IOException e) {
                    areaChat.append("CONEXÃO PERDIDA COM A CENTRAL\n");
                }
            }).start();

            areaChat.append(">>> RÁDIO SINTONIZADO NA CENTRAL <<<\n");
        } catch (IOException e) {
            areaChat.append("ERRO AO CONECTAR NA CENTRAL\n");
        }
    }

    public static void main(String[] args) {
        // Garante que a interface rode bonitinho
        SwingUtilities.invokeLater(() -> new Cliente());
    }
}
