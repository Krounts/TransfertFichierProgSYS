import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Serveur {
    private final File uploadDir = new File("upload"); // Dossier d'upload

    public Serveur() {
        // Créer le dossier d'upload s'il n'existe pas
        if (!uploadDir.exists()) {
            uploadDir.mkdir();
        }
    }

    public void startServer() {
        new Thread(() -> {
            int port = loadPortFromConfig(); // Charger le port depuis serveur.conf

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Serveur démarré sur le port " + port);

                while (true) {
                    try (Socket socket = serverSocket.accept();
                         DataInputStream dis = new DataInputStream(socket.getInputStream());
                         DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                        System.out.println("Connexion acceptée : " + socket);

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
                                    System.out.println("Fichier envoyé : " + file.getAbsolutePath());
                                }
                            }
                            // Commande pour supprimer un fichier
                            else if (command.startsWith("DELETE ")) {
                                String fileName = command.substring(7);
                                File file = new File(uploadDir, fileName);

                                if (file.exists() && file.isFile()) {
                                    if (file.delete()) {
                                        dos.writeUTF("Fichier supprimé avec succès !");
                                        System.out.println("Fichier supprimé : " + file.getAbsolutePath());
                                    } else {
                                        dos.writeUTF("Erreur lors de la suppression du fichier.");
                                    }
                                } else {
                                    dos.writeUTF("Fichier introuvable.");
                                }
                                System.out.println("Commande DELETE reçue pour : " + fileName);
                            }
                            // Réception de fichiers
                            else {
                                String fileName = command;
                                if (fileName.equals("END")) {
                                    System.out.println("Connexion terminée par le client.");
                                    break;
                                }

                                long fileSize = dis.readLong();
                                System.out.println("Réception du fichier : " + fileName + " (" + fileSize + " octets)");

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

                                    System.out.println("Fichier reçu : " + outputFile.getAbsolutePath());
                                    dos.writeUTF("Fichier reçu avec succès !");
                                }
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Erreur lors de la connexion : " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.out.println("Erreur du serveur : " + e.getMessage());
            }

        }).start();
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
                System.out.println("Erreur lors de la lecture de serveur.conf : " + e.getMessage());
            }
        } else {
            System.out.println("Fichier serveur.conf introuvable, utilisation du port par défaut : " + defaultPort);
        }
        return defaultPort;
    }

    public static void main(String[] args) {
        Serveur serveur = new Serveur();
        serveur.startServer();
    }
}

