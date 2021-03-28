package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

public class User {

    private final static Logger logger = Logger.getLogger(User.class.getName());

    private long id;

    private String login;

    private  String nick;

    private Connection connection;

    public long getId() {
        return id;
    }

    public String getNick() {
        return nick;
    }

    public String getLogin() {
        return login;
    }

    public User(long id, String nick, String login, Connection connection) {
        this.id = id;
        this.nick = nick;
        this.login = login;
        this.connection = connection;
    }

    public boolean changeNickName(String nick) {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("UPDATE `users` SET `nickname` = ? WHERE `users`.`id` = ?");
            preparedStatement.setString(1, nick);
            preparedStatement.setLong(2, this.id);
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException throwables) {
            logger.severe(throwables.getMessage());
            return false;
        }
        this.nick = nick;
        return true;

    }
}
