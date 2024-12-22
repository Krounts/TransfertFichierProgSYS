import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Serveur {
    private JTextArea logArea;
    private JButton startButton;
    private JFrame frame;
    private final File uploadDir = new File("upload"); // Dossier d'upload

    public Serveur() {
        // Créer le dossier d'upload s'il n'existe pas
        if (!uploadDir.exists()) {
            uploadDir.mkdir();
        }

        frame = new JFrame("Serveur de Transfert de Fichiers");
        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Courier New", Font.PLAIN, 14));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new LineBorder(Color.BLUE, 2));

        startButton = new JButton("Démarrer le Serveur");
        startButton.setBackground(Color.DARK_GRAY);
        startButton.setForeground(Color.WHITE);
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.setBorder(new LineBorder(Color.GREEN, 2));

        JPanel panel = new JPanel();
        panel.setBackground(Color.LIGHT_GRAY);
        panel.add(startButton);

        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        startButton.addActionListener(e -> startServer());
    }

    private void startServer() {
        new Thread(() -> {
            int port = loadPortFromConfig(); // Charger le port depuis serveur.conf

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log("Serveur démarré sur le port " + port);

                while (true) {
                    try (Socket socket = serverSocket.accept();
                         DataInputStream dis = new DataInputStream(socket.getInputStream());
                         DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                        log("Connexion acceptée : " + socket);

                        while (true) {
                            String command = dis.readUTF();

                            // Commande pour obtenir la liste des fichiers du dossier d'upload
                            if (command.equals("GET_LIST")) {
                                File[] files = uploadDir.listFiles();
                                dos.writeInt(files.length);
                                for (File file : files) {
                                    dos.writeUTF(file.getName());
                                }
                            }
                            // Commande pour télécharger un fichier
                            else if (command.startsWith("GET ")) {
                                String fileName = command.substring(4);
                                File file = new File(uploadDir, fileName);
                                if (!file.exists() || !file.isFile()) {
                                    dos.writeUTF("FILE_NOT_FOUND");
                                    continue;
                                }
                                dos.writeUTF("OK");
                                dos.writeUTF(file.getName());
                                dos.writeLong(file.length());

                                try (FileInputStream fis = new FileInputStream(file)) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    while ((bytesRead = fis.read(buffer)) != -1) {
                                        dos.write(buffer, 0, bytesRead);
                                    }
                                    log("Fichier envoyé : " + file.getAbsolutePath());
                                }
                            }
                            // Commande pour supprimer un fichier
                            else if (command.startsWith("DELETE ")) {
                                String fileName = command.substring(7);
                                File file = new File(uploadDir, fileName);

                                if (file.exists() && file.isFile()) {
                                    if (file.delete()) {
                                        dos.writeUTF("Fichier supprimé avec succès !");
                                        log("Fichier supprimé : " + file.getAbsolutePath());
                                    } else {
                                        dos.writeUTF("Erreur lors de la suppression du fichier.");
                                    }
                                } else {
                                    dos.writeUTF("Fichier introuvable.");
                                }
                                log("Commande DELETE reçue pour : " + fileName);

                            }
                            // Réception de fichiers
                            else {
                                String fileName = command;
                                if (fileName.equals("END")) {
                                    log("Connexion terminée par le client.");
                                    break;
                                }

                                long fileSize = dis.readLong();
                                log("Réception du fichier : " + fileName + " (" + fileSize + " octets)");

                                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                                File outputFile = new File(uploadDir, "received_" + timestamp + "_" + fileName);

                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    long totalRead = 0;

                                    while (totalRead < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                                        fos.write(buffer, 0, bytesRead);
                                        totalRead += bytesRead;
                                    }

                                    log("Fichier reçu : " + outputFile.getAbsolutePath());
                                    dos.writeUTF("Fichier reçu avec succès !");
                                }
                            }
                        }

                    } catch (IOException e) {
                        log("Erreur lors de la connexion : " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                log("Erreur du serveur : " + e.getMessage());
            }

        }).start();
        startButton.setEnabled(false);
    }

    private int loadPortFromConfig() {
        File configFile = new File("serveur.conf");
        int defaultPort = 12468; // Port par défaut
        if (configFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("port=")) {
                        return Integer.parseInt(line.split("=")[1].trim());
                    }
                }
            } catch (IOException | NumberFormatException e) {
                log("Erreur lors de la lecture de serveur.conf : " + e.getMessage());
            }
        } else {
            log("Fichier serveur.conf introuvable, utilisation du port par défaut : " + defaultPort);
        }
        return defaultPort;
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Serveur::new);
    }
}
