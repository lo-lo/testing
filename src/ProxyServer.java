import java.io.*;
import java.net.*;
/**
 * Этот класс применяет класс Server в качестве основы многопоточного сервера,
 * на который «навешиваются» относительно простые проксислужбы. Метод main()
 * запускает сервер. Вложенный класс Proxy реализует интерфейс
 * Server.Service и предоставляет проксиобслуживание.
 **/
public class ProxyServer {
    /**
     * Создаем объект Server и добавляем к нему объекты Proxy, осуществляющие
     * проксиобслуживание в соответствии с аргументами, заданными
     * в командной строке.
     **/
    public static void main(String[] args) {
        try {
            // Проверяем число аргументов. Оно должно быть кратно 3 и > 0.
            if ((args.length == 0) || (args.length % 3 != 0))
                throw new IllegalArgumentException("Неправильное число аргументов");
            // Создаем объект Server
            Server s = new Server(null, 12); // Записываем поток (stream)
            // и лимит подключений
            // Цикл, анализирующий кортежи (tuples) аргументов (host, remoteport,
            // localport). Для каждого из них создаем объект Proxy и добавляем
            // его к списку служб сервера.
            int i = 0;
            while(i < args.length) {
                String host = args[i++];
                int remoteport = Integer.parseInt(args[i++]);
                int localport = Integer.parseInt(args[i++]);
                s.addService(new Proxy(host, remoteport), localport);
            }
        }
        catch (Exception e) { // Печатаем сообщение об ошибке,
            // если чтото не в порядке
            System.err.println(e);
            System.err.println("Формат: java ProxyServer " +
                    "<host> <remoteport> <localport> ...");
            System.exit(1);
        }
    }
/**
 * Это класс, реализующий проксислужбу. Метод serve() будет вызываться
 * при подключении клиента. В этот момент он должен установить
 * подключение к серверу, а затем передавать байты от клиента серверу
 * и обратно. Для симметрии этот класс реализует два очень сходных потока
 * исполнения в виде безымянных классов. Один поток копирует байты
 * от клиента к серверу, а другой копирует их от сервера к клиенту.
 * Поток исполнения, вызывающий метод serve(), создает и запускает
 * эти потоки исполнения, а затем просто ожидает их завершения.
 **/
public static class Proxy implements Server.Service {
    String host;
    int port;
    /** Запоминаем узел и порт, которые представляем */
    public Proxy(String host, int port) {
        this.host = host;
        this.port = port;
    }
    /** Сервер вызывает этот метод при подключении клиента. */
    public void serve(InputStream in, OutputStream out) {
        // Это соединения, которые мы будем использовать. Они объявлены как final,
        // поэтому их смогут использовать приведенные ниже безымянные классы.
        final InputStream from_client = in;
        final OutputStream to_client = out;
        final InputStream from_server;
        final OutputStream to_server;
        // Пытаемся установить подключение к заданному серверу и порту и получить
        // соединения для связи с ним. В случае неудачи докладываем о ней клиенту.
        final Socket server;
        try {
            server = new Socket(host, port);
            from_server = server.getInputStream();
            to_server = server.getOutputStream();
        }
        catch (Exception e) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
            pw.print("Проксисервер не смог подключиться к " + host +
                    ":" + port + "\n");
            pw.flush();
            pw.close();
            try { in.close(); } catch (IOException ex) {}
            return;
        }
        // Создаем массив, содержащий два объекта Thread. Он объявляется как final,
        // так что им смогут пользоваться приведенные ниже безымянные классы.
        // Мы используем массив вместо двух переменных, поскольку при данной
        // структуре программы две переменные не сработали бы,
        // будучи определены как final.
        final Thread[] threads = new Thread[2];
        // Определяем и создаем поток исполнения, копирующий байты
        // от клиента к серверу
        Thread c2s = new Thread() {
            public void run() {
                // Копируем байты до тех пор, пока не получим от клиента EOF
                byte[] buffer = new byte[2048];
                int bytes_read;
                try {
                    while((bytes_read=from_client.read(buffer))!=-1) {
                        to_server.write(buffer, 0, bytes_read);
                        to_server.flush();
                    }
                }
                catch (IOException e) {}
                finally {
                    // По завершении потока исполнения
                    try {
                        server.close();    // закрываем соединение с сервером
                        to_client.close(); // и клиентские потоки (streams)
                        from_client.close();
                    }
                    catch (IOException e) {}
                }
            }
        };
        // Определяем и создаем поток исполнения, копирующий байты от сервера
        // к клиенту. Этот поток исполнения работает совершенно так же,
        // как приведенный выше.
        Thread s2c = new Thread() {
            public void run() {
                byte[] buffer = new byte[2048];
                int bytes_read;
                try {
                    while((bytes_read=from_server.read(buffer))!=-1) {
                        to_client.write(buffer, 0, bytes_read);
                        to_client.flush();
                    }
                }
                catch (IOException e) {}
                finally {
                    try {
                        server.close(); // закрываемся
                        to_client.close();
                        from_client.close();
                    } catch (IOException e) {}
                }
            }
        };
        // Сохраняем потоки исполнения в массиве final threads[], чтобы
        // безымянные классы могли ссылаться друг на друга.
        threads[0] = c2s; threads[1] = s2c;
        // запускаем потоки исполнения
        c2s.start(); s2c.start();
        // Ожидаем их завершения
        try { c2s.join(); s2c.join(); } catch (InterruptedException e) {}
    }
}
}

