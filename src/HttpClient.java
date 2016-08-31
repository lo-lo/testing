import java.io.*;
import java.net.*;
/**
 * Эта программа соединяется с вебсервером и загружает с него содержимое
 * заданного URL. Она непосредственно применяет протокол HTTP.
 **/
public class HttpClient {
    public static void main(String[] args) {
        try {
            // Проверка аргументов
            if ((args.length != 1) && (args.length != 2))
                throw new IllegalArgumentException("Неправильное число аргументов");
            // Получаем поток вывода для записи в него содержимого URL
            OutputStream to_file;
            if (args.length == 2) to_file = new FileOutputStream(args[1]);
            else to_file = System.out;
            // Теперь применяем класс URL для разбора заданного
            // пользователем URL на составные части.
            URL url = new URL(args[0]);
            String protocol = url.getProtocol();
            if (!protocol.equals("http")) // Поддерживаем ли мы протокол?
                throw new IllegalArgumentException("Должен использоватьсяпротокол HTTP ");
                        String host = url.getHost();
            int port = url.getPort();
            if (port ==-1) port = 80; // Если порт не задан, используем
            // стандартный порт HTTP
            String filename = url.getFile();
            // Открываем сетевое подключение с заданным узлом и портом
            Socket socket = new Socket(host, port);
            // Получаем потоки ввода и вывода для соединения socket
            InputStream from_server = socket.getInputStream();
            PrintWriter to_server = new PrintWriter(socket.getOutputStream());
            // Посылаем на вебсервер HTTP команду GET, запрашивающую файл
            // Здесь используется старая и очень простая версия протокола HTTP
            to_server.print("GET " + filename + "\n\n");
            to_server.flush(); // Отправляем немедленно!
            // Теперь читаем ответ сервера и записываем его в файл
            byte[] buffer = new byte[4096];
            int bytes_read;
            while((bytes_read = from_server.read(buffer)) !=-1)
            to_file.write(buffer, 0, bytes_read);
            // Когда сервер разрывает подключение, и мы закрываем свое хозяйство
            socket.close();
            to_file.close();
        }
        catch (Exception e) {  // сообщаем о произошедших ошибках
            System.err.println(e);
            System.err.println("Формат: java HttpClient <URL> [<filename>]");
        }
    }
}


