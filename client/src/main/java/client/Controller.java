package client;

import commands.Command;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final int PORT = 8189;
    private final String IP_ADDRESS = "localhost";

    private boolean authenticated;
    private String nickname;
    private Stage stage;
    private Stage refStage;
    private RegController regController;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);
        if (!authenticated) {
            nickname = "";
        }
        textArea.clear();
        setTitle(nickname);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) textArea.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                if (socket != null && !socket.isClosed()) {
                    try {
                        out.writeUTF(Command.END);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        setAuthenticated(false);
    }

    private void loadHistory(String logFileName) throws IOException {
        try {
            List<String> history = Files.readAllLines(Paths.get(logFileName), Charset.forName("UTF-8"));
            int start = history.size() > 100 ? history.size() - 100 : 0;
            for (int i = start; i < history.size(); i++) {
                textArea.appendText(history.get(i) + "\n");
            }
        } catch (NoSuchFileException e) {

        }
    }

    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    // цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                throw new RuntimeException("Сервак нас отключает");
                            }
                            if (str.startsWith(Command.AUTH_OK)) {
                                String[] token = str.split("\\s");
                                nickname = token[1];
                                setAuthenticated(true);
                                break;
                            }
                            if (str.equals(Command.REG_ACCEPT) || str.equals(Command.REG_REJECT)) {
                                regController.setResultTryToReg(str);
                            }
                        } else {
                            textArea.appendText(str + "\n");
                        }
                    }
                    //цикл работы
                    String fileName = String.format("history_%s.txt", nickname);
                    loadHistory(fileName);
                    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fileName, true), Charset.forName("UTF-8").newEncoder());
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                System.out.println("Client disconnected");
                                break;
                            } else if (str.startsWith(Command.CLIENT_LIST)) {
                                String[] token = str.split("\\s");
                                Platform.runLater(()->{
                                    clientList.getItems().clear();
                                    for (int i = 1; i < token.length; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            } else if (str.startsWith(Command.CHANGE_NICK_ACCEPT)) {
                                String[] token = str.split("\\s");
                                setTitle(token[1]);
                                textArea.appendText("Ник успешно сменен.\n");
                            }
                        } else {
                            writer.write(str + "\n");
                            textArea.appendText(str + "\n");
                        }
                    }
                    writer.close();
                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setAuthenticated(false);
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

    public void sendMsg(ActionEvent actionEvent) {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(String.format("%s %s %s", Command.AUTH, loginField.getText().trim(), passwordField.getText().trim()));

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            passwordField.clear();
        }
    }

    private void setTitle(String nickname) {
        Platform.runLater(() -> {
            if (nickname.equals("")) {
                stage.setTitle("Best chat of World");
            } else {
                stage.setTitle(String.format("Best chat of World - [ %s ]", nickname));
            }
        });
    }

    public void clientListMouseReleased(MouseEvent mouseEvent) {
        String msg = String.format("%s %s", Command.PRIVATE_MESSAGE_HEAD, clientList.getSelectionModel().getSelectedItem());
        textField.setText(msg);
    }

    public void showRegWindow(ActionEvent actionEvent) {
        if (refStage == null) {
            initRegWindow();
        }
        refStage.show();
    }

    private void initRegWindow()  {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();

            regController = fxmlLoader.getController();
            regController.setController(this);

            refStage = new Stage();
            refStage.setTitle("Best chat of World registration");
            refStage.setScene(new Scene(root, 450, 350));
            refStage.initStyle(StageStyle.UTILITY);
            refStage.initModality(Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void registration(String login, String password, String nickname) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(String.format("%s %s %s %s", Command.REQUEST_TO_REG, login, password, nickname));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
