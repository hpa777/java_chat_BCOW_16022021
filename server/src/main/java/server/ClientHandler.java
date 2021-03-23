package server;

import commands.Command;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private User user;

    private final static int SOCKET_TIMEOUT = 120000;

    private final static Logger logger = Logger.getLogger(ClientHandler.class.getName());

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // цикл аутентификации
            this.socket.setSoTimeout(SOCKET_TIMEOUT);
            while (true) {
                String str = in.readUTF();

                if (str.equals(Command.END)) {
                    out.writeUTF(Command.END);
                    throw new RuntimeException("Клиент захотел отключиться");
                }
                if (str.startsWith(Command.AUTH)) {
                    String[] token = str.split("\\s", 3);
                    if (token.length < 3) {
                        continue;
                    }
                    String newNick = server.getAuthService()
                            .getNicknameByLoginAndPassword(token[1], token[2]);

                    user = ((DbAuthService) server.getAuthService()).getUserByLoginAndPassword(token[1], token[2]);
                    if (user != null) {
                        if (!server.isLoginAuthenticated(user.getLogin())) {
                            sendMsg(Command.AUTH_OK + " " + user.getNick());
                            server.subscribe(this);
                            logger.info("client: " + socket.getRemoteSocketAddress() +
                                    " connected with nick: " + user.getNick());
                            break;
                        } else {
                            sendMsg("Данная учетная запись уже используется");
                        }
                    } else {
                        sendMsg("Неверный логин / пароль");
                    }
                }
                if (str.startsWith(Command.REQUEST_TO_REG)) {
                    String[] token = str.split("\\s", 4);
                    if (token.length < 4) {
                        continue;
                    }
                    boolean regSuccess = server.getAuthService().registration(token[1], token[2], token[3]);
                    sendMsg(regSuccess ? Command.REG_ACCEPT : Command.REG_REJECT);
                }
            }
            //цикл работы
            this.socket.setSoTimeout(0);
            while (true) {
                String str = in.readUTF();
                str = str.trim();
                if (str.isEmpty()) {
                    continue;
                } else if (str.startsWith("/")) {
                    logger.info(String.format("User: %s send command: %s", this.user.getNick(), str));
                    if (str.equals(Command.END)) {
                        out.writeUTF(Command.END);
                        break;
                    } else if (str.startsWith(Command.CHANGE_NICK)) {
                        String[] parts = str.split("\\s");
                        if (parts.length == 2 && this.user.changeNickName(parts[1])) {
                            this.sendMsg(String.format("%s %s", Command.CHANGE_NICK_ACCEPT, parts[1]));
                            server.broadcastClientList();
                        } else {
                            this.sendMsg("Такой ник уже используется");
                        }
                    } else if (str.startsWith(Command.PRIVATE_MESSAGE_HEAD)) {
                        String[] parts = str.split("\\s", 3);
                        if (parts.length == 3) {
                            server.sendPrivateMessage(parts[1], parts[2], this);
                        }
                    }
                } else {
                    logger.info(String.format("User: %s send message: %s", this.user.getNick(), str));
                    server.broadcastMsg(this, str);
                }
            }
        } catch (SocketTimeoutException e) {
            logger.warning(e.getMessage());
            try {
                out.writeUTF(Command.END);
            } catch (IOException ioException) {
                logger.severe(ioException.getMessage());
            }
        } catch (RuntimeException e) {
            logger.severe(e.getMessage());
        } catch (IOException e) {
            logger.severe(e.getMessage());
        } finally {
            server.unsubscribe(this);
            logger.info("Client disconnected: " + user.getNick());
            try {
                socket.close();
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }


    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public User getUser() {
        return user;
    }


}
