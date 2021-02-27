package client;

import commands.Command;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class RegController {

    @FXML
    private TextField loginField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField nicknameField;
    @FXML
    private TextArea textArea;

    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public void setResultTryToReg(String commang) {
        if (commang.equals(Command.REG_ACCEPT)) {
            textArea.appendText("Регистрация прошла успешно\n");
        } else if (commang.equals(Command.REG_REJECT)) {
            textArea.appendText("Логин или никнейм уже заняты\n");
        }
    }

    public void tryToReg(ActionEvent actionEvent) {
        String login = loginField.getText().trim();
        String pass = passwordField.getText().trim();
        String nick = nicknameField.getText().trim();
        if (login.length() * pass.length() * nick.length() != 0) {
            controller.registration(login, pass, nick);
        }
    }
}
