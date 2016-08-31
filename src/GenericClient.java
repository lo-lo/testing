import java.io.*;
import java.net.*;
/**
 * Эта программа соединяется с сервером на заданном узле и порте.
 * Она считывает текст с консоли и отправляет его серверу.
 * Она считывает текст от сервера и выводит его на консоль.
 **/
public class GenericClient {
    public static void main(String[] args) throws IOException {
        try {
            // Проверяем число аргументов
            if (args.length != 2)
                throw new IllegalArgumentException("Неправильное число аргументов");
            // Анализируем аргументы, заданные для узла и порта
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            // Соединяемся с заданными узлом и портом
            Socket s = new Socket(host, port);
            // Создаем потоки для связи с сервером. Поток from_server объявлен
            // как final ввиду его использования приведенным ниже внутренним классом
            final Reader from_server=new InputStreamReader(s.getInputStream());
            PrintWriter to_server = new PrintWriter(s.getOutputStream());
            // Создаем потоки для связи с консолью. Поток to_user объявлен
            // как final ввиду его использования приведенным ниже безымянным классом
            BufferedReader from_user =
                    new BufferedReader(new InputStreamReader(System.in));
            // Параметр true передается для осуществления автоматического
            // проталкивания в методе println()
            final PrintWriter to_user = new PrintWriter(System.out, true);
            // Сообщаем пользователю, что подключение установлено
            to_user.println("Установлено подключение к " + s.getInetAddress() +
                    ":" + s.getPort());
            // Создаем поток исполнения, получающий вывод с сервера и отображающий его
            // для пользователя. Поскольку для этого используется отдельный поток
            // исполнения, вывод можно получать асинхронно
            Thread t = new Thread() {
                public void run() {
                    char[] buffer = new char[1024];
                    int chars_read;
                    try {
                        // Считываем символы, пока поток ввода не закроется
                        while((chars_read = from_server.read(buffer)) != -1) {
                            // Проходим цикл по массиву символов и печатаем их,
                            // преобразуя все символы \n в локальный признак конца строки.
                            // Можно было бы сделать и поэффективней, но это, вероятно, будет
                            // происходить быстрее, чем передача по сети, так что нам этого хватит
                            for(int i = 0; i < chars_read; i++) {
                                if (buffer[i] == '\n') to_user.println();
                                else to_user.print(buffer[i]);
                            }
                            to_user.flush();
                        }
                    }
                    catch (IOException e) { to_user.println(e); }
                    // Когда сервер закрывает подключение, вышеприведенный цикл
                    // заканчивается. Сообщаем пользователю о произошедшем и вызываем
                    // System.exit(), в результате чего наряду с этим потоком исполнения
                    // прекратит работу и основной поток.
                    to_user.println("Сервер закрыл подключение.");
                    System.exit(0);
                }
            };
            // Приоритет потока исполнения, передающего данные от сервера
            // к пользователю, делаем на единицу больше, чем у основного потока.
            // Это как будто необязательно, но в некоторых операционных системах
            // вывод на консоль не отобразится, пока процесс с таким же
            // приоритетом заблокирован в ожидании ввода с консоли.
            t.setPriority(Thread.currentThread().getPriority() + 1);
            // Теперь запускаем этот поток исполнения
            t.start();
            // Параллельно с ним считываем пользовательский ввод
            // и отправляем его серверу.
            String line;
            while((line = from_user.readLine()) != null) {
                to_server.print(line + "\n");
                to_server.flush();
            }
            // Если пользователь нажимает CtrlD (Unix) или CtrlZ (Windows)
            // для завершения своего ввода, мы отправляем EOF, и происходит выход
            // из цикла. Когда это происходит, мы останавливаем дополнительный
            // (servertouser) поток исполнения и закрываем соединение (socket).
            s.close();
            to_user.println("Подключение закрыто клиентом.");
            System.exit(0);
        }
        // Если чтото не в порядке, печатаем сообщение об ошибке
        catch (Exception e) {
            System.err.println(e);
            System.err.println("Формат: java GenericClient <hostname> <port>");
        }
    }
}


