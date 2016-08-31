import java.io.*;
import java.net.*;
/**
 * Этот класс отправляет заданный текст или файл в виде дейтаграммы
 * на заданный порт заданного узла.
 **/
public class UDPSend {
    public static final String usage =
            "Формат: java UDPSend <hostname> <port> <msg>...\n" +
                    " или: java UDPSend <hostname> <port> f <file>";
    public static void main(String args[]) {
        try {
            // Проверяем число аргументов
            if (args.length < 3)
                throw new IllegalArgumentException("Неправильное число аргументов");
            // Разбираем аргументы
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            // Определяем пересылаемое сообщение.
            // Если третий аргумент f, отправляем содержимое файла,
            // заданного четвертым аргументом. В противном случае объединяем
            // третий и все последующие аргументы и отправляем их.
            byte[] message;
            if (args[2].equals("f")) {
                File f = new File(args[3]);
                int len = (int)f.length(); // Определяем размер файла
                message = new byte[len];   // Создаем буфер достаточного размера
                FileInputStream in = new FileInputStream(f);
                int bytes_read = 0, n;
                do {    // Цикл продолжается, пока не все прочитано
                    n = in.read(message, bytes_read, len-bytes_read);
                    bytes_read += n;
                } while((bytes_read < len) && (n != -1));
            }
            else { // В противном случае просто суммируем все остальные аргументы.
                String msg = args[2];
                for (int i = 3; i < args.length; i++) msg += " " + args[i];
                message = msg.getBytes();
            }
            // Получаем адрес заданного узла в Интернете
            InetAddress address = InetAddress.getByName(host);
            // Инициализируем пакет дейтаграммы данными и адресом
            DatagramPacket packet = new DatagramPacket(message, message.length,
                    address, port);
            // Создаем “дейтаграммное” соединение, отправляем по нему пакет, закрываем его
            DatagramSocket dsocket = new DatagramSocket();
            dsocket.send(packet);
            dsocket.close();
        }
        catch (Exception e) {
            System.err.println(e);
            System.err.println(usage);
        }
    }
}

