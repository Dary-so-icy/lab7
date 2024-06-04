package com.darb0ga.server;

import com.darb0ga.common.commands.Command;
import com.darb0ga.common.commands.History;
import com.darb0ga.common.exceptions.CommandRuntimeException;
import com.darb0ga.common.managers.DBManager;
import com.darb0ga.common.managers.StandardConsole;
import com.darb0ga.common.util.Header;
import com.darb0ga.common.util.Packet;
import com.darb0ga.common.util.Reply;
import com.darb0ga.common.util.Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class ClientCommunication {
    private static final int BUFFER_LENGTH = 10_000;
    private final DatagramSocket datagramSocket;
    private DBManager dataBase;
    private ArrayList<String> history = new ArrayList<>();
    private final Serializer serializer = new Serializer();
    private static final Logger logger = LogManager.getLogger(ClientCommunication.class);

    public ClientCommunication(DatagramSocket datagramSocket) throws FileNotFoundException {
        this.datagramSocket = datagramSocket;
        System.out.println(MD2hash("qwerty"));
        Scanner scan = new Scanner(new File("admin"));
        String name = scan.nextLine();
        String password = scan.nextLine();
        regUser("kdkdd", "kjdjjd");  //?
        this.dataBase = new DBManager(scan.nextLine(), scan.nextLine(), scan.nextLine());
        // хуйня какая то тут происходит надо переделать
        // и типо это мы создаем мэнэджера для котого whooo??
    }

    public void regUser(String name, String pswd) {

    }

    public Command readMessage(DatagramPacket datagramPacket, byte[] buffer) throws IOException {
        datagramSocket.receive(datagramPacket);
        Packet packet = serializer.deserialize(buffer);
        Header header = packet.getHeader();
        int countOfPieces = header.getBlocksCount();
        ArrayList<Packet> list = new ArrayList<>(countOfPieces);
        list.add(header.getBlockNumber(), packet);
        int k = 1;

        while (k < countOfPieces) {
            datagramSocket.receive(datagramPacket);
            Packet newPacket = serializer.deserialize(buffer);
            Header newHeader = newPacket.getHeader();
            list.add(newHeader.getBlockNumber(), newPacket);
            k += 1;
        }

        int buffLength = 0;
        for (int i = 0; i < countOfPieces; i++) {
            buffLength += list.get(i).getPieceOfBuffer().length;
        }
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(buffLength)) {
            for (int i = 0; i < countOfPieces; i++) {
                byteStream.write(list.get(i).getPieceOfBuffer());
            }
            return serializer.deserialize(byteStream.toByteArray());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public void sendMessage(Reply replyToClient, SocketAddress socketAddress) {
        try {
            Header header = new Header(0, 0);
            int headerLength = serializer.serialize(header).length + 200;

            byte[] buffer = serializer.serialize(replyToClient);
            int bufferLength = buffer.length;
            int countOfPieces = bufferLength / (BUFFER_LENGTH - headerLength);
            if (countOfPieces * (BUFFER_LENGTH - headerLength) < bufferLength) {
                countOfPieces += 1;
            }
            for (int i = 0; i < countOfPieces; i++) {
                header = new Header(countOfPieces, i);
                headerLength = serializer.serialize(header).length + 200;
                Packet packet = new Packet(header, Arrays.copyOfRange(buffer, i * (BUFFER_LENGTH - headerLength), Math.min(bufferLength, (i + 1) * (BUFFER_LENGTH - headerLength))));
                byte[] array = serializer.serialize(packet);
                DatagramPacket datagramPacket2 = new DatagramPacket(array, array.length, socketAddress);
                datagramSocket.send(datagramPacket2);
                Thread.sleep(100);
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Reply commandExecution(Command command, boolean fileReading, Scanner scanner) {
        Reply reply = new Reply();
        history.add(command.getName());
        if (command instanceof History) {
            reply.addResponse("Введенные команды: ");
            for (String element : history.subList(Math.max(0, history.size() - 14), history.size())) {
                reply.addResponse(element);
            }
            return reply;
        }
        if (!command.getAddition().isEmpty()) {
            try {
                reply = command.execute(command.getAddition(), scanner, fileReading, dataBase);
                return reply;
            } catch (FileNotFoundException | CommandRuntimeException e) {
                reply.addResponse(e.getMessage());
                return reply;
            }
        } else {
            try {
                reply = command.execute("", scanner, fileReading, dataBase);
            } catch (FileNotFoundException | CommandRuntimeException ex) {
                reply.addResponse(ex.getMessage());
                return reply;
            }
        }
        return reply;
    }

    /**
     * хэшируем пароль пользователя
    */
    public String MD2hash(String input) {
        try {
            // getInstance() method is called with algorithm MD2
            MessageDigest md = MessageDigest.getInstance("MD2");

            // digest() method is called
            // to calculate message digest of the input string
            // returned as array of byte
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);

            // Add preceding 0s to make it 32 bit
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            // return the HashText
            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

