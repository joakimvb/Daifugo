package client.networking;

import protocol.*;

import java.net.*;
import java.io.*;

public class ClientConnection {
    private final Socket clientSocket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public ClientConnection(String address) throws IOException {
        clientSocket = new Socket(address, Protocol.PORT);
        clientSocket.setSoTimeout(Protocol.SOCKET_TIMEOUT);
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        in = new ObjectInputStream(clientSocket.getInputStream());
    }

    public Message sendMessage(Message msg) throws ClassNotFoundException, IOException {
        out.writeObject(msg);
        return (Message) in.readObject();
    }

    public Message sendMessage(MessageType type) throws IOException, ClassNotFoundException {
        return sendMessage(new Message(type));
    }

    public void disconnect() throws IOException, ClassNotFoundException {
        sendMessage(new Message(MessageType.DISCONNECT));
        in.close();
        out.close();
        clientSocket.close();
    }
}
