package server;

import commands.Command;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private User user;

    private final static int SOCKET_TIMEOUT = 120000;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
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

                            user = ((DbAuthService)server.getAuthService()).getUserByLoginAndPassword(token[1], token[2]);
                            if (user != null) {
                                if (!server.isLoginAuthenticated(user.getLogin())) {
                                    sendMsg(Command.AUTH_OK + " " + user.getNick());
                                    server.subscribe(this);
                                    System.out.println("client: " + socket.getRemoteSocketAddress() +
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
                        } else if (str.equals(Command.END)) {
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
                        } else {
                            server.broadcastMsg(this, str);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    try {
                        out.writeUTF(Command.END);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Client disconnected: " + user.getNick());
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
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
