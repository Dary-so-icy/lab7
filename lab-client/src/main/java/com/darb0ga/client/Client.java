package com.darb0ga.client;

import com.darb0ga.common.collection.LabWork;
import com.darb0ga.common.collection.Models.AskLabWork;
import com.darb0ga.common.commands.*;
import com.darb0ga.common.managers.AuthorsRight;
import com.darb0ga.common.managers.Commander;
import com.darb0ga.common.util.Header;
import com.darb0ga.common.util.Packet;
import com.darb0ga.common.util.Reply;
import com.darb0ga.common.util.Serializer;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static com.darb0ga.client.HashPassword.MD2hash;

public final class Client {
    private Commander commander = new Commander();
    private String ip;
    private ScriptManager scriptExecution;
    private final DatagramChannel channel;
    private InetSocketAddress serverAddress;
    private final Selector selector;
    //private boolean isLogInto;
    private final int BUFFER_LENGTH = 1000;
    private AuthorsRight authorization = new AuthorsRight("", "");
    private ByteBuffer buffer = ByteBuffer.allocate(10_000);

    public Client(String ip) throws IOException {
        scriptExecution = new ScriptManager(this);
        this.ip = ip;
        channel = DatagramChannel.open();
        selector = Selector.open();
    }


    public Command CommandBuilder(String args) throws IOException {

        String name = args.split(" ")[0];
        String addition = "";
        if (args.split(" ").length > 1) {
            addition = args.split(" ")[1];
        }
        try {
            Command comm = commander.getCommands().get(name);
            comm.setAddition(addition);
            comm.setRequestOwner(authorization);
            return comm;
        } catch (Exception e) {
            System.err.println("Произошли ошибки при работе с введенной командой: " + name);
        }
        return null;
    }


    public void start() throws IOException, InterruptedException {
        connectServer(1);
        System.out.println("Необходимо войти в базу данных");
        Scanner scan = new Scanner(System.in);
        String comm;
        Command command;
        while (true) {
            if (!authorization.isGotIn()) {
                comm  = login(scan);
            }
            else {
                comm = scan.nextLine();
            }
            command = CommandBuilder(comm);
                if (command instanceof Exit) {
                    System.out.println("Выход из программы");
                    System.exit(0);
                }

            if (command == null) {
                continue;
            }
            command.setRequestOwner(authorization);
            try {
                if (command.isLabNeeded()) {
                    AskLabWork newLaba = new AskLabWork();
                    try {
                        LabWork laba = newLaba.build(scan, false);
                        command.setAssertNewLab(laba);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                if (command instanceof ExecuteScript) {
                    try {
                        scriptExecution.executeFile(comm.trim().split(" ")[1]);
                    } catch (RuntimeException e) {
                        System.out.println(e.getMessage());
                        continue;
                    }
                    continue;
                } else if (command != null) {
                    sendCommand(command);
                }
                TimeUnit.MILLISECONDS.sleep(30);
                Reply answer = receive(buffer);
                if(answer.getResponse()!=null){
                if(answer.getResponse().iterator().next().equals("Выполнен вход в аккаунт")){
                    authorization.setGotIn(true);
                }}
                answer.getResponse().forEach(System.out::println);
            } catch (ArrayIndexOutOfBoundsException ex) {
                System.out.println("Произошли ошибки при работе с аргументами");
            } catch (NumberFormatException e) {
                System.out.println(e.getMessage());
                buffer = ByteBuffer.allocate(10_000);
            }
        }}




    private String login(Scanner scan) throws IOException {
        String comm = "";
        while (!((comm.equals("register")) || (comm.equals("login")))) {
            System.out.println("Необходимо войти в базу данных. Введите register или login");
            comm = scan.nextLine().trim();
        }
        System.out.println("Введите ваше имя пользователя:");
        String name = scan.nextLine();
        System.out.println("Введите ваш пароль:");
        String passwd;
        java.io.Console console = System.console();
        if (console != null){
            char[] symbols = console.readPassword();
            while (symbols.length == 0){
                System.out.println("Введите непустой пароль:");
                symbols = console.readPassword();
            }
            passwd = MD2hash(String.valueOf(symbols));
        } else {
            String symbols = scan.nextLine().trim();
            while (symbols.isEmpty()){
                System.out.println("Введите непустой пароль:");
                symbols = scan.nextLine().trim();
            }
            passwd = MD2hash(symbols);
        }
        authorization.setName(name);
        authorization.setPassword(passwd);
        return comm;
        }



    public void sendCommand(Command command) throws IOException {
        Serializer serializer = new Serializer();
        Header header = new Header(0, 0);
        int headerLength = serializer.serialize(header).length + 200;

        byte[] buffer = serializer.serialize(command);
        int bufferLength = buffer.length;
        int countOfPieces = bufferLength / (BUFFER_LENGTH - headerLength);
        if (countOfPieces * (BUFFER_LENGTH - headerLength) < bufferLength) {
            countOfPieces += 1;
        }
        for (int i = 0; i < countOfPieces; i++) {
            header = new Header(countOfPieces, i);
            headerLength = serializer.serialize(header).length + 200;
            Packet packet = new Packet(header, Arrays.copyOfRange(buffer, i * (BUFFER_LENGTH - headerLength), Math.min(bufferLength, (i + 1) * (BUFFER_LENGTH - headerLength))));
            channel.send(ByteBuffer.wrap(serializer.serialize(packet)), serverAddress);
        }
    }

    public Reply receive(ByteBuffer buffer) {
        buffer.clear();
        Serializer serializer = new Serializer();
        try {
            SocketAddress address = null;
            int time = 1;
            int tries = 1;
            // adder for what
            while (!serverAddress.equals(address)) {
                if (time % 5000000 == 0) {
                    connectServer(tries);
                    tries += 1;
                }
                buffer.clear();
                address = channel.receive(buffer);
                time += 1;
            }

            Packet packet = serializer.deserialize(buffer.array());
            Header header = packet.getHeader();
            int countOfPieces = header.getBlocksCount();
            ArrayList<Packet> list = new ArrayList<>(3);
            list.add(header.getBlockNumber(), packet);
            list.add(1, null);
            int k = 1;

            while (k < countOfPieces) {
                buffer.clear();
                if (selector.select() < 0) continue;

                channel.receive(buffer);
                Packet newPacket = serializer.deserialize(buffer.array());
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
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public void connectServer(int connectionTries) {
        try {
            serverAddress = new InetSocketAddress(ip, 2226);
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            System.out.println("Попытка подключения к серверу №" + connectionTries);
            if (connectionTries > 2) {
                System.out.println("Не удается подключиться к серверу");
                System.exit(0);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
