package server;

import commands.Command;
import jdk.internal.org.objectweb.asm.Handle;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class Server {
    private final int PORT = 8189;
    private final static String CONNECTION_STRING = "jdbc:mysql://localhost:3306/chat_db?autoReconnect=true";
    private final static String DB_USER = "root";
    private final static String DB_PASS = "root";

    private final static Logger logger = Logger.getLogger(Server.class.getName());

    private ServerSocket server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private List<ClientHandler> clients;
    private AuthService authService;

    private Connection connection;
    private PreparedStatement saveMessageQuery;

    public Server() {
        try {
            connection = DriverManager.getConnection(CONNECTION_STRING, DB_USER, DB_PASS);
            authService = new DbAuthService(connection);
            saveMessageQuery = connection.prepareStatement("INSERT INTO `messages` (`sender_id`, `recipient_id`, `message`) VALUES (?, ?, ?)");
        } catch (SQLException e) {
            logger.severe(e.getMessage());
            return;
        }

        clients = new CopyOnWriteArrayList<>();

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        try {
            server = new ServerSocket(PORT);
            logger.info("Server started");

            while (true) {
                socket = server.accept();
                logger.info("Client connected. IP: " + socket.getRemoteSocketAddress());
                executorService.execute(new ClientHandler(this, socket));
            }
        } catch (IOException e) {
            logger.severe(e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
            try {
                server.close();
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
            try {
                ((DbAuthService) authService).CloseStatement();
                saveMessageQuery.close();
                connection.close();
            } catch (SQLException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    public void sendPrivateMessage(String receiverNickName, String message, ClientHandler sender) {
        boolean foundRecipient = false;
        try {
            for (ClientHandler client : this.clients) {
                if (client.getUser().getNick().equals(receiverNickName)) {
                    String msg = prepareMessage(sender.getUser().getNick(), message);
                    client.sendMsg(msg);
                    foundRecipient = true;
                    saveMessageQuery.setLong(1, sender.getUser().getId());
                    saveMessageQuery.setLong(2, client.getUser().getId());
                    saveMessageQuery.setString(3, msg);
                    saveMessageQuery.addBatch();
                    break;
                }
            }
            String response = foundRecipient ?
                    prepareMessage(sender.getUser().getNick(), message) :
                    String.format("Пользователь с ником [ %s ] не найден.", receiverNickName);
            sender.sendMsg(response);
            saveMessageQuery.setLong(1, sender.getUser().getId());
            saveMessageQuery.setLong(2, sender.getUser().getId());
            saveMessageQuery.setString(3, response);
            saveMessageQuery.addBatch();
            saveMessageQuery.executeBatch();
        } catch (SQLException e) {
            logger.severe(e.getMessage());
        }
    }

    public void broadcastMsg(ClientHandler sender, String message) {
        String msg = prepareMessage(sender.getUser().getNick(), message);
        try {
            for (ClientHandler c : clients) {
                c.sendMsg(msg);
                saveMessageQuery.setLong(1, sender.getUser().getId());
                saveMessageQuery.setLong(2, c.getUser().getId());
                saveMessageQuery.setString(3, msg);
                saveMessageQuery.addBatch();
            }
            saveMessageQuery.executeBatch();
        } catch (SQLException e) {
            logger.severe(e.getMessage());
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        broadcastClientList();
    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastClientList();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public boolean isLoginAuthenticated(String login) {
        for (ClientHandler c : clients) {
            if (c.getUser().getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    public void broadcastClientList() {
        StringBuilder sb = new StringBuilder(Command.CLIENT_LIST);
        for (ClientHandler c : clients) {
            sb.append(" ").append(c.getUser().getNick());
        }
        String msg = sb.toString();
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }

    private static String prepareMessage(String nickname, String message) {
        return String.format("[ %s ]: %s", nickname, message);
    }

}
