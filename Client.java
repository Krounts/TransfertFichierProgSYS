import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Properties;

public class Client {
    private JTextField serverField;
    private JTextArea logArea;
    private JButton connectButton;
    private JButton sendButton;
    private JButton browseButton;
    private JButton downloadButton;
    private JButton deleteButton;
    private JButton listButton;
    private JFrame frame;
    private File selectedFile;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private String serverAddress;
    private int port;

    public Client() {
        // Charger la configuration
        loadConfiguration();

        frame = new JFrame("Client de Transfert de Fichiers");
        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Courier New", Font.PLAIN, 14));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.CYAN);

        serverField = new JTextField(serverAddress, 20);
        serverField.setEditable(false);
        serverField.setFont(new Font("Arial", Font.PLAIN, 14));
        serverField.setBorder(new LineBorder(Color.GRAY, 2));

        connectButton = new JButton("Se connecter");
        connectButton.setBackground(Color.DARK_GRAY);
        connectButton.setForeground(Color.WHITE);
        connectButton.setFont(new Font("Arial", Font.BOLD, 14));
        connectButton.setBorder(new LineBorder(Color.GREEN, 2));

        sendButton = new JButton("Envoyer");
        sendButton.setEnabled(false);
        sendButton.setBackground(Color.DARK_GRAY);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 14));
        sendButton.setBorder(new LineBorder(Color.BLUE, 2));

        browseButton = new JButton("Parcourir");
        browseButton.setEnabled(false);
        browseButton.setBackground(Color.DARK_GRAY);
        browseButton.setForeground(Color.WHITE);
        browseButton.setFont(new Font("Arial", Font.BOLD, 14));
        browseButton.setBorder(new LineBorder(Color.ORANGE, 2));

        downloadButton = new JButton("Télécharger");
        downloadButton.setEnabled(false);
        downloadButton.setBackground(Color.DARK_GRAY);
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setFont(new Font("Arial", Font.BOLD, 14));
        downloadButton.setBorder(new LineBorder(Color.BLUE, 2));

        deleteButton = new JButton("Supprimer");
        deleteButton.setEnabled(false);
        deleteButton.setBackground(Color.DARK_GRAY);
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFont(new Font("Arial", Font.BOLD, 14));
        deleteButton.setBorder(new LineBorder(Color.RED, 2));

        listButton = new JButton("Lister");
        listButton.setEnabled(false);
        listButton.setBackground(Color.DARK_GRAY);
        listButton.setForeground(Color.WHITE);
        listButton.setFont(new Font("Arial", Font.BOLD, 14));
        listButton.setBorder(new LineBorder(Color.GREEN, 2));

        JPanel inputPanel = new JPanel();
        inputPanel.setBackground(Color.LIGHT_GRAY);
        inputPanel.add(new JLabel("Adresse du serveur :"));
        inputPanel.add(serverField);
        inputPanel.add(connectButton);
        inputPanel.add(browseButton);
        inputPanel.add(sendButton);
        inputPanel.add(downloadButton);
        inputPanel.add(deleteButton);
        inputPanel.add(listButton);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new LineBorder(Color.MAGENTA, 2));

        frame.setLayout(new BorderLayout());
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        connectButton.addActionListener(e -> connectToServer());
        browseButton.addActionListener(e -> browseFile());
        sendButton.addActionListener(e -> sendFile());
        downloadButton.addActionListener(e -> downloadFile());
        deleteButton.addActionListener(e -> deleteFile());
        listButton.addActionListener(e -> listFiles());
    }

    private void loadConfiguration() {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream("client.conf")) {
            config.load(fis);
            serverAddress = config.getProperty("server_address", "127.0.0.1");
            port = Integer.parseInt(config.getProperty("port", "12467"));
        } catch (IOException | NumberFormatException e) {
            serverAddress = "127.0.0.1";
            port = 12467;
            log("Erreur lors du chargement de la configuration, utilisation des valeurs par défaut.");
        }
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(serverAddress, port);
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());

                log("Connecté au serveur : " + serverAddress + ":" + port);
                connectButton.setEnabled(false);
                sendButton.setEnabled(true);
                browseButton.setEnabled(true);
                downloadButton.setEnabled(true);
                deleteButton.setEnabled(true);
                listButton.setEnabled(true);

            } catch (IOException e) {
                log("Erreur de connexion : " + e.getMessage());
            }
        }).start();
    }
    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            log("Fichier sélectionné : " + selectedFile.getAbsolutePath());
        } else {
            log("Aucun fichier sélectionné.");
        }
    }

    private void sendFile() {
        if (selectedFile == null) {
            log("Veuillez sélectionner un fichier avant d'envoyer.");
            return;
        }

        if (socket == null || socket.isClosed()) {
            log("Veuillez d'abord vous connecter au serveur.");
            return;
        }

        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(selectedFile)) {
                log("Envoi du fichier : " + selectedFile.getName());

                dos.writeUTF(selectedFile.getName());
                dos.writeLong(selectedFile.length());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }

                log("Fichier envoyé : " + selectedFile.getName());

                String response = dis.readUTF();
                log("Réponse du serveur : " + response);

            } catch (IOException e) {
                log("Erreur lors de l'envoi du fichier : " + e.getMessage());
            }
        }).start();
    }

    private void downloadFile() {
        new Thread(() -> {
            try {
                // Demander la liste des fichiers disponibles sur le serveur
                dos.writeUTF("GET_LIST");
                dos.flush();

                // Lire la liste des fichiers
                int fileCount = dis.readInt();
                String[] fileNames = new String[fileCount];
                for (int i = 0; i < fileCount; i++) {
                    fileNames[i] = dis.readUTF();
                }

                // Afficher un JFileChooser pour que l'utilisateur puisse sélectionner un fichier
                String selectedFileName = (String) JOptionPane.showInputDialog(frame,
                        "Choisissez un fichier à télécharger :",
                        "Sélectionner un fichier", JOptionPane.PLAIN_MESSAGE,
                        null, fileNames, fileNames[0]);

                if (selectedFileName == null) {
                    return;
                }

                // Demander au serveur d'envoyer le fichier choisi
                dos.writeUTF("GET " + selectedFileName);
                dos.flush();

                // Recevoir le fichier du serveur
                String serverResponse = dis.readUTF();
                if (serverResponse.equals("FILE_NOT_FOUND")) {
                    log("Le fichier n'a pas été trouvé sur le serveur.");
                    return;
                }

                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new File(fileName));
                int result = fileChooser.showSaveDialog(frame);

                if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                }

                File saveFile = fileChooser.getSelectedFile();

                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }

                    log("Fichier téléchargé : " + saveFile.getAbsolutePath());
                }

            } catch (IOException e) {
                log("Erreur lors du téléchargement du fichier : " + e.getMessage());
            }
        }).start();
    }
    private void deleteFile() {
        new Thread(() -> {
            try {
                // Demander la liste des fichiers disponibles sur le serveur
                dos.writeUTF("GET_LIST");
                dos.flush();
    
                // Lire la liste des fichiers
                int fileCount = dis.readInt();
                String[] fileNames = new String[fileCount];
                for (int i = 0; i < fileCount; i++) {
                    fileNames[i] = dis.readUTF();
                }
    
                // Afficher un JFileChooser pour sélectionner le fichier à supprimer
                String selectedFileName = (String) JOptionPane.showInputDialog(frame,
                        "Choisissez un fichier à supprimer :",
                        "Supprimer un fichier", JOptionPane.PLAIN_MESSAGE,
                        null, fileNames, fileNames[0]);
    
                if (selectedFileName == null) {
                    return; // L'utilisateur a annulé
                }
    
                // Envoyer la commande de suppression au serveur
                dos.writeUTF("DELETE " + selectedFileName);
                dos.flush();
    
                // Lire la réponse du serveur
                String response = dis.readUTF();
                log("Réponse du serveur : " + response);
    
            } catch (IOException e) {
                log("Erreur lors de la suppression du fichier : " + e.getMessage());
            }
        }).start();
    }
    private void listFiles() {
        new Thread(() -> {
            try {
                // Envoyer la commande GET_LIST au serveur
                dos.writeUTF("GET_LIST");
                dos.flush();
    
                // Recevoir la liste des fichiers
                int fileCount = dis.readInt();
                StringBuilder fileList = new StringBuilder("Fichiers disponibles sur le serveur :\n");
                for (int i = 0; i < fileCount; i++) {
                    fileList.append("- ").append(dis.readUTF()).append("\n");
                }
    
                // Afficher la liste des fichiers dans la zone de log
                log(fileList.toString());
    
            } catch (IOException e) {
                log("Erreur lors de la récupération de la liste des fichiers : " + e.getMessage());
            }
        }).start();
    }
    
    

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}
