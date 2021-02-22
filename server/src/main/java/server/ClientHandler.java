package server;

import commands.Command;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String nickname;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    // цикл аутентификации
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

                            if (newNick != null) {
                                nickname = newNick;
                                sendMsg(Command.AUTH_OK + " " + nickname);
                                server.subscribe(this);
                                System.out.println("client: " + socket.getRemoteSocketAddress() +
                                        " connected with nick: " + nickname);
                                break;
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }

                        }
                    }
                    //цикл работы
                    while (true) {
                        String str = in.readUTF();
                        if (str.isEmpty()) {
                            continue;
                        } else if (str.equals(Command.END)) {
                            out.writeUTF(Command.END);
                            break;
                        } else if (str.trim().startsWith(Command.PRIVATE_MESSAGE_HEAD)) {
                            String[] parts = str.trim().split("\\s", 3);
                            if (parts.length == 3) {
                                String response = "";
                                if (server.sendPrivateMessage(parts[1], parts[2], this)) {
                                    response = prepareMessage(this.getNickname(), parts[2]);
                                } else {
                                    response = String.format("Пользователь с ником [ %s ] не найден.", parts[2]);
                                }
                                this.sendMsg(response);
                            }
                        } else {
                            server.broadcastMsg(this, str);
                        }
                    }
                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Client disconnected: " + nickname);
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

    public String getNickname() {
        return nickname;
    }

    public static String prepareMessage(String nickname, String message) {
        return String.format("[ %s ]: %s", nickname, message);
    }
}
