import java.io.*;
import java.net.*;
/**
 * Эта программа ожидает прибытия дейтаграмм, отправленных на заданный порт.
 * Приняв дейтаграмму, она отображает узел отправителя и выводит содержимое
 * дейтаграммы в виде строки. Затем она возвращается к началу
 * цикла и снова ждет.
 **/
public class UDPReceive {
    public static final String usage = "Формат: java UDPReceive <port>";
    public static void main(String args[]) {
        try {
            if (args.length != 1)
                throw new IllegalArgumentException("Неправильное число аргументов");
            // Получаем порт из командной строки
            int port = Integer.parseInt(args[0]);
            // Создаем соединение, слушающее этот порт.
            DatagramSocket dsocket = new DatagramSocket(port);
            // Создаем буфер для считывания дейтаграммы. Если ктото отправит нам пакет
            // большего размера, чем может поместиться в буфере, избыток просто пропадет!
            byte[] buffer = new byte[2048];
            // Создаем пакет для приема данных в буфер
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            // Запускаем бесконечный цикл, ожидающий получения пакетов и печатающий их.
            for(;;) {
                // Ждем прибытия дейтаграммы
                dsocket.receive(packet);
                // Преобразуем ее содержимое в объект String и отображаем его
                String msg = new String(buffer, 0, packet.getLength());
                System.out.println(packet.getAddress().getHostName() +
                        ": " + msg);
                // Перед следующим использованием пакета packet восстанавливаем его длину.
                // До появления Java 1.1 нам пришлось бы каждый раз создавать новый пакет.
                packet.setLength(buffer.length);
            }
        }
        catch (Exception e) {
            System.err.println(e);
            System.err.println(usage);
        }
    }
}

