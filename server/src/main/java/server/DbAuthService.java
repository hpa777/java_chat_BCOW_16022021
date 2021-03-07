package server;

import java.sql.*;

public class DbAuthService implements AuthService{

    private PreparedStatement addUserQuery;

    private PreparedStatement getUserQuery;

    private Connection connection;

    public DbAuthService(Connection connection) throws SQLException {
        this.connection = connection;
        addUserQuery = connection.prepareStatement("INSERT INTO `users` (`login`, `password`, `nickname`) VALUES (?, ?, ?)");
        getUserQuery = connection.prepareStatement("SELECT `id`, `login`, `password`, `nickname` FROM `users` WHERE `login` = ? AND  `password` = ?");
    }

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        String nick = null;
        try {
            getUserQuery.setString(1, login);
            getUserQuery.setString(2, password);
            ResultSet resultSet = getUserQuery.executeQuery();
            if (resultSet.next()) {
                nick = resultSet.getString("nickname");
            }
            resultSet.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return nick;
    }

    public User getUserByLoginAndPassword(String login, String password) {
        try {
            getUserQuery.setString(1, login);
            getUserQuery.setString(2, password);
            ResultSet resultSet = getUserQuery.executeQuery();
            if (resultSet.next()) {
                String nick = resultSet.getString("nickname");
                long id = resultSet.getLong("id");
                resultSet.close();
                return new User(id, nick, login, connection);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean registration(String login, String password, String nickname) {
        try {
            addUserQuery.setString(1, login);
            addUserQuery.setString(2, password);
            addUserQuery.setString(3, nickname);
            addUserQuery.executeUpdate();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
        return true;
    }

    public void CloseStatement() throws SQLException {
        addUserQuery.close();
        getUserQuery.close();
    }
}
